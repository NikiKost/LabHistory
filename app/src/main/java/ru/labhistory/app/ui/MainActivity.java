package ru.labhistory.app.ui;

import android.content.Intent;
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
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import ru.labhistory.app.BuildConfig;
import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.LabResultEntity;
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

    private final ActivityResultLauncher<String[]> openDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onDocumentSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new AnalysisRepository(this);
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
        binding.updateButton.setOnClickListener(view -> checkForUpdates(true));
        binding.documentsList.setOnItemClickListener((parent, view, position, id) -> {
            AnalysisDocumentEntity document = documentAdapter.getItem(position);
            if (document != null) {
                showDocumentDetails(document);
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
                        "Документ сохранён",
                        Toast.LENGTH_SHORT
                ).show());
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

    private void showDocumentDetails(AnalysisDocumentEntity document) {
        repository.loadResults(document.id, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(List<LabResultEntity> results) {
                runOnUiThread(() -> {
                    StringBuilder message = new StringBuilder();
                    message.append("Источник: ").append(document.originalFileName).append("\n");
                    message.append("Статус: ").append(document.importStatus).append("\n\n");

                    if (results.isEmpty()) {
                        message.append("Оригинал сохранён внутри приложения. Распознавание PDF и фотографий будет подключено на следующем этапе.");
                    } else {
                        for (LabResultEntity result : results) {
                            message.append(result.testName)
                                    .append(": ")
                                    .append(result.rawValue);
                            if (result.unit != null && !result.unit.trim().isEmpty()) {
                                message.append(' ').append(result.unit);
                            }
                            if (result.referenceText != null && !result.referenceText.trim().isEmpty()) {
                                message.append("\nРеференс: ").append(result.referenceText);
                            }
                            message.append("\n\n");
                        }
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(document.labName)
                            .setMessage(message.toString().trim())
                            .setPositiveButton("Закрыть", null)
                            .show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "Не удалось открыть результат: " + safeMessage(error),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
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

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
