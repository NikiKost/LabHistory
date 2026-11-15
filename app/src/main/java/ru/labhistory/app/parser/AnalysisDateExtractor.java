package ru.labhistory.app.parser;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a labelled collection/result date and deliberately ignores dates of birth. */
public final class AnalysisDateExtractor {
    private static final String DATE = "(\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})";
    private static final Pattern[] PRIORITY_PATTERNS = {
            label("дата\\s+(?:при[её]ма\\s+)?взятия\\s+(?:био)?материала"),
            label("дата\\s+забора(?:\\s+(?:био)?материала)?"),
            label("дата\\s+сдачи(?:\\s+анализа)?"),
            label("дата\\s+(?:при[её]ма|поступления)\\s+(?:био)?материала"),
            label("дата\\s+(?:лабораторного\\s+)?исследования"),
            label("дата\\s+выполнения(?:\\s+исследования)?"),
            label("дата\\s+регистрации(?:\\s+(?:био)?материала)?")
    };
    private static final Pattern[] FALLBACK_PATTERNS = {
            label("дата\\s+(?:выдачи|готовности|результата|составления|формирования|печати)")
    };

    private AnalysisDateExtractor() {
    }

    public static Long extract(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.toLowerCase(Locale.forLanguageTag("ru-RU")).replace('ё', 'е');
        Long result = find(normalized, PRIORITY_PATTERNS);
        return result != null ? result : find(normalized, FALLBACK_PATTERNS);
    }

    private static Pattern label(String label) {
        // PDF text layers often place the value on the next visual line.
        return Pattern.compile("(?iu)" + label + "[^\\d]{0,60}" + DATE);
    }

    private static Long find(String text, Pattern[] patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                Long parsed = parseDate(matcher.group(1));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Long parseDate(String value) {
        String[] parts = value.split("[./-]");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year;
            int month;
            int day;
            if (parts[0].length() == 4) {
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
                day = Integer.parseInt(parts[2]);
            } else {
                day = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
                year = Integer.parseInt(parts[2]);
            }
            if (year < 1990 || year > 2100) {
                return null;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.setLenient(false);
            calendar.set(year, month - 1, day, 12, 0, 0);
            return calendar.getTimeInMillis();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
