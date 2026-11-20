package ru.labhistory.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.ArrayList;
import java.util.List;

import ru.labhistory.app.R;
import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.LabResultEntity;
import ru.labhistory.app.databinding.ActivityReviewResultsBinding;
import ru.labhistory.app.domain.ReviewResultInput;
import ru.labhistory.app.parser.LabDocumentParserRegistry;
import ru.labhistory.app.parser.ParsedAnalysis;
import ru.labhistory.app.parser.ParsedResult;

public class ReviewResultsActivity extends SecureActivity {
    private static final String EXTRA_DOCUMENT_ID = "document_id";

    private final List<ResultRow> rows = new ArrayList<>();
    private final LabDocumentParserRegistry parser = new LabDocumentParserRegistry();
    private ActivityReviewResultsBinding binding;
    private AnalysisRepository repository;
    private long documentId;
    private String ocrText;
    private String documentLabName;

    public static Intent createIntent(Context context, long documentId) {
        return new Intent(context, ReviewResultsActivity.class)
                .putExtra(EXTRA_DOCUMENT_ID, documentId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityReviewResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        documentId = getIntent().getLongExtra(EXTRA_DOCUMENT_ID, -1L);
        if (documentId <= 0L) {
            finish();
            return;
        }

        repository = new AnalysisRepository(this);
        binding.backButton.setOnClickListener(view -> finish());
        binding.addRowButton.setOnClickListener(view -> addRow(new ReviewResultInput("", "", "", "")));
        binding.rebuildButton.setOnClickListener(view -> rebuildFromOcr());
        binding.saveButton.setOnClickListener(view -> saveResults());
        loadReviewData();
    }

    private void loadReviewData() {
        setLoading(true);
        repository.loadDocument(documentId, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(AnalysisDocumentEntity document) {
                repository.loadResults(documentId, new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(List<LabResultEntity> savedResults) {
                        runOnUiThread(() -> showReviewData(document, savedResults));
                    }

                    @Override
                    public void onError(Exception error) {
                        showLoadError(error);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                showLoadError(error);
            }
        });
    }

    private void showReviewData(
            AnalysisDocumentEntity document,
            List<LabResultEntity> savedResults
    ) {
        binding.documentText.setText(document.originalFileName);
        ocrText = document.ocrText;
        documentLabName = document.labName;
        List<ReviewResultInput> inputs = new ArrayList<>();
        if (savedResults != null && !savedResults.isEmpty()) {
            for (LabResultEntity result : savedResults) {
                inputs.add(new ReviewResultInput(
                        result.testName,
                        result.rawValue,
                        result.unit,
                        result.referenceText
                ));
            }
            binding.parserInfo.setText(getString(
                    R.string.saved_results_info_format,
                    displayLabName(documentLabName),
                    inputs.size()
            ));
        } else {
            ParsedAnalysis parsed = parser.parse(documentLabName, document.ocrText);
            inputs.addAll(toReviewInputs(parsed));
            showParserInfo(parsed);
        }

        if (inputs.isEmpty()) {
            inputs.add(new ReviewResultInput("", "", "", ""));
            Toast.makeText(
                    this,
                    "Не удалось уверенно разделить текст. Добавьте строки вручную.",
                    Toast.LENGTH_LONG
            ).show();
        }
        showInputs(inputs);
        setLoading(false);
    }

    private void showInputs(List<ReviewResultInput> inputs) {
        rows.clear();
        binding.rowsContainer.removeAllViews();
        for (ReviewResultInput input : inputs) {
            addRow(input);
        }
    }

    private void rebuildFromOcr() {
        ParsedAnalysis parsed = parser.parse(documentLabName, ocrText);
        List<ReviewResultInput> inputs = toReviewInputs(parsed);
        if (inputs.isEmpty()) {
            Toast.makeText(
                    this,
                    "Не удалось найти строки таблицы. Можно добавить их вручную.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Пересобрать черновик?")
                .setMessage("Текущие поля на экране будут заменены результатом нового разбора. В базе ничего не изменится, пока вы не нажмёте «Сохранить показатели».")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Пересобрать", (dialog, which) -> {
                    showInputs(inputs);
                    showParserInfo(parsed);
                    binding.resultsScroll.post(() -> binding.resultsScroll.fullScroll(View.FOCUS_UP));
                    Toast.makeText(
                            this,
                            "Найдено строк: " + inputs.size() + ". Проверьте их по оригиналу.",
                            Toast.LENGTH_LONG
                    ).show();
                })
                .show();
    }

    private List<ReviewResultInput> toReviewInputs(ParsedAnalysis parsed) {
        List<ReviewResultInput> inputs = new ArrayList<>();
        for (ParsedResult result : parsed.results) {
            inputs.add(new ReviewResultInput(
                    result.testName,
                    result.rawValue,
                    result.unit,
                    result.referenceText
            ));
        }
        return inputs;
    }

    private void showParserInfo(ParsedAnalysis parsed) {
        binding.parserInfo.setText(getString(
                R.string.parser_info_format,
                displayLabName(parsed.labName),
                parsed.results.size()
        ));
    }

    private String displayLabName(String labName) {
        return labName == null || labName.trim().isEmpty()
                ? "Не определена"
                : labName.trim();
    }

    private void addRow(ReviewResultInput input) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(getColor(R.color.surface_soft));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setTextColor(getColor(R.color.primary));
        title.setTextSize(17f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button removeButton = new Button(this);
        removeButton.setText("Удалить");
        removeButton.setTextColor(getColor(R.color.danger));
        removeButton.setMinWidth(0);
        header.addView(removeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        card.addView(header);

        EditText nameField = addField(card, "Название показателя", input.testName, false);
        EditText valueField = addField(card, "Значение", input.value, true);
        EditText unitField = addField(card, "Единица измерения", input.unit, false);
        EditText referenceField = addField(card, "Референс, например 4,0–10,0", input.referenceText, false);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(10);
        binding.rowsContainer.addView(card, cardParams);

        ResultRow row = new ResultRow(card, title, nameField, valueField, unitField, referenceField);
        rows.add(row);
        removeButton.setOnClickListener(view -> {
            rows.remove(row);
            binding.rowsContainer.removeView(card);
            updateRowNumbers();
        });
        updateRowNumbers();
    }

    private EditText addField(
            LinearLayout parent,
            String hint,
            String value,
            boolean numeric
    ) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        field.setTextColor(getColor(R.color.text_primary));
        field.setHintTextColor(getColor(R.color.text_secondary));
        field.setInputType(numeric
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return field;
    }

    private void updateRowNumbers() {
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).title.setText("Показатель " + (index + 1));
        }
    }

