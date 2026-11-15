package ru.labhistory.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;

final class DocumentBitmapLoader {
    private DocumentBitmapLoader() {
    }

    static Bitmap loadPreview(File source, String sourceType, int maxSide) throws IOException {
        if ("PDF".equals(sourceType)) {
            return renderFirstPdfPage(source, maxSide);
        }
        return decodeImage(source, maxSide);
    }

    private static Bitmap renderFirstPdfPage(File source, int maxSide) throws IOException {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                source,
                ParcelFileDescriptor.MODE_READ_ONLY
        ); PdfRenderer renderer = new PdfRenderer(descriptor)) {
            if (renderer.getPageCount() == 0) {
                throw new IOException("PDF не содержит страниц");
            }
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                float scale = Math.min(
                        2.5f,
                        (float) maxSide / Math.max(page.getWidth(), page.getHeight())
                );
                int width = Math.max(1, Math.round(page.getWidth() * scale));
                int height = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(0xFFFFFFFF);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                return bitmap;
            }
        }
    }

    private static Bitmap decodeImage(File source, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Не удалось прочитать изображение");
        }

        int sampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sampleSize > maxSide) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (bitmap == null) {
            throw new IOException("Не удалось декодировать изображение");
        }
        return applyExifRotation(source, bitmap);
    }

    private static Bitmap applyExifRotation(File source, Bitmap bitmap) {
        try {
            ExifInterface exif = new ExifInterface(source.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            float rotation;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                rotation = 90f;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                rotation = 180f;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                rotation = 270f;
            } else {
                return bitmap;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (IOException ignored) {
            return bitmap;
        }
    }
}
