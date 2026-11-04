package ru.labhistory.app.importer;

import java.util.Locale;

public final class LabFormatDetector {
    private LabFormatDetector() {
    }

    public static String detect(String text) {
        if (text == null) {
            return "Не определена";
        }

        String normalized = text.toLowerCase(Locale.forLanguageTag("ru"));
        if (normalized.contains("инвитро") || normalized.contains("invitro")) {
            return "ИНВИТРО";
        }
        if (normalized.contains("lab4u") || normalized.contains("лаб4ю")) {
            return "Lab4U";
        }
        return "Не определена";
    }
}