    private void saveResults() {
        if (rows.isEmpty()) {
            Toast.makeText(this, "Добавьте хотя бы один показатель", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ReviewResultInput> inputs = new ArrayList<>();
        for (ResultRow row : rows) {
            String name = text(row.nameField);
            String value = text(row.valueField);
            if (name.isEmpty()) {
                row.nameField.setError("Укажите название");
                row.nameField.requestFocus();
                return;
            }
            if (value.isEmpty()) {
                row.valueField.setError("Укажите значение");
                row.valueField.requestFocus();
                return;
            }
            inputs.add(new ReviewResultInput(
                    name,
                    value,
                    text(row.unitField),
                    text(row.referenceField)
            ));
        }

        setLoading(true);
        repository.saveReviewedResults(documentId, inputs, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(Void value) {
                runOnUiThread(() -> {
                    Toast.makeText(
                            ReviewResultsActivity.this,
                            "Показатели сохранены и добавлены в историю",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(
                            ReviewResultsActivity.this,
                            "Не удалось сохранить: " + safeMessage(error),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void showLoadError(Exception error) {
        runOnUiThread(() -> {
            setLoading(false);
            Toast.makeText(
                    ReviewResultsActivity.this,
                    "Не удалось открыть показатели: " + safeMessage(error),
                    Toast.LENGTH_LONG
            ).show();
            finish();
        });
    }

    private void setLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.resultsScroll.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        binding.addRowButton.setEnabled(!loading);
        binding.rebuildButton.setEnabled(!loading);
        binding.saveButton.setEnabled(!loading);
    }

    private String text(EditText field) {
        return field.getText().toString().trim();
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

    private static final class ResultRow {
        final LinearLayout card;
        final TextView title;
        final EditText nameField;
        final EditText valueField;
        final EditText unitField;
        final EditText referenceField;

        ResultRow(
                LinearLayout card,
                TextView title,
                EditText nameField,
                EditText valueField,
                EditText unitField,
                EditText referenceField
        ) {
            this.card = card;
            this.title = title;
            this.nameField = nameField;
            this.valueField = valueField;
            this.unitField = unitField;
            this.referenceField = referenceField;
        }
    }
}
