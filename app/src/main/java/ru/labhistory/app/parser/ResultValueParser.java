package ru.labhistory.app.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResultValueParser {
    private static final String NUMBER = "[-+]?\\d+(?:[.,]\\d+)?";
    private static final Pattern RANGE = Pattern.compile(
            "^\\s*(" + NUMBER + ")\\s*(?:[-–—]|\\.\\.)\\s*(" + NUMBER + ")\\s*$"
    );
    private static final Pattern UPPER_BOUND = Pattern.compile(
            "(?iu)^\\s*(?:<|≤|до)\\s*(" + NUMBER + ")\\s*$"
    );
    private static final Pattern LOWER_BOUND = Pattern.compile(
            "(?iu)^\\s*(?:>|≥|от)\\s*(" + NUMBER + ")\\s*$"
    );

    private ResultValueParser() {
    }

    public static String normalizeNumber(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String candidate = rawValue.trim().replace(',', '.');
        return candidate.matches("[-+]?\\d+(?:\\.\\d+)?") ? candidate : null;
    }

    public static String[] parseReferenceRange(String referenceText) {
        if (referenceText == null) {
            return new String[]{null, null};
        }
        Matcher matcher = RANGE.matcher(referenceText.replace('−', '-'));
        if (matcher.matches()) {
            return new String[]{
                    matcher.group(1).replace(',', '.'),
                    matcher.group(2).replace(',', '.')
            };
        }
        matcher = UPPER_BOUND.matcher(referenceText.replace('−', '-'));
        if (matcher.matches()) {
            return new String[]{null, matcher.group(1).replace(',', '.')};
        }
        matcher = LOWER_BOUND.matcher(referenceText.replace('−', '-'));
        if (matcher.matches()) {
            return new String[]{matcher.group(1).replace(',', '.'), null};
        }
        return new String[]{null, null};
    }
}
