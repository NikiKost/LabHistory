package ru.labhistory.app.parser;

import java.util.Arrays;
import java.util.List;

import ru.labhistory.app.importer.LabFormatDetector;

public final class LabDocumentParserRegistry {
    private final List<LabDocumentParser> parsers = Arrays.asList(
            new InvitroDocumentParser(),
            new Lab4uDocumentParser()
    );

    public ParsedAnalysis parse(String labHint, String extractedText) {
        LabDocumentParser parser = findParser(labHint);
        if (parser == null) {
            parser = findParser(extractedText);
        }
        if (parser != null) {
            return parser.parse(extractedText);
        }

        String detectedLab = LabFormatDetector.detect(join(labHint, extractedText));
        return LabParserDrafts.parse(detectedLab, extractedText);
    }

    private LabDocumentParser findParser(String text) {
        for (LabDocumentParser parser : parsers) {
            if (parser.supports(text)) {
                return parser;
            }
        }
        return null;
    }

    private String join(String first, String second) {
        String safeFirst = first == null ? "" : first;
        String safeSecond = second == null ? "" : second;
        return safeFirst + "\n" + safeSecond;
    }
}
