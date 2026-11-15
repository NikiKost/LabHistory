package ru.labhistory.app.domain;

import java.util.Locale;

import ru.labhistory.app.data.AnalysisDocumentEntity;

public final class DocumentSearch {
    private DocumentSearch() {
    }

    public static boolean matches(AnalysisDocumentEntity document, String query) {
        if (document == null) {
            return false;
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String searchable = normalize(document.labName)
                + " " + normalize(document.originalFileName)
                + " " + normalize(document.sourceType)
                + " " + normalize(document.importStatus);
        return searchable.contains(normalizedQuery);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
