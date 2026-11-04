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

public class DocumentListAdapter extends ArrayAdapter<AnalysisDocumentEntity> {
    private final LayoutInflater inflater;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy, HH:mm", new Locale("ru", "RU"));

    public DocumentListAdapter(Context context) {
        super(context, 0, new ArrayList<>());
        inflater = LayoutInflater.from(context);
    }

    public void replaceAll(List<AnalysisDocumentEntity> documents) {
        clear();
        if (documents != null) {
            addAll(documents);
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
            holder.title.setText(document.labName + " • " + document.sourceType);
            holder.subtitle.setText(dateFormat.format(new Date(document.analysisDate)) + "\n" + document.originalFileName);
            holder.status.setText(document.importStatus);
        }
        return convertView;
    }

    private static class ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView status;

        ViewHolder(TextView title, TextView subtitle, TextView status) {
            this.title = title;
            this.subtitle = subtitle;
            this.status = status;
        }
    }
}
