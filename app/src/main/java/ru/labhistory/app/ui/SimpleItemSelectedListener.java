package ru.labhistory.app.ui;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    public interface SelectionCallback {
        void onSelected(int position);
    }

    private final SelectionCallback callback;

    public SimpleItemSelectedListener(SelectionCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        callback.onSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // No action required.
    }
}
