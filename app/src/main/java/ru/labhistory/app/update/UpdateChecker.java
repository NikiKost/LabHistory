package ru.labhistory.app.update;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ru.labhistory.app.BuildConfig;

public final class UpdateChecker {
    public interface Callback {
        void onSuccess(UpdateInfo updateInfo);

        void onError(Exception error);
    }

    private static final int MAX_RESPONSE_LENGTH = 256 * 1024;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void check(Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BuildConfig.UPDATE_MANIFEST_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "LabHistory-Android/" + BuildConfig.VERSION_NAME);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Сервер обновлений вернул код " + responseCode);
                }

                String json;
                try (InputStream input = connection.getInputStream()) {
                    json = readLimited(input);
                }
                callback.onSuccess(UpdateInfo.fromJson(json));
            } catch (Exception error) {
                callback.onError(error);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String readLimited(InputStream input) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
                if (result.length() > MAX_RESPONSE_LENGTH) {
                    throw new IOException("Ответ сервера обновлений слишком большой");
                }
            }
        }
        return result.toString();
    }
}
