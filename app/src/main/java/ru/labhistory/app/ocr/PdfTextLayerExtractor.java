package ru.labhistory.app.ocr;

import android.content.Context;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.domain.ReviewResultInput;
import ru.labhistory.app.parser.AnalysisDateExtractor;
import ru.labhistory.app.parser.PdfTableRowAssembler;

final class PdfTextLayerExtractor {
    static final String STRUCTURED_HEADER = "=== Показатели из текстового слоя PDF ===";
    private static final int MIN_RESULT_ROWS = 2;

    private final Context appContext;

    PdfTextLayerExtractor(Context context) {
        appContext = context.getApplicationContext();
    }

    Extraction extract(File source) throws IOException {
        PDFBoxResourceLoader.init(appContext);
        List<PdfTableRowAssembler.Glyph> glyphs;
        String rawText;
        try (PDDocument document = PDDocument.load(source)) {
            PositionCollector collector = new PositionCollector();
            collector.setSortByPosition(true);
            collector.setSuppressDuplicateOverlappingText(true);
            rawText = collector.getText(document);
            glyphs = collector.glyphs;
        }

        List<ReviewResultInput> rows = new PdfTableRowAssembler().assemble(glyphs);
        if (rows.size() < MIN_RESULT_ROWS) {
            return new Extraction("", AnalysisDateExtractor.extract(rawText));
        }
        return new Extraction(
                toStructuredText(rows),
                AnalysisDateExtractor.extract(rawText)
        );
    }

    private String toStructuredText(List<ReviewResultInput> rows) {
        StringBuilder text = new StringBuilder(STRUCTURED_HEADER);
        for (ReviewResultInput row : rows) {
            text.append("\n\nНазвание: ").append(clean(row.testName));
            text.append("\nЗначение: ").append(clean(row.value));
            text.append("\nЕдиница: ").append(clean(row.unit));
            text.append("\nРеференс: ").append(clean(row.referenceText));
        }
        return text.toString();
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    static final class Extraction {
        final String text;
        final Long detectedAnalysisDate;

        Extraction(String text, Long detectedAnalysisDate) {
            this.text = text == null ? "" : text;
            this.detectedAnalysisDate = detectedAnalysisDate;
        }
    }

    private static final class PositionCollector extends PDFTextStripper {
        final List<PdfTableRowAssembler.Glyph> glyphs = new ArrayList<>();
        int pageNumber;
        float pageWidth;
        float pageHeight;

        PositionCollector() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pageNumber++;
            pageWidth = page.getCropBox().getWidth();
            pageHeight = page.getCropBox().getHeight();
            super.startPage(page);
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            String unicode = position.getUnicode();
            if (unicode != null && !unicode.isEmpty()) {
                String fontName = "";
                try {
                    fontName = position.getFont().getName().toLowerCase(Locale.ROOT);
                } catch (Exception ignored) {
                    // Font metadata is optional; coordinates and text remain usable.
                }
                glyphs.add(new PdfTableRowAssembler.Glyph(
                        pageNumber,
                        pageWidth,
                        pageHeight,
                        position.getXDirAdj(),
                        position.getYDirAdj(),
                        position.getWidthDirAdj(),
                        position.getHeightDir(),
                        unicode,
                        fontName.contains("bold") || fontName.contains("semibold")
                ));
            }
            super.processTextPosition(position);
        }
    }
}
