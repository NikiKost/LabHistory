package ru.labhistory.app.parser;

import ru.labhistory.app.importer.LabFormatDetector;

public class InvitroDocumentParser implements LabDocumentParser {
    @Override
    public boolean supports(String extractedText) {
        return "ИНВИТРО".equals(LabFormatDetector.detect(extractedText));
    }

    @Override
    public ParsedAnalysis parse(String extractedText) {
        String preparedText = extractedText == null
                ? ""
                : extractedText.replaceAll(
                        "(?imu)^\\s*исследовани[ея]\\s+результат.*$",
                        "Название теста Результат Ед.изм. Норма"
                );
        return LabParserDrafts.parse("ИНВИТРО", preparedText);
    }
}
