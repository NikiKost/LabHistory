package ru.labhistory.app.parser;

import java.util.ArrayList;
import java.util.List;

import ru.labhistory.app.importer.LabFormatDetector;

public class InvitroDocumentParser implements LabDocumentParser {
    @Override
    public boolean supports(String extractedText) {
        return "ИНВИТРО".equals(LabFormatDetector.detect(extractedText));
    }

    @Override
    public ParsedAnalysis parse(String extractedText) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Парсер ИНВИТРО создан как точка расширения; извлечение строк анализа ещё не подключено.");
        return new ParsedAnalysis("ИНВИТРО", new ArrayList<>(), warnings);
    }
}
