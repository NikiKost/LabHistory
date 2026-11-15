package ru.labhistory.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.labhistory.app.R;
import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.IndicatorMeasurement;
import ru.labhistory.app.databinding.ActivityIndicatorHistoryBinding;
import ru.labhistory.app.parser.ReferenceRangeEvaluator;
import ru.labhistory.app.parser.TestNameNormalizer;

public class IndicatorHistoryActivity extends AppCompatActivity {
    private static final String EXTRA_PROFILE_ID = "profile_id";
    private static final String EXTRA_CANONICAL_KEY = "canonical_key";
    private static final String EXTRA_TEST_NAME = "test_name";
    private static final String EXTRA_UNIT = "unit";

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "dd.MM.yyyy",
            Locale.forLanguageTag("ru-RU")
    );
    private final List<IndicatorMeasurement> allMeasurements = new ArrayList<>();
    private ActivityIndicatorHistoryBinding binding;
    private AnalysisRepository repository;
    private long profileId;
    private String canonicalKey;
    private String testName;
    private String unit;
    private int periodFilterIndex;
    private String selectedLab;

    public static Intent createIntent(
            Context context,
            long profileId,
            String canonicalKey,
            String testName,
            String unit
    ) {
        return new Intent(context, IndicatorHistoryActivity.class)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_CANONICAL_KEY, canonicalKey)
                .putExtra(EXTRA_TEST_NAME, testName)
                .putExtra(EXTRA_UNIT, unit);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityIndicatorHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        profileId = getIntent().getLongExtra(EXTRA_PROFILE_ID, -1L);
        canonicalKey = getIntent().getStringExtra(EXTRA_CANONICAL_KEY);
        testName = getIntent().getStringExtra(EXTRA_TEST_NAME);
        unit = getIntent().getStringExtra(EXTRA_UNIT);
        if (profileId <= 0L || canonicalKey == null || testName == null) {
            finish();
            return;
        }
        if (unit == null) {
            unit = "";
        }

        repository = new AnalysisRepository(this);
        binding.titleText.setText(testName);
        binding.unitText.setText(unit.isEmpty() ? "Единица не указана" : "Единица: " + unit);
        binding.backButton.setOnClickListener(view -> finish());
        binding.renameIndicatorButton.setOnClickListener(view -> showRenameDialog());
        configureFilters();
        loadMeasurements();
    }

    private void configureFilters() {
        ArrayAdapter<CharSequence> periods = ArrayAdapter.createFromResource(
                this,
                R.array.history_period_filters,
                android.R.layout.simple_spinner_item
        );
        periods.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.periodFilter.setAdapter(periods);
        binding.periodFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                periodFilterIndex = position;
                applyMeasurementFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.labFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLab = position == 0 ? null : String.valueOf(parent.getItemAtPosition(position));
                applyMeasurementFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void showRenameDialog() {
        EditText nameField = new EditText(this);
        nameField.setSingleLine(false);
        nameField.setText(testName);
        nameField.selectAll();
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_indicator_title)
                .setMessage(R.string.rename_indicator_message)
                .setView(nameField)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> renameIndicator(
                        nameField.getText().toString()
                ))
                .show();
    }

    private void renameIndicator(String requestedName) {
        binding.renameIndicatorButton.setEnabled(false);
        repository.renameIndicatorGroup(
                profileId,
                canonicalKey,
                unit,
                requestedName,
                new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(TestNameNormalizer.NormalizedTest normalized) {
                        runOnUiThread(() -> {
                            canonicalKey = normalized.key;
                            testName = normalized.displayName;
                            binding.titleText.setText(testName);
                            binding.renameIndicatorButton.setEnabled(true);
                            Toast.makeText(
                                    IndicatorHistoryActivity.this,
                                    "Название сохранено",
                                    Toast.LENGTH_SHORT
                            ).show();
                            loadMeasurements();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            binding.renameIndicatorButton.setEnabled(true);
                            Toast.makeText(
                                    IndicatorHistoryActivity.this,
                                    "Не удалось изменить название: " + safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void loadMeasurements() {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.loadIndicatorMeasurements(
                profileId,
                canonicalKey,
                unit,
                new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(List<IndicatorMeasurement> measurements) {
                        runOnUiThread(() -> setMeasurements(measurements));
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(
                                    IndicatorHistoryActivity.this,
                                    "Не удалось загрузить историю: " + safeMessage(error),
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void setMeasurements(List<IndicatorMeasurement> measurements) {
        allMeasurements.clear();
        if (measurements != null) {
            allMeasurements.addAll(measurements);
        }
        updateLabFilter();
        applyMeasurementFilters();
    }

    private void updateLabFilter() {
        String previousLab = selectedLab;
        Set<String> laboratories = new LinkedHashSet<>();
        for (IndicatorMeasurement measurement : allMeasurements) {
            laboratories.add(displayLab(measurement.labName));
        }
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.all_laboratories));
        options.addAll(laboratories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.labFilter.setAdapter(adapter);
        int selectedPosition = previousLab == null ? 0 : options.indexOf(previousLab);
        if (selectedPosition < 1) {
            selectedLab = null;
            selectedPosition = 0;
        }
        binding.labFilter.setSelection(selectedPosition);
    }

    private void applyMeasurementFilters() {
        List<IndicatorMeasurement> filtered = new ArrayList<>();
        long cutoff = historyCutoff();
        for (IndicatorMeasurement measurement : allMeasurements) {
            boolean periodMatches = cutoff == Long.MIN_VALUE || measurement.analysisDate >= cutoff;
            boolean labMatches = selectedLab == null
                    || selectedLab.equals(displayLab(measurement.labName));
            if (periodMatches && labMatches) {
                filtered.add(measurement);
            }
        }
        showMeasurements(filtered);
    }

    private long historyCutoff() {
        int years;
        if (periodFilterIndex == 1) {
            years = 1;
        } else if (periodFilterIndex == 2) {
            years = 3;
        } else if (periodFilterIndex == 3) {
            years = 5;
        } else {
            return Long.MIN_VALUE;
        }
        Calendar cutoff = Calendar.getInstance();
        cutoff.add(Calendar.YEAR, -years);
        return cutoff.getTimeInMillis();
    }

    private void showMeasurements(List<IndicatorMeasurement> measurements) {
        binding.progressBar.setVisibility(View.GONE);
        binding.measurementsContainer.removeAllViews();
        boolean empty = measurements == null || measurements.isEmpty();
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.chartView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            return;
        }

        binding.chartView.setMeasurements(measurements);
        for (int index = measurements.size() - 1; index >= 0; index--) {
            addMeasurementRow(measurements.get(index));
        }
    }

    private void addMeasurementRow(IndicatorMeasurement measurement) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(getColor(R.color.surface_soft));

        TextView valueText = new TextView(this);
        String value = measurement.rawValue == null ? "" : measurement.rawValue;
        valueText.setText(value + (unit.isEmpty() ? "" : " " + unit));
        valueText.setTextColor(getColor(R.color.text_primary));
        valueText.setTextSize(18f);
        valueText.setTypeface(valueText.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(valueText);

        ReferenceRangeEvaluator.Status rangeStatus = ReferenceRangeEvaluator.evaluate(
                measurement.numericValue,
                measurement.referenceLow,
                measurement.referenceHigh
        );
        if (rangeStatus != ReferenceRangeEvaluator.Status.UNKNOWN) {
            TextView rangeStatusText = new TextView(this);
            if (rangeStatus == ReferenceRangeEvaluator.Status.BELOW) {
                rangeStatusText.setText(R.string.reference_status_below);
                rangeStatusText.setTextColor(getColor(R.color.danger));
                valueText.setTextColor(getColor(R.color.danger));
            } else if (rangeStatus == ReferenceRangeEvaluator.Status.ABOVE) {
                rangeStatusText.setText(R.string.reference_status_above);
                rangeStatusText.setTextColor(getColor(R.color.danger));
                valueText.setTextColor(getColor(R.color.danger));
            } else {
                rangeStatusText.setText(R.string.reference_status_within);
                rangeStatusText.setTextColor(getColor(R.color.primary));
            }
            rangeStatusText.setTextSize(13f);
            card.addView(rangeStatusText);
        }

        TextView dateText = new TextView(this);
        String lab = displayLab(measurement.labName);
        dateText.setText(dateFormat.format(new Date(measurement.analysisDate)) + " • " + lab);
        dateText.setTextColor(getColor(R.color.text_secondary));
        card.addView(dateText);

        if (measurement.referenceText != null && !measurement.referenceText.trim().isEmpty()) {
            TextView referenceText = new TextView(this);
            referenceText.setText("Референс: " + measurement.referenceText);
            referenceText.setTextColor(getColor(R.color.text_secondary));
            card.addView(referenceText);
        }

        Button editButton = new Button(this);
        editButton.setText(R.string.edit_measurement);
        editButton.setOnClickListener(view -> showEditMeasurementDialog(measurement));
        card.addView(editButton);

        Button deleteButton = new Button(this);
        deleteButton.setText(R.string.delete_measurement);
        deleteButton.setTextColor(getColor(R.color.danger));
        deleteButton.setOnClickListener(view -> confirmDeleteMeasurement(measurement));
        card.addView(deleteButton);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        binding.measurementsContainer.addView(card, params);
    }

    private String displayLab(String labName) {
        return labName == null || labName.trim().isEmpty()
                ? "Лаборатория не указана"
                : labName.trim();
    }

    private void showEditMeasurementDialog(IndicatorMeasurement measurement) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), 0, dp(20), 0);

        EditText nameField = addEditField(fields, "Название", measurement.testName, false);
        EditText valueField = addEditField(fields, "Значение", measurement.rawValue, true);
        EditText unitField = addEditField(fields, "Единица", measurement.unit, false);
        EditText referenceField = addEditField(
                fields,
                "Референс, например 4–10, <5 или от 4",
                measurement.referenceText,
                false
        );
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(fields);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_measurement_title)
                .setMessage(R.string.edit_measurement_note)
                .setView(scrollView)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (dialog, which) -> updateMeasurement(
                        measurement.resultId,
                        nameField.getText().toString(),
                        valueField.getText().toString(),
                        unitField.getText().toString(),
                        referenceField.getText().toString()
                ))
                .show();
    }

    private void confirmDeleteMeasurement(IndicatorMeasurement measurement) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_measurement_title)
                .setMessage(R.string.delete_measurement_message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, which) -> deleteMeasurement(
                        measurement.resultId
                ))
                .show();
    }

    private void deleteMeasurement(long resultId) {
        repository.deleteIndicatorMeasurement(
                profileId,
                resultId,
                new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    IndicatorHistoryActivity.this,
                                    "Измерение удалено",
                                    Toast.LENGTH_SHORT
                            ).show();
                            loadMeasurements();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> Toast.makeText(
                                IndicatorHistoryActivity.this,
                                "Не удалось удалить измерение: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private EditText addEditField(
            LinearLayout parent,
            String hint,
            String value,
            boolean numeric
    ) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value == null ? "" : value);
        field.setSingleLine(true);
        field.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                : InputType.TYPE_CLASS_TEXT);
        parent.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return field;
    }

    private void updateMeasurement(
            long resultId,
            String name,
            String value,
            String measurementUnit,
            String reference
    ) {
        repository.updateIndicatorMeasurement(
                profileId,
                resultId,
                name,
                value,
                measurementUnit,
                reference,
                new AnalysisRepository.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    IndicatorHistoryActivity.this,
                                    "Измерение сохранено",
                                    Toast.LENGTH_SHORT
                            ).show();
                            loadMeasurements();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> Toast.makeText(
                                IndicatorHistoryActivity.this,
                                "Не удалось сохранить измерение: " + safeMessage(error),
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
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
