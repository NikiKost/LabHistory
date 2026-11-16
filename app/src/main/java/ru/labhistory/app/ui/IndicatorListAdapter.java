package ru.labhistory.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.R;
import ru.labhistory.app.data.IndicatorSummary;
import ru.labhistory.app.parser.ReferenceRangeEvaluator;

public class IndicatorListAdapter extends ArrayAdapter<IndicatorSummary> {
    private final LayoutInflater inflater;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "dd.MM.yyyy",
            Locale.forLanguageTag("ru-RU")
    );
    private final List<IndicatorSummary> allIndicators = new ArrayList<>();
    private String currentQuery = "";
    private boolean outsideOnly;

    public IndicatorListAdapter(Context context) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
    }

    public void replaceAll(List<IndicatorSummary> indicators) {
        allIndicators.clear();
        if (indicators != null) {
            allIndicators.addAll(indicators);
        }
        publishFilteredItems();
    }

    public void applyFilter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        publishFilteredItems();
    }

    public void setOutsideOnly(boolean outsideOnly) {
        this.outsideOnly = outsideOnly;
        publishFilteredItems();
    }

    private void publishFilteredItems() {
        clear();
        for (IndicatorSummary indicator : allIndicators) {
            String name = indicator.testName == null ? "" : indicator.testName;
            String unit = indicator.unit == null ? "" : indicator.unit;
            String searchable = (name + " " + unit).toLowerCase(Locale.ROOT);
            boolean matchesQuery = currentQuery.isEmpty() || searchable.contains(currentQuery);
            if (matchesQuery && (!outsideOnly || indicator.isLatestOutsideReference())) {
                add(indicator);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_indicator, parent, false);
            holder = new ViewHolder(
                    convertView.findViewById(R.id.nameText),
                    convertView.findViewById(R.id.metaText),
                    convertView.findViewById(R.id.statusText)
            );
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        IndicatorSummary indicator = getItem(position);
        if (indicator != null) {
            holder.name.setText(indicator.testName);
            String unit = indicator.unit == null || indicator.unit.trim().isEmpty()
                    ? "без единицы"
                    : indicator.unit;
            holder.meta.setText(getContext().getString(
                    R.string.indicator_meta_format,
                    unit,
                    indicator.measurementCount,
                    dateFormat.format(new Date(indicator.latestDate))
            ));
            bindReferenceStatus(holder.status, indicator, unit);
        }
        return convertView;
    }

    private void bindReferenceStatus(
            TextView statusView,
            IndicatorSummary indicator,
            String displayUnit
    ) {
        ReferenceRangeEvaluator.Status status = indicator.getLatestReferenceStatus();
        if (status != ReferenceRangeEvaluator.Status.BELOW
                && status != ReferenceRangeEvaluator.Status.ABOVE) {
            statusView.setVisibility(View.GONE);
            return;
        }
        int textResource = status == ReferenceRangeEvaluator.Status.BELOW
                ? R.string.indicator_latest_below
                : R.string.indicator_latest_above;
        String value = indicator.latestNumericValue == null ? "" : indicator.latestNumericValue;
        String valueWithUnit = displayUnit.equals("без единицы")
                ? value
                : value + " " + displayUnit;
        statusView.setText(getContext().getString(textResource, valueWithUnit));
        statusView.setVisibility(View.VISIBLE);
    }

    private static final class ViewHolder {
        final TextView name;
        final TextView meta;
        final TextView status;

        ViewHolder(TextView name, TextView meta, TextView status) {
            this.name = name;
            this.meta = meta;
            this.status = status;
        }
    }
}
