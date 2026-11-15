package ru.labhistory.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.List;

import ru.labhistory.app.data.AnalysisRepository;
import ru.labhistory.app.data.IndicatorSummary;
import ru.labhistory.app.databinding.ActivityIndicatorsBinding;

public class IndicatorsActivity extends AppCompatActivity {
    private static final String EXTRA_PROFILE_ID = "profile_id";
    private static final String EXTRA_PROFILE_NAME = "profile_name";

    private ActivityIndicatorsBinding binding;
    private AnalysisRepository repository;
    private IndicatorListAdapter adapter;
    private long profileId;
    private String profileName;

    public static Intent createIntent(Context context, long profileId, String profileName) {
        return new Intent(context, IndicatorsActivity.class)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_PROFILE_NAME, profileName);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityIndicatorsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applySystemInsets();

        profileId = getIntent().getLongExtra(EXTRA_PROFILE_ID, -1L);
        profileName = getIntent().getStringExtra(EXTRA_PROFILE_NAME);
        if (profileId <= 0L) {
            finish();
            return;
        }

        repository = new AnalysisRepository(this);
        adapter = new IndicatorListAdapter(this);
        binding.profileText.setText("Профиль: " + (profileName == null ? "" : profileName));
        binding.indicatorsList.setAdapter(adapter);
        binding.indicatorsList.setEmptyView(binding.emptyView);
        binding.searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                adapter.applyFilter(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        binding.backButton.setOnClickListener(view -> finish());
        binding.indicatorsList.setOnItemClickListener((parent, view, position, id) -> {
            IndicatorSummary indicator = adapter.getItem(position);
            if (indicator != null) {
                startActivity(IndicatorHistoryActivity.createIntent(
                        this,
                        profileId,
                        indicator.canonicalKey,
                        indicator.testName,
                        indicator.unit
                ));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadIndicators();
    }

    private void loadIndicators() {
        binding.progressBar.setVisibility(View.VISIBLE);
        repository.loadIndicatorSummaries(profileId, new AnalysisRepository.Callback<>() {
            @Override
            public void onSuccess(List<IndicatorSummary> value) {
                runOnUiThread(() -> {
                    adapter.replaceAll(value);
                    binding.progressBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(
                            IndicatorsActivity.this,
                            "Не удалось загрузить показатели: " + safeMessage(error),
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
