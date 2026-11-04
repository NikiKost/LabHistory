package ru.labhistory.app.update;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class UpdateInfo {
    public final int versionCode;
    public final String versionName;
    public final int minimumAndroid;
    public final boolean required;
    public final String apkUrl;
    public final String sha256;
    public final String releaseNotes;

    public UpdateInfo(
            int versionCode,
            String versionName,
            int minimumAndroid,
            boolean required,
            String apkUrl,
            String sha256,
            String releaseNotes
    ) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minimumAndroid = minimumAndroid;
        this.required = required;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.releaseNotes = releaseNotes;
    }

    public static UpdateInfo fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        int versionCode = object.getInt("versionCode");
        String versionName = object.getString("versionName");
        int minimumAndroid = object.optInt("minimumAndroid", 26);
        boolean required = object.optBoolean("required", false);
        String apkUrl = object.getString("apkUrl");
        String sha256 = object.getString("sha256").toLowerCase();

        if (!apkUrl.startsWith("https://")) {
            throw new JSONException("Адрес APK должен использовать HTTPS");
        }
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new JSONException("Некорректная контрольная сумма APK");
        }

        JSONArray notes = object.optJSONArray("releaseNotes");
        StringBuilder notesText = new StringBuilder();
        if (notes != null) {
            for (int index = 0; index < notes.length(); index++) {
                String note = notes.optString(index, "").trim();
                if (!note.isEmpty()) {
                    if (notesText.length() > 0) {
                        notesText.append('\n');
                    }
                    notesText.append("• ").append(note);
                }
            }
        }

        return new UpdateInfo(
                versionCode,
                versionName,
                minimumAndroid,
                required,
                apkUrl,
                sha256,
                notesText.toString()
        );
    }
}
