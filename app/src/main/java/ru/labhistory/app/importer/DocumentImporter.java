package ru.labhistory.app.importer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public class DocumentImporter {
    public ImportedDocument copyToPrivateStorage(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String displayName = resolveDisplayName(resolver, uri);
        String mimeType = resolver.getType(uri);
        String extension = resolveExtension(displayName, mimeType);

        File directory = new File(context.getFilesDir(), "analysis_documents");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Не удалось создать внутреннюю папку документов");
        }

        File destination = new File(directory, UUID.randomUUID() + extension);
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) {
                throw new IOException("Не удалось открыть выбранный файл");
            }

            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw error;
        }

        String sourceType = isPdf(mimeType, extension) ? "PDF" : "IMAGE";
        String labName = LabFormatDetector.detect(displayName);
        return new ImportedDocument(displayName, destination.getAbsolutePath(), sourceType, labName);
    }

    private String resolveDisplayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        }
        String lastSegment = uri.getLastPathSegment();
        return lastSegment == null ? "document" : lastSegment;
    }

    private String resolveExtension(String displayName, String mimeType) {
        int dotIndex = displayName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < displayName.length() - 1) {
            String extension = displayName.substring(dotIndex).toLowerCase(Locale.ROOT);
            if (extension.matches("\\.[a-z0-9]{1,8}")) {
                return extension;
            }
        }

        if ("application/pdf".equalsIgnoreCase(mimeType)) {
            return ".pdf";
        }
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("png")) {
            return ".png";
        }
        return ".jpg";
    }

    private boolean isPdf(String mimeType, String extension) {
        return "application/pdf".equalsIgnoreCase(mimeType) || ".pdf".equals(extension);
    }
}
