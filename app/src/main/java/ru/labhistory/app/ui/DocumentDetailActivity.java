package ru.labhistory.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.DatePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.labhistory.app.R;
import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.LabResultEntity;
import ru.labhistory.app.databinding.ActivityDocumentDetailBinding;

public class DocumentDetailActivity extends SecureActivity {
    private static final String EXTRA_DOCUMENT_ID = "document_id";
    private static final int PREVIEW_MAX_SIDE = 1600;

    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "dd.MM.yyyy",
            new Locale("ru", "RU")
    );

    private ActivityDocumentDetailBinding binding;
    private AnalysisRepository repository;
    private AnalysisDocumentEntity document;
    private Bitmap previewBitmap;
    private String previewPath;
    private long documentId;

    public static Intent createIntent(Context context, long documentId) {
        return new Intent(context, DocumentDetailActivity.class)
                .putExtra(EXTRA_DOCUMENT_ID, documentId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityDocumentDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        documentId = getIntent().getLongExtra(EXTRA_DOCUMENT_ID, -1L);
        if (documentId <= 0L) {
            finish();
            return;
        }

        repository = new AnalysisRepository(this);
        binding.backButton.setOnClickListener(view -> finish());
        binding.openOriginalButton.setOnClickListener(view -> openOriginal());
        binding.recognizeButton.setOnClickListener(view -> recognizeDocument());
        binding.saveTextButton.setOnClickListener(view -> saveReviewedText());
        binding.reviewResultsButton.setOnClickListener(view -> openResultsReview());
        binding.changeDateButton.setOnClickListener(view -> showDatePicker());
        binding.deleteButton.setOnClickListener(view -> confirmDelete());

        repository.observeDocument(documentId).observe(this, updatedDocument -> {
            if (updatedDocument == null) {
                finish();
                return;
            }
            document = updatedDocument;
            renderDocument();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null && documentId > 0L) {
            loadResults();
        }
    }

    @Override
    protected void onDestroy() {
        previewExecutor.shutdownNow();
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        super.onDestroy();
    }

    private void renderDocument() {
        binding.titleText.setText(document.labName);
        binding.fileNameText.setText(document.originalFileName);
        binding.metaText.setText(getString(
                R.string.document_meta_format,
                dateFormat.format(new Date(document.analysisDate)),
                document.sourceType
        ));
        binding.statusText.setText(document.importStatus);
        if ("DOCUMENT".equals(document.analysisDateSource)) {
            binding.changeDateButton.setText(R.string.change_detected_analysis_date);
        } else if ("USER".equals(document.analysisDateSource)) {
            binding.changeDateButton.setText(R.string.change_user_analysis_date);
        } else if ("IMPORT".equals(document.analysisDateSource)) {
            binding.changeDateButton.setText(R.string.change_import_analysis_date);
        } else {
            binding.changeDateButton.setText(R.string.change_analysis_date);
        }

        boolean fileDocument = document.localPath != null && !"MANUAL".equals(document.sourceType);
        binding.openOriginalButton.setVisibility(fileDocument ? View.VISIBLE : View.GONE);
        binding.recognizeButton.setVisibility(fileDocument ? View.VISIBLE : View.GONE);
        binding.ocrSection.setVisibility(fileDocument ? View.VISIBLE : View.GONE);
        binding.previewContainer.setVisibility(fileDocument ? View.VISIBLE : View.GONE);
        binding.previewCaption.setVisibility(fileDocument ? View.VISIBLE : View.GONE);
        binding.previewCaption.setText("PDF".equals(document.sourceType)
                ? R.string.pdf_preview_caption
                : R.string.image_preview_caption);

        boolean recognizing = document.importStatus != null
                && (document.importStatus.startsWith("Распознавание")
                || document.importStatus.startsWith("Обработка"));
        binding.progressBar.setVisibility(recognizing ? View.VISIBLE : View.GONE);
        binding.recognizeButton.setEnabled(!recognizing);
        binding.recognizeButton.setText(document.ocrText == null
                ? R.string.recognize_text
                : R.string.recognize_again);

        String ocrText = document.ocrText == null ? "" : document.ocrText;
        if (!binding.ocrText.hasFocus()
                && !TextUtils.equals(binding.ocrText.getText().toString(), ocrText)) {
            binding.ocrText.setText(ocrText);
        }
        binding.saveTextButton.setEnabled(!recognizing);
        binding.reviewResultsButton.setVisibility(ocrText.trim().isEmpty() ? View.GONE : View.VISIBLE);
        binding.reviewResultsButton.setEnabled(!recognizing);

        if (fileDocument && !TextUtils.equals(previewPath, document.localPath)) {
            previewPath = document.localPath;
            loadPreview(document.localPath, document.sourceType);
        }
    }

    private void loadResults() {
        repository.loadResults(documentId, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(List<LabResultEntity> results) {
                runOnUiThread(() -> renderResults(results));
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        DocumentDetailActivity.this,
                        "Не удалось загрузить результаты: " + safeMessage(error),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void renderResults(List<LabResultEntity> results) {
        if (results == null || results.isEmpty()) {
            binding.resultsSection.setVisibility(View.GONE);
            return;
        }

        StringBuilder text = new StringBuilder();
        for (LabResultEntity result : results) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(result.testName).append(": ").append(result.rawValue);
            if (result.unit != null && !result.unit.trim().isEmpty()) {
                text.append(' ').append(result.unit);
            }
            if (result.referenceText != null && !result.referenceText.trim().isEmpty()) {
                text.append("\nРеференс: ").append(result.referenceText);
            }
        }
        binding.resultsText.setText(text.toString());
        binding.resultsSection.setVisibility(View.VISIBLE);
    }

    private void loadPreview(String localPath, String sourceType) {
        binding.previewProgress.setVisibility(View.VISIBLE);
        binding.previewImage.setVisibility(View.GONE);
        previewExecutor.execute(() -> {
            try {
                Bitmap bitmap = DocumentBitmapLoader.loadPreview(
                        new File(localPath),
                        sourceType,
                        PREVIEW_MAX_SIDE
                );
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !TextUtils.equals(previewPath, localPath)) {
                        bitmap.recycle();
                        return;
                    }
                    if (previewBitmap != null) {
                        previewBitmap.recycle();
                    }
                    previewBitmap = bitmap;
                    binding.previewImage.setImageBitmap(bitmap);
                    binding.previewImage.setVisibility(View.VISIBLE);
                    binding.previewProgress.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    binding.previewProgress.setVisibility(View.GONE);
                    binding.previewCaption.setText(getString(
                            R.string.preview_unavailable_format,
                            safeMessage(error)
                    ));
                });
            }
        });
    }

    private void recognizeDocument() {
        binding.recognizeButton.setEnabled(false);
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.recognizeDocument(documentId, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity value) {
                runOnUiThread(() -> {
                    binding.ocrText.clearFocus();
                    binding.ocrText.setText(value.ocrText == null ? "" : value.ocrText);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Данные извлечены. Проверьте их перед сохранением.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.recognizeButton.setEnabled(true);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Ошибка обработки: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void saveReviewedText() {
        String text = binding.ocrText.getText().toString().trim();
        if (text.isEmpty()) {
            binding.ocrText.setError("Сначала обработайте документ или введите текст");
            return;
        }
        repository.saveReviewedText(documentId, text, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity value) {
                runOnUiThread(() -> {
                    binding.ocrText.clearFocus();
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Проверенный текст сохранён",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> Toast.makeText(
                        DocumentDetailActivity.this,
                        "Не удалось сохранить текст: " + safeMessage(error),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private void openResultsReview() {
        String text = binding.ocrText.getText().toString().trim();
        if (text.isEmpty()) {
            binding.ocrText.setError("Сначала обработайте документ или введите текст");
            return;
        }
        binding.reviewResultsButton.setEnabled(false);
        repository.saveReviewedText(documentId, text, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity value) {
                runOnUiThread(() -> {
                    binding.reviewResultsButton.setEnabled(true);
                    startActivity(ReviewResultsActivity.createIntent(
                            DocumentDetailActivity.this,
                            documentId
                    ));
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.reviewResultsButton.setEnabled(true);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Не удалось сохранить текст: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void showDatePicker() {
        if (document == null) {
            return;
        }
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(document.analysisDate);
        android.app.DatePickerDialog dialog = new android.app.DatePickerDialog(
                this,
                (DatePicker view, int year, int month, int dayOfMonth) -> {
                    Calendar updated = Calendar.getInstance();
                    updated.clear();
                    updated.set(year, month, dayOfMonth, 12, 0, 0);
                    updateAnalysisDate(updated.getTimeInMillis());
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
        );
        dialog.setTitle("Дата сдачи анализа");
        dialog.show();
    }

    private void updateAnalysisDate(long analysisDate) {
        binding.changeDateButton.setEnabled(false);
        repository.updateAnalysisDate(documentId, analysisDate, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity value) {
                runOnUiThread(() -> {
                    binding.changeDateButton.setEnabled(true);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Дата сдачи сохранена",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.changeDateButton.setEnabled(true);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Не удалось изменить дату: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void openOriginal() {
        if (document == null || document.localPath == null) {
            return;
        }
        File source = new File(document.localPath);
        if (!source.isFile()) {
            Toast.makeText(this, "Исходный файл не найден", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                source
        );
        String mimeType = "PDF".equals(document.sourceType) ? "application/pdf" : "image/*";
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Открыть оригинал"));
        } catch (Exception error) {
            Toast.makeText(this, "Нет приложения для открытия файла", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить документ?")
                .setMessage("Будут удалены оригинальный файл, извлечённый текст и результаты этого документа. Отменить действие нельзя.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> deleteDocument())
                .show();
    }

    private void deleteDocument() {
        binding.deleteButton.setEnabled(false);
        repository.deleteDocument(documentId, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Документ удалён",
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.deleteButton.setEnabled(true);
                    Toast.makeText(
                            DocumentDetailActivity.this,
                            "Не удалось удалить документ: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
