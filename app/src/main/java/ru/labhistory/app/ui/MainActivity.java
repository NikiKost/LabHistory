package ru.labhistory.app.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.LiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.BuildConfig;
import ru.labhistory.app.R;
import ru.labhistory.app.backup.BackupRepository;
import ru.labhistory.app.backup.BackupSummary;
import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.ProfileEntity;
import ru.labhistory.app.databinding.ActivityMainBinding;
import ru.labhistory.app.domain.ManualResultInput;
import ru.labhistory.app.update.UpdateChecker;
import ru.labhistory.app.update.UpdateDownloader;
import ru.labhistory.app.update.UpdateInfo;

public class MainActivity extends AppCompatActivity {
    private static final String UPDATE_PREFS = "update_check";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private ActivityMainBinding binding;
    private AnalysisRepository repository;
    private BackupRepository backupRepository;
    private DocumentListAdapter documentAdapter;
    private UpdateChecker updateChecker;
    private UpdateDownloader updateDownloader;
    private List<ProfileEntity> profiles = new ArrayList<>();
    private LiveData<List<AnalysisDocumentEntity>> currentDocuments;
    private Uri pendingIncomingUri;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private boolean updateInspectionRunning;
    private boolean updateReadyDialogShown;
    private boolean awaitingInstallPermission;
    private char[] pendingExportPassword;

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocumentSelected);

    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/octet-stream"),
                    this::onBackupDestinationSelected
            );

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    this::onBackupSourceSelected
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        repository = new AnalysisRepository(this);
        backupRepository = new BackupRepository(this);
        updateChecker = new UpdateChecker();
        updateDownloader = new UpdateDownloader(this);
        documentAdapter = new DocumentListAdapter(this);
        binding.documentsList.setAdapter(documentAdapter);
        binding.documentsList.setEmptyView(binding.emptyView);

        pendingIncomingUri = extractIncomingUri(getIntent());
        setIntent(new Intent());

        binding.addProfileButton.setOnClickListener(view -> showAddProfileDialog());
        binding.importButton.setOnClickListener(view -> openDocumentLauncher.launch(
                new String[]{"application/pdf", "image/*"}
        ));
        binding.manualButton.setOnClickListener(view -> showManualResultDialog());
        binding.indicatorsButton.setOnClickListener(view -> openIndicators());
        binding.updateButton.setOnClickListener(view -> checkForUpdates(true));
        binding.backupButton.setOnClickListener(view -> showBackupActions());
        binding.documentsList.setOnItemClickListener((parent, view, position, id) -> {
            AnalysisDocumentEntity document = documentAdapter.getItem(position);
            if (document != null) {
                startActivity(DocumentDetailActivity.createIntent(this, document.id));
            }
        });
        binding.profileSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(
                position -> observeDocumentsForSelectedProfile()
        ));

        repository.observeProfiles().observe(this, this::renderProfiles);
        repository.ensureDefaultProfile();
        checkForUpdates(false);
        if (updateDownloader.hasPendingDownload()) {
            inspectPendingUpdate();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingInstallPermission && getPackageManager().canRequestPackageInstalls()) {
            awaitingInstallPermission = false;
            launchUpdateInstaller();
        } else if (updateDownloader != null && updateDownloader.hasPendingDownload()) {
            inspectPendingUpdate();
        }
    }

    @Override
    protected void onDestroy() {
        updateHandler.removeCallbacksAndMessages(null);
        clearPendingExportPassword();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri incoming = extractIncomingUri(intent);
        if (incoming != null) {
            if (profiles.isEmpty()) {
                pendingIncomingUri = incoming;
            } else {
                importUri(incoming);
            }
        }
    }

    private void showBackupActions() {
        new AlertDialog.Builder(this)
                .setTitle("Резервная копия")
                .setItems(
                        new String[]{
                                "Создать зашифрованную копию",
                                "Восстановить из копии"
                        },
                        (dialog, which) -> {
                            if (which == 0) {
                                beginBackupExport();
                            } else {
                                openBackupLauncher.launch(new String[]{
                                        "application/octet-stream",
                                        "application/zip",
                                        "*/*"
                                });
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void beginBackupExport() {
        showBackupPasswordDialog(true, password -> {
            clearPendingExportPassword();
            pendingExportPassword = password;
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
            createBackupLauncher.launch("LabHistory-" + date + ".labbackup");
        });
    }

    private void onBackupDestinationSelected(Uri destination) {
        char[] password = pendingExportPassword;
        pendingExportPassword = null;
        if (password == null) {
            return;
        }
        if (destination == null) {
            Arrays.fill(password, '\0');
            return;
        }

        setBackupBusy(true, false);
        try {
            backupRepository.exportBackup(destination, password, new BackupRepository.Callback() {
                @Override
                public void onSuccess(BackupSummary summary) {
                    runOnUiThread(() -> {
                        setBackupBusy(false, false);
                        Toast.makeText(
                                MainActivity.this,
                                "Копия создана: профилей " + summary.profiles
                                        + ", документов " + summary.documents
                                        + ", показателей " + summary.results,
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

                @Override
                public void onError(Exception error) {
                    runOnUiThread(() -> {
                        setBackupBusy(false, false);
                        Toast.makeText(
                                MainActivity.this,
                                "Не удалось создать копию: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }
            });
        } catch (Exception error) {
            setBackupBusy(false, false);
            Toast.makeText(
                    this,
                    "Не удалось создать копию: " + safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void onBackupSourceSelected(Uri source) {
        if (source == null) {
            return;
        }
        showBackupPasswordDialog(false, password -> restoreBackup(source, password));
    }

    private void restoreBackup(Uri source, char[] password) {
        setBackupBusy(true, true);
        try {
            backupRepository.importBackup(source, password, new BackupRepository.Callback() {
                @Override
                public void onSuccess(BackupSummary summary) {
                    runOnUiThread(() -> {
                        setBackupBusy(false, true);
                        String duplicates = summary.skippedDuplicates == 0
                                ? ""
                                : ", пропущено повторов " + summary.skippedDuplicates;
                        Toast.makeText(
                                MainActivity.this,
                                "Восстановлено: профилей " + summary.profiles
                                        + ", документов " + summary.documents
                                        + ", показателей " + summary.results
                                        + duplicates,
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

                @Override
                public void onError(Exception error) {
                    runOnUiThread(() -> {
                        setBackupBusy(false, true);
                        Toast.makeText(
                                MainActivity.this,
                                "Не удалось восстановить копию: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }
            });
        } catch (Exception error) {
            setBackupBusy(false, true);
            Toast.makeText(
                    this,
                    "Не удалось восстановить копию: " + safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void showBackupPasswordDialog(boolean requireConfirmation, PasswordCallback callback) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        fields.setPadding(padding, dp(4), padding, 0);

        EditText passwordField = addField(fields, getString(R.string.backup_password_hint));
        passwordField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        EditText repeatedField = null;
        if (requireConfirmation) {
            repeatedField = addField(
                    fields,
                    getString(R.string.backup_password_repeat_hint)
            );
            repeatedField.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
        }
        EditText confirmationField = repeatedField;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.backup_password_title)
                .setMessage(R.string.backup_password_message)
                .setView(fields)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(requireConfirmation ? "Создать" : "Восстановить", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    char[] password = passwordField.getText().toString().toCharArray();
                    if (password.length < 8) {
                        Arrays.fill(password, '\0');
                        passwordField.setError("Минимум 8 символов");
                        return;
                    }
                    if (confirmationField != null) {
                        char[] confirmation = confirmationField.getText().toString().toCharArray();
                        boolean matches = Arrays.equals(password, confirmation);
                        Arrays.fill(confirmation, '\0');
                        if (!matches) {
                            Arrays.fill(password, '\0');
                            confirmationField.setError("Пароли не совпадают");
                            return;
                        }
                    }
                    callback.onPassword(password);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void setBackupBusy(boolean busy, boolean restoring) {
        binding.backupButton.setEnabled(!busy);
        binding.backupButton.setText(
                busy
                        ? (restoring
                        ? R.string.restore_in_progress
                        : R.string.backup_in_progress)
                        : R.string.backup_and_restore
        );
    }

    private void clearPendingExportPassword() {
        if (pendingExportPassword != null) {
            Arrays.fill(pendingExportPassword, '\0');
            pendingExportPassword = null;
        }
    }

    private void checkForUpdates(boolean manual) {
        if (!manual) {
            long lastCheck = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                    .getLong(KEY_LAST_UPDATE_CHECK, 0L);
            if (System.currentTimeMillis() - lastCheck < UPDATE_CHECK_INTERVAL_MS) {
                return;
            }
        }

        binding.updateButton.setEnabled(false);
        binding.updateButton.setText("Проверяю обновления…");
        updateChecker.check(new UpdateChecker.Callback() {
            @Override
            public void onSuccess(UpdateInfo updateInfo) {
                runOnUiThread(() -> {
                    binding.updateButton.setEnabled(true);
                    binding.updateButton.setText("Проверить обновления");
                    getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis())
                            .apply();

                    if (updateInfo.versionCode > BuildConfig.VERSION_CODE
                            && Build.VERSION.SDK_INT >= updateInfo.minimumAndroid) {
                        showAvailableUpdate(updateInfo);
                    } else if (manual) {
                        Toast.makeText(
                                MainActivity.this,
                                "Установлена актуальная версия " + BuildConfig.VERSION_NAME,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.updateButton.setEnabled(true);
                    binding.updateButton.setText("Проверить обновления");
                    if (manual) {
                        Toast.makeText(
                                MainActivity.this,
                                "Не удалось проверить обновления: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }
        });
    }

    private void showAvailableUpdate(UpdateInfo updateInfo) {
        StringBuilder message = new StringBuilder()
                .append("Доступна версия ")
                .append(updateInfo.versionName)
                .append(".\n\n");
        if (!updateInfo.releaseNotes.isEmpty()) {
            message.append(updateInfo.releaseNotes).append("\n\n");
        }
        message.append("APK будет загружен с GitHub и проверен по SHA-256.");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Обновление приложения")
                .setMessage(message.toString())
                .setPositiveButton("Скачать", (dialog, which) -> downloadUpdate(updateInfo));
        if (!updateInfo.required) {
            builder.setNegativeButton("Позже", null);
        }
        builder.show();
    }

    private void downloadUpdate(UpdateInfo updateInfo) {
        try {
            updateReadyDialogShown = false;
            updateDownloader.enqueue(updateInfo);
            Toast.makeText(this, "Обновление загружается", Toast.LENGTH_LONG).show();
            scheduleUpdateInspection();
        } catch (Exception error) {
            Toast.makeText(
                    this,
                    "Не удалось начать загрузку: " + safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void scheduleUpdateInspection() {
        updateHandler.removeCallbacksAndMessages(null);
        updateHandler.postDelayed(this::inspectPendingUpdate, 1500L);
    }

    private void inspectPendingUpdate() {
        if (updateInspectionRunning || updateDownloader == null) {
            return;
        }
        updateInspectionRunning = true;
        updateDownloader.inspectPending((state, message) -> runOnUiThread(() -> {
            updateInspectionRunning = false;
            if (state == UpdateDownloader.State.DOWNLOADING) {
                scheduleUpdateInspection();
            } else if (state == UpdateDownloader.State.READY) {
                showInstallUpdateDialog();
            } else if (state == UpdateDownloader.State.FAILED) {
                Toast.makeText(
                        MainActivity.this,
                        "Обновление не загружено: " + message,
                        Toast.LENGTH_LONG
                ).show();
            }
        }));
    }

    private void showInstallUpdateDialog() {
        if (updateReadyDialogShown || !updateDownloader.hasVerifiedUpdate()) {
            return;
        }
        updateReadyDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Обновление готово")
                .setMessage("Файл проверен. Android попросит подтвердить установку новой версии.")
                .setNegativeButton("Позже", (dialog, which) -> updateReadyDialogShown = false)
                .setPositiveButton("Установить", (dialog, which) -> {
                    updateReadyDialogShown = false;
                    launchUpdateInstaller();
                })
                .show();
    }

    private void launchUpdateInstaller() {
        UpdateDownloader.InstallResult result = updateDownloader.requestInstall(this);
        awaitingInstallPermission = result == UpdateDownloader.InstallResult.PERMISSION_REQUIRED;
        if (result == UpdateDownloader.InstallResult.NO_FILE) {
            Toast.makeText(this, "Файл обновления не найден", Toast.LENGTH_LONG).show();
        } else if (result == UpdateDownloader.InstallResult.PERMISSION_REQUIRED) {
            Toast.makeText(
                    this,
                    "Разрешите установку из этого источника и вернитесь в приложение",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void renderProfiles(List<ProfileEntity> updatedProfiles) {
        long previousId = getSelectedProfileId();
        profiles = updatedProfiles == null ? new ArrayList<>() : new ArrayList<>(updatedProfiles);

        ArrayAdapter<ProfileEntity> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                profiles
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.profileSpinner.setAdapter(adapter);

        int selectedIndex = 0;
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).id == previousId) {
                selectedIndex = index;
                break;
            }
        }
        if (!profiles.isEmpty()) {
            binding.profileSpinner.setSelection(selectedIndex);
            observeDocumentsForSelectedProfile();
        }

        if (pendingIncomingUri != null && !profiles.isEmpty()) {
            Uri uri = pendingIncomingUri;
            pendingIncomingUri = null;
            importUri(uri);
        }
    }

    private void observeDocumentsForSelectedProfile() {
        long profileId = getSelectedProfileId();
        if (profileId <= 0) {
            documentAdapter.replaceAll(new ArrayList<>());
            return;
        }

        if (currentDocuments != null) {
            currentDocuments.removeObservers(this);
        }
        currentDocuments = repository.observeDocuments(profileId);
        currentDocuments.observe(this, documentAdapter::replaceAll);
    }

    private long getSelectedProfileId() {
        int position = binding == null ? -1 : binding.profileSpinner.getSelectedItemPosition();
        if (position < 0 || position >= profiles.size()) {
            return -1;
        }
        return profiles.get(position).id;
    }

    private void openIndicators() {
        int position = binding.profileSpinner.getSelectedItemPosition();
        if (position < 0 || position >= profiles.size()) {
            Toast.makeText(this, "Сначала создайте профиль", Toast.LENGTH_SHORT).show();
            return;
        }
        ProfileEntity profile = profiles.get(position);
        startActivity(IndicatorsActivity.createIntent(this, profile.id, profile.name));
    }

    private void onDocumentSelected(Uri uri) {
        if (uri != null) {
            importUri(uri);
        }
    }

    private void importUri(Uri uri) {
        long profileId = getSelectedProfileId();
        if (profileId <= 0) {
            Toast.makeText(this, "Сначала создайте профиль", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Копирую документ во внутреннее хранилище…", Toast.LENGTH_SHORT).show();
        repository.importDocument(this, profileId, uri, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity value) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Документ сохранён. Начинаю локальную обработку…",
                        Toast.LENGTH_LONG
                ).show());
                repository.recognizeDocument(value.id, new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(AnalysisDocumentEntity recognizedDocument) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "Данные извлечены — откройте документ и проверьте их",
                                Toast.LENGTH_LONG
                        ).show());
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "Документ сохранён, но обработка не завершена: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show());
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Ошибка импорта: " + safeMessage(error),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void showAddProfileDialog() {
        EditText nameField = createField("Имя или псевдоним");
        nameField.setSingleLine(true);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Новый профиль")
                .setMessage("Для друга или родственника можно использовать псевдоним.")
                .setView(nameField)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = nameField.getText().toString().trim();
            if (name.isEmpty()) {
                nameField.setError("Введите имя");
                return;
            }
            repository.addProfile(name);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showManualResultDialog() {
        if (getSelectedProfileId() <= 0) {
            Toast.makeText(this, "Сначала создайте профиль", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        fields.setPadding(padding, dp(8), padding, dp(8));

        EditText labField = addField(fields, "Лаборатория, например ИНВИТРО");
        EditText testField = addField(fields, "Показатель, например Гемоглобин");
        EditText valueField = addField(fields, "Значение");
        EditText unitField = addField(fields, "Единица измерения");
        EditText lowField = addField(fields, "Референс: нижняя граница");
        EditText highField = addField(fields, "Референс: верхняя граница");

        valueField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        lowField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        highField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(fields);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Результат анализа")
                .setView(scrollView)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String testName = text(testField);
            String value = text(valueField);
            if (testName.isEmpty()) {
                testField.setError("Укажите показатель");
                return;
            }
            if (value.isEmpty()) {
                valueField.setError("Укажите значение");
                return;
            }

            ManualResultInput input = new ManualResultInput(
                    text(labField),
                    testName,
                    value,
                    text(unitField),
                    text(lowField),
                    text(highField)
            );
            long profileId = getSelectedProfileId();
            repository.addManualResult(profileId, input, new AnalysisRepository.Callback<>() {
                @Override
                public void onSuccess(Long documentId) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Результат сохранён",
                            Toast.LENGTH_SHORT
                    ).show());
                }

                @Override
                public void onError(Exception error) {
                    runOnUiThread(() -> Toast.makeText(
                            MainActivity.this,
                            "Ошибка сохранения: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show());
                }
            });
            dialog.dismiss();
        }));
        dialog.show();
    }

    private Uri extractIncomingUri(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            return intent.getData();
        }
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        }
        return null;
    }

    private EditText addField(LinearLayout parent, String hint) {
        EditText field = createField(hint);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(4);
        parent.addView(field, params);
        return field;
    }

    private EditText createField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        return field;
    }

    private String text(EditText field) {
        return field.getText().toString().trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void applySystemInsets() {
        boolean isNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                binding.getRoot()
        );
        controller.setAppearanceLightStatusBars(!isNight);
        controller.setAppearanceLightNavigationBars(!isNight);

        int basePadding = dp(16);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    basePadding + bars.left,
                    basePadding + bars.top,
                    basePadding + bars.right,
                    basePadding + bars.bottom
            );
            return windowInsets;
        });
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private interface PasswordCallback {
        void onPassword(char[] password);
    }
}
