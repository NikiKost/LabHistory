package ru.labhistory.app.domain;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ProfileValidator {
    private ProfileValidator() {
    }

    public static boolean isValidBirthDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String cleaned = value.trim();
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(cleaned, position);
        return parsed != null
                && position.getIndex() == cleaned.length()
                && !parsed.after(new Date());
    }
}
