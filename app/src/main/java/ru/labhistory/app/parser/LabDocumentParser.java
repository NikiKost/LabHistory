package ru.labhistory.app.parser;

public interface LabDocumentParser {
    boolean supports(String extractedText);

    ParsedAnalysis parse(String extractedText);
}
