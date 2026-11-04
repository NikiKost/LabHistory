package ru.labhistory.app.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.labhistory.app.BuildConfig;

public final class UpdateDownloader {
    public enum State {
        NONE,
        DOWNLOADING,
        READY,
        FAILED
    }

    public enum InstallResult {
        STARTED,
        PERMISSION_REQUIRED,
        NO_FILE
    }

    public interface Callback {
        void onState(State state, String message);
    }

    private static final String PREFS = "update_download";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_FILE_PATH = "file_path";
    private static final String KEY_SHA256 = "sha256";
    private static final String KEY_VERSION_CODE = "version_code";
    private static final String KEY_VERIFIED = "verified";
    private static final String MIME_APK = "application/vnd.android.package-archive";

    private final Context context;
    private final DownloadManager downloadManager;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public UpdateDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        removeInstalledUpdate();
    }

    public long enqueue(UpdateInfo info) throws IOException {
        File baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (baseDirectory == null) {
            throw new IOException("Внешнее хранилище приложения недоступно");
        }

        String relativePath = "updates/LabHistory-" + info.versionName + ".apk";
        File destination = new File(baseDirectory, relativePath);
        File parent = destination.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Не удалось создать папку обновлений");
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Не удалось заменить ранее загруженное обновление");
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("Мои анализы " + info.versionName)
                .setDescription("Загрузка обновления")
                .setMimeType(MIME_APK)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        relativePath
                );
        request.addRequestHeader("User-Agent", "LabHistory-Android/" + BuildConfig.VERSION_NAME);

        long downloadId = downloadManager.enqueue(request);
        preferences.edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_FILE_PATH, destination.getAbsolutePath())
                .putString(KEY_SHA256, info.sha256)
                .putInt(KEY_VERSION_CODE, info.versionCode)
                .putBoolean(KEY_VERIFIED, false)
                .apply();
        return downloadId;
    }

    public boolean hasPendingDownload() {
        return preferences.contains(KEY_DOWNLOAD_ID);
    }

    public boolean hasVerifiedUpdate() {
        String path = preferences.getString(KEY_FILE_PATH, null);
        return preferences.getBoolean(KEY_VERIFIED, false)
                && path != null
                && new File(path).isFile();
    }

    public void inspectPending(Callback callback) {
        long downloadId = preferences.getLong(KEY_DOWNLOAD_ID, -1L);
        if (downloadId < 0) {
            callback.onState(State.NONE, "");
            return;
        }

        executor.execute(() -> {
            try (Cursor cursor = downloadManager.query(
                    new DownloadManager.Query().setFilterById(downloadId)
            )) {
                if (cursor == null || !cursor.moveToFirst()) {
                    clearPending(false);
                    callback.onState(State.FAILED, "Загрузка обновления не найдена");
                    return;
                }

                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    verifyDownloadedFile(callback);
                } else if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    clearPending(true);
                    callback.onState(State.FAILED, "Ошибка загрузки, код " + reason);
                } else {
                    callback.onState(State.DOWNLOADING, "Обновление загружается");
                }
            } catch (Exception error) {
                callback.onState(State.FAILED, safeMessage(error));
            }
        });
    }

    public InstallResult requestInstall(Activity activity) {
        String path = preferences.getString(KEY_FILE_PATH, null);
        if (!preferences.getBoolean(KEY_VERIFIED, false) || path == null) {
            return InstallResult.NO_FILE;
        }

        File apk = new File(path);
        if (!apk.isFile()) {
            clearPending(false);
            return InstallResult.NO_FILE;
        }

        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(settingsIntent);
            return InstallResult.PERMISSION_REQUIRED;
        }

        Uri contentUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk
        );
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setData(contentUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(installIntent);
        return InstallResult.STARTED;
    }

    private void verifyDownloadedFile(Callback callback) {
        if (preferences.getBoolean(KEY_VERIFIED, false)) {
            callback.onState(State.READY, "Обновление готово к установке");
            return;
        }

        String path = preferences.getString(KEY_FILE_PATH, null);
        String expectedHash = preferences.getString(KEY_SHA256, null);
        if (path == null || expectedHash == null) {
            clearPending(false);
            callback.onState(State.FAILED, "Не найдены данные проверки обновления");
            return;
        }

        try {
            File apk = new File(path);
            String actualHash = sha256(apk);
            if (!expectedHash.equalsIgnoreCase(actualHash)) {
                clearPending(true);
                callback.onState(State.FAILED, "Контрольная сумма обновления не совпала");
                return;
            }
            preferences.edit().putBoolean(KEY_VERIFIED, true).apply();
            callback.onState(State.READY, "Обновление проверено и готово к установке");
        } catch (Exception error) {
            clearPending(true);
            callback.onState(State.FAILED, safeMessage(error));
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private void removeInstalledUpdate() {
        int pendingVersion = preferences.getInt(KEY_VERSION_CODE, Integer.MAX_VALUE);
        if (pendingVersion <= BuildConfig.VERSION_CODE) {
            clearPending(true);
        }
    }

    private void clearPending(boolean deleteFile) {
        String path = preferences.getString(KEY_FILE_PATH, null);
        if (deleteFile && path != null) {
            File file = new File(path);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        preferences.edit().clear().apply();
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
