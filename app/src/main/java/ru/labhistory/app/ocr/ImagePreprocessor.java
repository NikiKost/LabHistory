package ru.labhistory.app.ocr;

import android.graphics.Bitmap;
import android.graphics.Rect;

final class ImagePreprocessor {
    private static final int PAPER_ANALYSIS_MAX_SIDE = 420;
    private static final int OCR_MIN_SIDE = 2600;
    private static final int OCR_MAX_SIDE = 3400;
    private static final int OCR_MAX_PIXELS = 8_500_000;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private ImagePreprocessor() {
    }

    static Bitmap prepareForOcr(Bitmap source) {
        Bitmap cropped = cropToPaper(source);
        if (cropped != source) {
            source.recycle();
        }

        Bitmap scaled = scaleForOcr(cropped);
        if (scaled != cropped) {
            cropped.recycle();
        }

        Bitmap thresholded = adaptiveThreshold(scaled);
        scaled.recycle();
        return thresholded;
    }

    private static Bitmap cropToPaper(Bitmap source) {
        Rect bounds = findPaperBounds(source);
        if (bounds == null) {
            return source;
        }
        return Bitmap.createBitmap(
                source,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height()
        );
    }

    private static Rect findPaperBounds(Bitmap source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        float scale = Math.min(
                1f,
                (float) PAPER_ANALYSIS_MAX_SIDE / Math.max(sourceWidth, sourceHeight)
        );
        int sampleWidth = Math.max(1, Math.round(sourceWidth * scale));
        int sampleHeight = Math.max(1, Math.round(sourceHeight * scale));
        Bitmap sample = Bitmap.createScaledBitmap(source, sampleWidth, sampleHeight, true);

        int[] pixels = new int[sampleWidth * sampleHeight];
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
        if (sample != source) {
            sample.recycle();
        }

        int[] luminance = new int[pixels.length];
        int[] histogram = new int[256];
        for (int index = 0; index < pixels.length; index++) {
            int value = toLuminance(pixels[index]);
            luminance[index] = value;
            histogram[value]++;
        }

        int threshold = Math.max(105, Math.min(235, otsuThreshold(histogram, pixels.length) + 8));
        int minimumBrightPerRow = Math.max(2, Math.round(sampleWidth * 0.20f));
        int minimumBrightPerColumn = Math.max(2, Math.round(sampleHeight * 0.20f));

        int top = sampleHeight;
        int bottom = -1;
        for (int y = 0; y < sampleHeight; y++) {
            int bright = 0;
            int offset = y * sampleWidth;
            for (int x = 0; x < sampleWidth; x++) {
                if (luminance[offset + x] >= threshold) {
                    bright++;
                }
            }
            if (bright >= minimumBrightPerRow) {
                top = Math.min(top, y);
                bottom = y;
            }
        }

        int left = sampleWidth;
        int right = -1;
        for (int x = 0; x < sampleWidth; x++) {
            int bright = 0;
            for (int y = 0; y < sampleHeight; y++) {
                if (luminance[y * sampleWidth + x] >= threshold) {
                    bright++;
                }
            }
            if (bright >= minimumBrightPerColumn) {
                left = Math.min(left, x);
                right = x;
            }
        }

        if (right <= left || bottom <= top) {
            return null;
        }

        int sampleCropWidth = right - left + 1;
        int sampleCropHeight = bottom - top + 1;
        if (sampleCropWidth < sampleWidth * 0.35f || sampleCropHeight < sampleHeight * 0.35f) {
            return null;
        }
        if (sampleCropWidth > sampleWidth * 0.985f
                && sampleCropHeight > sampleHeight * 0.985f) {
            return null;
        }

        int paddingX = Math.max(2, Math.round(sampleWidth * 0.02f));
        int paddingY = Math.max(2, Math.round(sampleHeight * 0.02f));
        left = Math.max(0, left - paddingX);
        right = Math.min(sampleWidth - 1, right + paddingX);
        top = Math.max(0, top - paddingY);
        bottom = Math.min(sampleHeight - 1, bottom + paddingY);

        int sourceLeft = Math.max(0, (int) Math.floor((double) left * sourceWidth / sampleWidth));
        int sourceTop = Math.max(0, (int) Math.floor((double) top * sourceHeight / sampleHeight));
        int sourceRight = Math.min(
                sourceWidth,
                (int) Math.ceil((double) (right + 1) * sourceWidth / sampleWidth)
        );
        int sourceBottom = Math.min(
                sourceHeight,
                (int) Math.ceil((double) (bottom + 1) * sourceHeight / sampleHeight)
        );

        if (sourceRight - sourceLeft < 2 || sourceBottom - sourceTop < 2) {
            return null;
        }
        return new Rect(sourceLeft, sourceTop, sourceRight, sourceBottom);
    }

