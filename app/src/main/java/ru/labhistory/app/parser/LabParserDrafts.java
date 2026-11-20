package ru.labhistory.app.parser;

import java.util.ArrayList;
import java.util.List;

import ru.labhistory.app.domain.ReviewResultInput;

final class LabParserDrafts {
    private static final String STRUCTURED_HEADER = "=== Показатели из текстового слоя PDF ===";

    private LabParserDrafts() {
    }

    static ParsedAnalysis parse(String labName, String extractedText) {
        List<ReviewResultInput> draftRows = new OcrResultParser().parse(extractedText);
        List<ParsedResult> results = new ArrayList<>();
        double confidence = isStructuredPdf(extractedText) ? 0.95 : 0.75;
        for (ReviewResultInput row : draftRows) {
            results.add(new ParsedResult(
                    row.testName,
                    row.value,
                    row.unit,
                    row.referenceText,
                    confidence
            ));
        }

        List<String> warnings = new ArrayList<>();
        if (results.isEmpty()) {
            warnings.add("Не удалось уверенно выделить строки показателей.");
        }
        return new ParsedAnalysis(labName, results, warnings);
    }

    private static boolean isStructuredPdf(String extractedText) {
        return extractedText != null && extractedText.trim().startsWith(STRUCTURED_HEADER);
    }
}
