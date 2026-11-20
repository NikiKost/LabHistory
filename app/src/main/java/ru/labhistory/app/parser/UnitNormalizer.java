package ru.labhistory.app.parser;

import java.util.Locale;

/** Normalizes spelling only; it never converts the numeric value. */
public final class UnitNormalizer {
    private UnitNormalizer() {
    }

    public static String normalize(String sourceUnit) {
        String original = sourceUnit == null ? "" : sourceUnit.replaceAll("[\\r\\n]+", " ").trim();
        if (original.isEmpty()) {
            return "";
        }
        String withoutInterpretation = original.replaceFirst(
                "(?iu)^\\s*(?:повыш(?:ен(?:ный|ная|ное|ные|о)?|ено)?|"
                        + "пониж(?:ен(?:ный|ная|ное|ные|о)?|ено)?|"
                        + "повыше|пониже|выше|ниже|норм(?:а|альный|альная|альное|альные)?)"
                        + "\\s*[:;,.\\-–—()]*\\s+",
                ""
        );
        withoutInterpretation = withoutInterpretation.replaceFirst(
                "(?iu)^[-+]?\\d+(?:[.,]\\d+)?\\s+"
                        + "(?=(?:ед+|u|ме)/[лl]\\s*$)",
                ""
        );
        String value = withoutInterpretation.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('l', 'л')
                .replace("⁹", "^9")
                .replace("¹²", "^12")
                .replace("×", "x")
                .replace("*", "x")
                .replaceAll("\\s+", "");

        if (value.equals("%") || value.equals("проц") || value.equals("процент")) {
            return "%";
        }
        if (value.matches("(?:x)?10\\^?9/л") || value.matches("10x9/л")) {
            return "10^9/л";
        }
        if (value.matches("(?:x)?10\\^?12/л") || value.matches("10x12/л")) {
            return "10^12/л";
        }
        if (value.matches("(?:ед+|u|ме)/л")) {
            return "Ед/л";
        }
        if (value.equals("г/л")) {
            return "г/л";
        }
        if (value.equals("мг/л")) {
            return "мг/л";
        }
        if (value.equals("ммоль/л")) {
            return "ммоль/л";
        }
        if (value.equals("мкмоль/л") || value.equals("мкмол/л")) {
            return "мкмоль/л";
        }
        if (value.equals("нмоль/л")) {
            return "нмоль/л";
        }
        if (value.equals("пмоль/л")) {
            return "пмоль/л";
        }
        if (value.equals("фл")) {
            return "фл";
        }
        if (value.equals("пг")) {
            return "пг";
        }
        if (value.equals("мм/ч") || value.equals("мм/час")) {
            return "мм/ч";
        }
        return withoutInterpretation.replaceAll("\\s+", " ");
    }
}
