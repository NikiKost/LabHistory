package ru.labhistory.app.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.googlecode.tesseract.android.TessBaseAPI;

import ru.labhistory.app.parser.AnalysisDateExtractor;

public final class LocalOcrEngine {
    private static final int MAX_DECODE_SIDE = 4200;
    private static final int MAX_PDF_RENDER_SIDE = 3400;
    private static final int MAX_PDF_PAGES = 25;
    private static final String[] MODEL_FILES = {"rus.traineddata", "eng.traineddata"};

    private final Context appContext;
    private final PdfTextLayerExtractor pdfTextLayerExtractor;

    public LocalOcrEngine(Context context) {
        appContext = context.getApplicationContext();
        pdfTextLayerExtractor = new PdfTextLayerExtractor(appContext);
    }

    public DocumentRecognitionResult recognize(File source, String sourceType) throws IOException {
        Long textLayerDate = null;
        if ("PDF".equals(sourceType)) {
            try {
                PdfTextLayerExtractor.Extraction extraction = pdfTextLayerExtractor.extract(source);
                String textLayer = extraction.text.trim();
                textLayerDate = extraction.detectedAnalysisDate;
                if (!textLayer.isEmpty()) {
                    return new DocumentRecognitionResult(textLayer, textLayerDate);
                }
            } catch (Exception ignored) {
                // Scans and unsupported PDF layouts continue through local OCR.
            }
        }

        File dataRoot = ensureModels();
        TessBaseAPI tess = new TessBaseAPI();
        if (!tess.init(dataRoot.getAbsolutePath(), "rus+eng")) {
            tess.recycle();
            throw new IOException("Не удалось запустить локальный модуль распознавания");
        }
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        tess.setVariable("preserve_interword_spaces", "1");
        tess.setVariable("user_defined_dpi", "300");

        try {
            String recognizedText;
            if ("PDF".equals(sourceType)) {
                recognizedText = recognizePdf(tess, source);
            } else {
                Bitmap decoded = decodeImage(source, MAX_DECODE_SIDE);
                Bitmap bitmap = ImagePreprocessor.prepareForOcr(decoded);
                try {
                    recognizedText = recognizeBitmap(tess, bitmap);
                } finally {
                    bitmap.recycle();
                }
            }
            Long detectedDate = AnalysisDateExtractor.extract(recognizedText);
            return new DocumentRecognitionResult(
                    recognizedText,
                    detectedDate != null ? detectedDate : textLayerDate
            );
        } finally {
            tess.recycle();
        }
    }

    private String recognizePdf(TessBaseAPI tess, File source) throws IOException {
        StringBuilder text = new StringBuilder();
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                source,
                ParcelFileDescriptor.MODE_READ_ONLY
        ); PdfRenderer renderer = new PdfRenderer(descriptor)) {
            int pageCount = Math.min(renderer.getPageCount(), MAX_PDF_PAGES);
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    float scale = Math.min(
                            4.5f,
                            (float) MAX_PDF_RENDER_SIDE / Math.max(page.getWidth(), page.getHeight())
                    );
                    int width = Math.max(1, Math.round(page.getWidth() * scale));
                    int height = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    try {
                        rendered.eraseColor(0xFFFFFFFF);
                        page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        Bitmap bitmap = ImagePreprocessor.prepareForOcr(rendered);
                        rendered = null;
                        String pageText;
                        try {
                            pageText = recognizeBitmap(tess, bitmap).trim();
                        } finally {
                            bitmap.recycle();
                        }
                        if (!pageText.isEmpty()) {
                            if (text.length() > 0) {
                                text.append("\n\n");
                            }
                            text.append("--- Страница ").append(pageIndex + 1).append(" ---\n");
                            text.append(pageText);
                        }
                    } finally {
                        if (rendered != null) {
                            rendered.recycle();
                        }
                    }
                }
            }
        }
        return text.toString();
    }

    private String recognizeBitmap(TessBaseAPI tess, Bitmap bitmap) {
        tess.setImage(bitmap);
        String text = tess.getUTF8Text();
        tess.clear();
        return text == null ? "" : text;
    }

    private Bitmap decodeImage(File source, int maxSide) throws IOException {
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
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) {
            throw new IOException("Не удалось декодировать изображение");
        }
        return applyExifRotation(source, decoded);
    }

    private Bitmap applyExifRotation(File source, Bitmap bitmap) {
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

    private File ensureModels() throws IOException {
        File dataRoot = new File(appContext.getFilesDir(), "tesseract");
        File tessData = new File(dataRoot, "tessdata");
        if (!tessData.exists() && !tessData.mkdirs()) {
            throw new IOException("Не удалось подготовить папку OCR");
        }

        for (String modelName : MODEL_FILES) {
            File destination = new File(tessData, modelName);
            if (destination.isFile() && destination.length() > 1024L * 1024L) {
                continue;
            }
            copyModel(modelName, destination);
        }
        return dataRoot;
    }

    private void copyModel(String modelName, File destination) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".tmp");
        try (InputStream input = appContext.getAssets().open("tessdata/" + modelName);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw error;
        }

        if (destination.exists() && !destination.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Не удалось заменить модель " + modelName);
        }
        if (!temporary.renameTo(destination)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            throw new IOException("Не удалось установить модель " + modelName);
        }
    }
}