    private static Bitmap scaleForOcr(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longestSide = Math.max(width, height);
        int targetSide = Math.max(OCR_MIN_SIDE, Math.min(OCR_MAX_SIDE, longestSide));
        float sideScale = (float) targetSide / longestSide;
        float areaScale = (float) Math.sqrt((double) OCR_MAX_PIXELS / ((long) width * height));
        float scale = Math.min(sideScale, areaScale);
        if (Math.abs(scale - 1f) < 0.01f) {
            return source;
        }

        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
    }

    private static Bitmap adaptiveThreshold(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int pixelCount = width * height;
        int[] pixels = new int[pixelCount];
        source.getPixels(pixels, 0, width, 0, 0, width, height);

        byte[] gray = new byte[pixelCount];
        for (int index = 0; index < pixelCount; index++) {
            gray[index] = (byte) toLuminance(pixels[index]);
        }

        int radius = Math.max(16, Math.min(48, Math.min(width, height) / 45));
        int[] columnSums = new int[width];
        int initialBottom = Math.min(height - 1, radius);
        for (int y = 0; y <= initialBottom; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                columnSums[x] += gray[offset + x] & 0xFF;
            }
        }

        for (int y = 0; y < height; y++) {
            if (y > 0) {
                int rowToRemove = y - radius - 1;
                if (rowToRemove >= 0) {
                    int offset = rowToRemove * width;
                    for (int x = 0; x < width; x++) {
                        columnSums[x] -= gray[offset + x] & 0xFF;
                    }
                }
                int rowToAdd = y + radius;
                if (rowToAdd < height) {
                    int offset = rowToAdd * width;
                    for (int x = 0; x < width; x++) {
                        columnSums[x] += gray[offset + x] & 0xFF;
                    }
                }
            }

            int top = Math.max(0, y - radius);
            int bottom = Math.min(height - 1, y + radius);
            int windowHeight = bottom - top + 1;
            int right = Math.min(width - 1, radius);
            long windowSum = 0L;
            for (int x = 0; x <= right; x++) {
                windowSum += columnSums[x];
            }

            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int left = Math.max(0, x - radius);
                right = Math.min(width - 1, x + radius);
                int area = (right - left + 1) * windowHeight;
                int localMean = (int) (windowSum / area);
                int value = gray[rowOffset + x] & 0xFF;
                pixels[rowOffset + x] = value < localMean - 11 ? BLACK : WHITE;

                int columnToRemove = x - radius;
                if (columnToRemove >= 0) {
                    windowSum -= columnSums[columnToRemove];
                }
                int columnToAdd = x + radius + 1;
                if (columnToAdd < width) {
                    windowSum += columnSums[columnToAdd];
                }
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int toLuminance(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return (red * 77 + green * 150 + blue * 29) >> 8;
    }

    private static int otsuThreshold(int[] histogram, int totalPixels) {
        long totalValue = 0L;
        for (int value = 0; value < histogram.length; value++) {
            totalValue += (long) value * histogram[value];
        }

        long backgroundValue = 0L;
        int backgroundWeight = 0;
        double bestVariance = -1d;
        int bestThreshold = 160;
        for (int value = 0; value < histogram.length; value++) {
            backgroundWeight += histogram[value];
            if (backgroundWeight == 0) {
                continue;
            }
            int foregroundWeight = totalPixels - backgroundWeight;
            if (foregroundWeight == 0) {
                break;
            }

            backgroundValue += (long) value * histogram[value];
            double backgroundMean = (double) backgroundValue / backgroundWeight;
            double foregroundMean = (double) (totalValue - backgroundValue) / foregroundWeight;
            double difference = backgroundMean - foregroundMean;
            double variance = (double) backgroundWeight * foregroundWeight * difference * difference;
            if (variance > bestVariance) {
                bestVariance = variance;
                bestThreshold = value;
            }
        }
        return bestThreshold;
    }
}
