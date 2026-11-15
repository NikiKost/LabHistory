package ru.labhistory.app.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ru.labhistory.app.domain.ReviewResultInput;

public final class OcrResultParser {
    private static final String STRUCTURED_HEADER = "=== Показатели из текстового слоя PDF ===";
    private static final String NUMBER = "[-+]?\\d+(?:[.,]\\d+)?";
    private static final String REFERENCE = "(?:[<>≤≥]?\\s*)?" + NUMBER
            + "\\s*(?:[-–—]|\\.\\.)\\s*" + NUMBER;
    private static final Pattern COMPLETE_ROW = Pattern.compile(
            "^[|:;`'\\s]*(.+(?:\\p{L}|\\)))\\s+([<>≤≥]?\\s*" + NUMBER + ")"
                    + "\\s+(.{1,24}?)\\s+(" + REFERENCE + ")[|:;\\s]*$"
    );
    private static final Pattern VALUE_UNIT_REFERENCE_LINE = Pattern.compile(
            "^[|:;`'\\s]*([<>≤≥]?\\s*" + NUMBER + ")\\s+(.{1,24}?)\\s+("
                    + REFERENCE + ")[|:;\\s]*$"
    );
    private static final Pattern VALUE_LINE = Pattern.compile(
            "^[|:;`'\\s]*([<>≤≥]?\\s*[-+]?\\d+(?:[.,]\\d+)?)\\s*(.*?)\\s*$"
    );
    private static final Pattern REFERENCE_LINE = Pattern.compile(
            "^[|:;\\s]*(?:[<>≤≥]\\s*)?[-+]?\\d+(?:[.,]\\d+)?\\s*(?:[-–—]|\\.\\.)\\s*[-+]?\\d+(?:[.,]\\d+)?[|:;\\s]*$"
    );

    public List<ReviewResultInput> parse(String ocrText) {
        List<ReviewResultInput> results = new ArrayList<>();
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return results;
        }
        if (ocrText.trim().startsWith(STRUCTURED_HEADER)) {
            return parseStructuredPdfText(ocrText);
        }

        List<String> nameLines = new ArrayList<>();
        PendingResult pending = null;
        String lowerText = ocrText.toLowerCase(new Locale("ru", "RU"));
        boolean hasTableHeader = lowerText.contains("название теста")
                || lowerText.contains("наименование теста");
        boolean tableStarted = !hasTableHeader;
        String[] lines = ocrText.replace('\r', '\n').split("\\n+");
        for (String rawLine : lines) {
            String line = cleanLine(rawLine);
            String lower = line.toLowerCase(new Locale("ru", "RU"));
            if (lower.contains("название теста") || lower.contains("наименование теста")) {
                tableStarted = true;
                nameLines.clear();
                continue;
            }
            if (!tableStarted || line.isEmpty() || isHeader(line)) {
                continue;
            }

            if (looksLikeMetadata(line)) {
                if (pending != null) {
                    results.add(pending.withReference(""));
                    pending = null;
                }
                nameLines.clear();
                continue;
            }

            if (pending != null) {
                if (looksLikeReference(line)) {
                    results.add(pending.withReference(cleanReference(line)));
                    pending = null;
                    continue;
                }
                results.add(pending.withReference(""));
                pending = null;
            }

            ReviewResultInput completeRow = parseCompleteRow(rawLine);
            if (completeRow != null) {
                results.add(completeRow);
                nameLines.clear();
                continue;
            }

            Matcher valueReferenceMatcher = VALUE_UNIT_REFERENCE_LINE.matcher(line);
            if (valueReferenceMatcher.matches() && !nameLines.isEmpty()) {
                String testName = joinName(nameLines);
                nameLines.clear();
                if (!testName.isEmpty()) {
                    results.add(new ReviewResultInput(
                            testName,
                            compactValue(valueReferenceMatcher.group(1)),
                            cleanUnit(valueReferenceMatcher.group(2)),
                            cleanReference(valueReferenceMatcher.group(3))
                    ));
                }
                continue;
            }

            Matcher valueMatcher = VALUE_LINE.matcher(line);
            if (valueMatcher.matches() && !nameLines.isEmpty()) {
                if (looksLikeDateRemainder(valueMatcher.group(2))) {
                    nameLines.clear();
                    continue;
                }
                String testName = joinName(nameLines);
                nameLines.clear();
                if (!testName.isEmpty()) {
                    String value = compactValue(valueMatcher.group(1));
                    String unit = cleanUnit(valueMatcher.group(2));
                    pending = new PendingResult(testName, value, unit);
                }
                continue;
            }

            if (!looksLikeFooter(line)) {
                nameLines.add(line);
                if (nameLines.size() > 4) {
                    nameLines.remove(0);
                }
            }
        }

