package ru.labhistory.app.parser;

import java.util.ArrayList;
import java.util.List;

import ru.labhistory.app.importer.LabFormatDetector;

public class Lab4uDocumentParser implements LabDocumentParser {
    @Override
    public boolean supports(String extractedText) {
        return "Lab4U".equals(LabFormatDetector.detect(extractedText));
    }

    @Override
    public ParsedAnalysis parse(String extractedText) {
        List<String> warnings = new ArrayList<>();
        warnings.add("Парсер Lab4U создан как точка расширения; извлечение строк анализа ещё не подключено.");
        return new ParsedAnalysis("Lab4U", new ArrayList<>(), warnings);
    }
}
