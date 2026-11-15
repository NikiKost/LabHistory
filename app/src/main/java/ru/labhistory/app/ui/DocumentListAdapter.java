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
import ru.labhistory.app.data.AnalysisDocumentEntity;
import ru.labhistory.app.domain.DocumentSearch;

public class DocumentListAdapter extends ArrayAdapter<AnalysisDocumentEntity> {
    private final LayoutInflater inflater;
    private final List<AnalysisDocumentEntity> sourceDocuments = new ArrayList<>();
    private final SimpleDateFormat dateKeyFormat = new SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.ROOT
    );
    private final SimpleDateFormat dateHeaderFormat = new SimpleDateFormat(
            "d MMMM yyyy",
            new Locale("ru", "RU")
    );
    private String query = "";

    public DocumentListAdapter(Context context) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
    }

    public void replaceAll(List<AnalysisDocumentEntity> documents) {
        sourceDocuments.clear();
        if (documents != null) {
            sourceDocuments.addAll(documents);
        }
        applyFilter();
    }

    public void setQuery(String query) {
        this.query = query == null ? "" : query;
        applyFilter();
    }

    private void applyFilter() {
        clear();
        for (AnalysisDocumentEntity document : sourceDocuments) {
            if (DocumentSearch.matches(document, query)) {
                add(document);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_document, parent, false);
            holder = new ViewHolder(
                    convertView.findViewById(R.id.dateHeaderText),
                    convertView.findViewById(R.id.titleText),
                    convertView.findViewById(R.id.subtitleText),
                    convertView.findViewById(R.id.statusText)
            );
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        AnalysisDocumentEntity document = getItem(position);
        if (document != null) {
            String currentDateKey = dateKeyFormat.format(new Date(document.analysisDate));
            AnalysisDocumentEntity previous = position == 0 ? null : getItem(position - 1);
            boolean showDateHeader = previous == null || !currentDateKey.equals(
                    dateKeyFormat.format(new Date(previous.analysisDate))
            );
            holder.dateHeader.setVisibility(showDateHeader ? View.VISIBLE : View.GONE);
            if (showDateHeader) {
                holder.dateHeader.setText(dateHeaderFormat.format(new Date(document.analysisDate)));
            }
            holder.title.setText(getContext().getString(
                    R.string.document_title_format,
                    document.labName,
                    document.sourceType
            ));
            holder.subtitle.setText(document.originalFileName);
            holder.status.setText(document.importStatus);
        }
        return convertView;
    }

    private static class ViewHolder {
        final TextView dateHeader;
        final TextView title;
        final TextView subtitle;
        final TextView status;

        ViewHolder(TextView dateHeader, TextView title, TextView subtitle, TextView status) {
            this.dateHeader = dateHeader;
            this.title = title;
            this.subtitle = subtitle;
            this.status = status;
        }
    }
}