        if (pending != null) {
            results.add(pending.withReference(""));
        }
        return results;
    }

    private List<ReviewResultInput> parseStructuredPdfText(String text) {
        List<ReviewResultInput> results = new ArrayList<>();
        String name = null;
        String value = "";
        String unit = "";
        String reference = "";
        String[] lines = text.replace('\r', '\n').split("\\n+");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("Название:")) {
                if (name != null && !name.trim().isEmpty() && !value.trim().isEmpty()) {
                    results.add(new ReviewResultInput(name, value, unit, reference));
                }
                name = line.substring("Название:".length()).trim();
                value = "";
                unit = "";
                reference = "";
            } else if (line.startsWith("Значение:")) {
                value = line.substring("Значение:".length()).trim();
            } else if (line.startsWith("Единица:")) {
                unit = line.substring("Единица:".length()).trim();
            } else if (line.startsWith("Референс:")) {
                reference = line.substring("Референс:".length()).trim();
            }
        }
        if (name != null && !name.trim().isEmpty() && !value.trim().isEmpty()) {
            results.add(new ReviewResultInput(name, value, unit, reference));
        }
        return results;
    }

    private boolean looksLikeReference(String line) {
        String compact = line.replace('−', '-').trim();
        return REFERENCE_LINE.matcher(compact).matches();
    }

    private ReviewResultInput parseCompleteRow(String rawLine) {
        if (rawLine == null) {
            return null;
        }
        String candidate = rawLine.replace('\t', ' ')
                .replace('|', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        Matcher matcher = COMPLETE_ROW.matcher(candidate);
        if (!matcher.matches()) {
            return null;
        }

        String name = matcher.group(1).trim();
        String value = compactValue(matcher.group(2));
        String unit = cleanUnit(matcher.group(3));
        String reference = cleanReference(matcher.group(4));
        if (!containsLetter(name)
                || isHeader(name)
                || looksLikeMetadata(name)
                || unit.isEmpty()
                || unit.equals("-")
                || unit.equals("–")
                || unit.equals("—")) {
            return null;
        }
        return new ReviewResultInput(name, value, unit, reference);
    }

    private boolean containsLetter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private String compactValue(String value) {
        return value.replaceAll("\\s+", "");
    }

    private boolean looksLikeDateRemainder(String remainder) {
        if (remainder == null) {
            return false;
        }
        return remainder.trim().matches("[/.-]\\d{1,2}[/.-]\\d{2,4}(?:\\s+.*)?");
    }

    private String cleanLine(String rawLine) {
        return rawLine == null
                ? ""
                : rawLine.replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }

    private boolean isHeader(String line) {
        String lower = line.toLowerCase(new Locale("ru", "RU"));
        return lower.equals("название теста")
                || lower.equals("наименование теста")
                || lower.equals("результат")
                || lower.equals("ед.изм.")
                || lower.equals("ед. изм.")
                || lower.equals("норма")
                || (lower.contains("результат")
                && (lower.contains("ед.изм") || lower.contains("ед. изм") || lower.contains("норма")));
    }

    private boolean looksLikeFooter(String line) {
        String lower = line.toLowerCase(new Locale("ru", "RU"));
        return lower.startsWith("без письменного разрешения")
                || lower.startsWith("лабораторные результаты")
                || lower.startsWith("врач-консультант");
    }

    private boolean looksLikeMetadata(String line) {
        String lower = line.toLowerCase(new Locale("ru", "RU"))
                .replaceAll("^[|:;`'\\s]+", "");
        return looksLikeFooter(line)
                || lower.startsWith("дата выдачи")
                || lower.startsWith("дата рождения")
                || lower.startsWith("дата взятия")
                || lower.startsWith("дата забора")
                || lower.startsWith("дата получения")
                || lower.startsWith("дата поступления")
                || lower.startsWith("врач ")
                || lower.startsWith("врач:")
                || lower.contains("врач клд")
                || lower.startsWith("огрн")
                || lower.contains(" огрн")
                || lower.startsWith("стр.")
                || lower.startsWith("страница ")
                || lower.startsWith("--- страница")
                || lower.startsWith("пациент")
                || lower.startsWith("номер заказа")
                || lower.startsWith("номер образца")
                || lower.startsWith("адрес:")
                || lower.startsWith("страховая компания");
    }

    private String joinName(List<String> lines) {
        String name = String.join(" ", lines)
                .replaceAll("^[|:;`'\\s]+", "")
                .replaceAll("[|:;\\s]+$", "")
                .trim();
        return name.length() > 180 ? name.substring(0, 180).trim() : name;
    }

    private String cleanUnit(String rawUnit) {
        if (rawUnit == null) {
            return "";
        }
        String unit = rawUnit.replaceAll("^[|:;\\s]+", "")
                .replaceAll("[|:;\\s]+$", "")
                .trim();
        return unit.length() > 40 ? unit.substring(0, 40).trim() : unit;
    }

    private String cleanReference(String rawReference) {
        return rawReference.replaceAll("^[|:;\\s]+", "")
                .replaceAll("[|:;\\s]+$", "")
                .trim();
    }

    private static final class PendingResult {
        final String testName;
        final String value;
        final String unit;

        PendingResult(String testName, String value, String unit) {
            this.testName = testName;
            this.value = value;
            this.unit = unit;
        }

        ReviewResultInput withReference(String reference) {
            return new ReviewResultInput(testName, value, unit, reference);
        }
    }
}
