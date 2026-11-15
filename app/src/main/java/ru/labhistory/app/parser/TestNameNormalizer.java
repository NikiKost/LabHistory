package ru.labhistory.app.parser;

import java.util.Locale;

/** Maps laboratory aliases and verbose method names to a stable local identifier. */
public final class TestNameNormalizer {
    private TestNameNormalizer() {
    }

    public static NormalizedTest normalize(String sourceName) {
        String original = cleanDisplayName(sourceName);
        String value = searchable(original);

        if (isAbbreviation(value, "mchc", "мснс")
                || value.contains("средняя концентрация гемоглобина")) {
            return known("mchc", "MCHC (средняя концентрация гемоглобина)");
        }
        if (isAbbreviation(value, "mch", "мсн")
                || value.contains("среднее содержание гемоглобина")) {
            return known("mch", "MCH (среднее содержание гемоглобина)");
        }
        if (isAbbreviation(value, "mcv", "мсв")
                || value.contains("средний объем эритроцит")) {
            return known("mcv", "MCV (средний объём эритроцита)");
        }
        if (value.contains("rdw sd") || value.contains("ширина распределения эритроцит")) {
            return known("rdw_sd", "RDW-SD (ширина распределения эритроцитов)");
        }
        if (value.contains("rdw cv") || value.contains("гетерогенность эритроцит")) {
            return known("rdw_cv", "RDW-CV (гетерогенность эритроцитов)");
        }
        if (isAbbreviation(value, "mpv") || value.contains("средний объем тромбоцит")) {
            return known("mpv", "MPV (средний объём тромбоцита)");
        }
        if (isAbbreviation(value, "pdw") || value.contains("гетерогенность тромбоцит")) {
            return known("pdw", "PDW (гетерогенность тромбоцитов)");
        }
        if (isAbbreviation(value, "pct", "тромбокрит") || value.contains("тромбокрит")) {
            return known("pct", "PCT (тромбокрит)");
        }

        if (isAbbreviation(value, "алт", "alt") || value.contains("аланинаминотрансфераз")) {
            return known("alt", "АЛТ (аланинаминотрансфераза)");
        }
        if (isAbbreviation(value, "аст", "ast", "act") || value.contains("аспартатаминотрансфераз")) {
            return known("ast", "АСТ (аспартатаминотрансфераза)");
        }
        if (isAbbreviation(value, "ггт", "ggt", "ггтп") || value.contains("гамма глутамил")) {
            return known("ggt", "ГГТ (гамма-глутамилтрансфераза)");
        }
        if (isAbbreviation(value, "щф", "alp") || value.contains("щелочная фосфатаз")) {
            return known("alp", "ЩФ (щелочная фосфатаза)");
        }

        if (value.contains("гемоглобин") || isAbbreviation(value, "hgb", "hb")) {
            return known("hemoglobin", "Гемоглобин");
        }
        if (value.contains("гематокрит") || isAbbreviation(value, "hct")) {
            return known("hematocrit", "Гематокрит");
        }
        if (value.contains("эритроцит") || isAbbreviation(value, "rbc")) {
            return known("erythrocytes", "Эритроциты");
        }
        if (value.contains("лейкоцит") || isAbbreviation(value, "wbc")) {
            return known("leukocytes", "Лейкоциты");
        }
        if (value.contains("тромбоцит") || isAbbreviation(value, "plt")) {
            return known("platelets", "Тромбоциты");
        }
        if (value.contains("нейтрофил")) {
            return known("neutrophils", "Нейтрофилы");
        }
        if (value.contains("лимфоцит")) {
            return known("lymphocytes", "Лимфоциты");
        }
        if (value.contains("моноцит")) {
            return known("monocytes", "Моноциты");
        }
        if (value.contains("эозинофил")) {
            return known("eosinophils", "Эозинофилы");
        }
        if (value.contains("базофил")) {
            return known("basophils", "Базофилы");
        }
        if (value.contains("соэ") || value.contains("скорость оседания эритроцит")
                || value.contains("westergren")) {
            return known("esr", "СОЭ");
        }

        if (value.contains("белок общий") || value.contains("общий белок")) {
            return known("total_protein", "Общий белок");
        }
        if (value.contains("мочевин")) {
            return known("urea", "Мочевина");
        }
        if (value.contains("креатинин")) {
            return known("creatinine", "Креатинин");
        }
        if (value.contains("билирубин общий") || value.contains("общий билирубин")) {
            return known("total_bilirubin", "Билирубин общий");
        }
        if (value.contains("билирубин прям") || value.contains("прямой билирубин")) {
            return known("direct_bilirubin", "Билирубин прямой");
        }
        if (value.contains("глюкоз")) {
            return known("glucose", "Глюкоза");
        }
        if (value.contains("мочевая кислот") || value.contains("урат")) {
            return known("uric_acid", "Мочевая кислота");
        }
        if (value.contains("холестерин липопротеинов высокой")
                || value.contains("лпвп") || isAbbreviation(value, "hdl")) {
            return known("hdl", "ЛПВП (HDL)");
        }
        if (value.contains("холестерин липопротеинов низкой")
                || value.contains("лпнп") || isAbbreviation(value, "ldl")) {
            return known("ldl", "ЛПНП (LDL)");
        }
        if (value.contains("холестерин") || value.contains("холестерол")) {
            return known("total_cholesterol", "Холестерин общий");
        }
        if (value.contains("триглицерид")) {
            return known("triglycerides", "Триглицериды");
        }
        if (value.contains("глобулин связывающий половые гормоны")
                || isAbbreviation(value, "shbg", "гспг")) {
            return known("shbg", "ГСПГ (SHBG)");
        }

        String fallbackDisplay = stripMethodDetails(original);
        String fallbackKey = searchable(fallbackDisplay);
        if (fallbackKey.isEmpty()) {
            fallbackKey = "без названия";
        }
        if (fallbackDisplay.isEmpty()) {
            fallbackDisplay = "Без названия";
        }
        return new NormalizedTest("custom:" + fallbackKey, fallbackDisplay);
    }

    private static NormalizedTest known(String key, String displayName) {
        return new NormalizedTest(key, displayName);
    }

    private static boolean isAbbreviation(String value, String... aliases) {
        for (String alias : aliases) {
            if (value.equals(alias) || value.startsWith(alias + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String searchable(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[\\[\\](){}.,:;_\\-/]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String cleanDisplayName(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripMethodDetails(String value) {
        String result = cleanDisplayName(value)
                .replaceAll("(?iu),?\\s+(?:массовая|молярная|каталитическая)\\s+концентрация.*$", "")
                .replaceAll("(?iu)\\s+в\\s+(?:крови|сыворотке|плазме).*$", "")
                .replaceAll("(?iu)\\s+методом\\s+.*$", "")
                .trim();
        return result.isEmpty() ? cleanDisplayName(value) : result;
    }

    public static final class NormalizedTest {
        public final String key;
        public final String displayName;

        private NormalizedTest(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }
    }
}
