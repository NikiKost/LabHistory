package ru.labhistory.app.parser;

import ru.labhistory.app.importer.LabFormatDetector;

public class Lab4uDocumentParser implements LabDocumentParser {
    @Override
    public boolean supports(String extractedText) {
        return "Lab4U".equals(LabFormatDetector.detect(extractedText));
    }

    @Override
    public ParsedAnalysis parse(String extractedText) {
        String preparedText = extractedText == null
                ? ""
                : extractedText.replaceAll(
                        "(?imu)^\\s*наименование\\s+(?:исследования|анализа).*результат.*$",
                        "Наименование теста Результат Единицы измерения Референсные значения"
                )
                .replaceAll(
                        "(?iu)\\s+(?:1|[тt](?:[тt])?|[|{])\\s+"
                                + "(?=[<>≤≥]?\\s*[-+]?\\d)",
                        " "
                )
                .replaceAll("(?m)(?<=\\d)\\s+\\$\\s+(?=[<>≤≥]?\\s*[-+]?\\d)", " % ");
        return LabParserDrafts.parse("Lab4U", preparedText);
    }
}
