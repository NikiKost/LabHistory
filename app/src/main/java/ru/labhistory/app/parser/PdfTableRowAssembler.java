package ru.labhistory.app.parser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import ru.labhistory.app.domain.ReviewResultInput;

public final class PdfTableRowAssembler {
    private static final float TABLE_LEFT = 0.045f;
    private static final float NAME_END = 0.315f;
    private static final float VALUE_END = 0.404f;
    private static final float INTERPRETATION_END = 0.490f;
    private static final float UNIT_END = 0.578f;
    private static final float REFERENCE_END = 0.666f;
    private static final float TABLE_RIGHT = 0.935f;
    private static final float TABLE_BOTTOM = 0.835f;
    private static final Pattern VALUE_START = Pattern.compile(
            "^[<>≤≥]?\\s*[-+]?\\d+(?:[.,]\\d+)?(?:\\s.*)?$"
    );

    public List<ReviewResultInput> assemble(List<Glyph> sourceGlyphs) {
        List<Glyph> glyphs = new ArrayList<>();
        for (Glyph glyph : sourceGlyphs) {
            if (glyph == null || glyph.text == null || glyph.text.isEmpty()) {
                continue;
            }
            float relativeX = glyph.x / glyph.pageWidth;
            float relativeY = glyph.y / glyph.pageHeight;
            if (relativeX >= TABLE_LEFT
                    && relativeX <= TABLE_RIGHT
                    && relativeY <= TABLE_BOTTOM) {
                glyphs.add(glyph);
            }
        }
        glyphs.sort(Comparator
                .comparingInt((Glyph glyph) -> glyph.page)
                .thenComparingDouble(glyph -> glyph.y)
                .thenComparingDouble(glyph -> glyph.x));

        List<Line> lines = groupIntoLines(glyphs);
        List<ReviewResultInput> results = new ArrayList<>();
        Draft current = null;
        boolean tableStarted = false;
        for (Line line : lines) {
            String name = line.cellText(0);
            String value = line.cellText(1);
            String unit = line.cellText(3);
            String reference = line.cellText(4);
            String normalizedName = name.toLowerCase(new Locale("ru", "RU"));

            if (normalizedName.contains("показатель")) {
                tableStarted = true;
                continue;
            }
            if (!tableStarted) {
                continue;
            }
            if (isEndMarker(normalizedName)) {
                if (current != null) {
                    addIfValid(results, current);
                    current = null;
                }
                continue;
            }
            if (line.bold && !name.isEmpty() && value.isEmpty() && unit.isEmpty() && reference.isEmpty()) {
                if (current != null) {
                    addIfValid(results, current);
                    current = null;
                }
                continue;
            }

            if (looksLikeValue(value) && containsLetter(name)) {
                if (current != null) {
                    addIfValid(results, current);
                }
                current = new Draft(name, value, unit, reference);
                continue;
            }

            if (current != null) {
                current.name = appendText(current.name, name);
                current.value = appendText(current.value, value);
                current.unit = appendText(current.unit, unit);
                current.reference = appendText(current.reference, reference);
            }
        }
        if (current != null) {
            addIfValid(results, current);
        }
        return results;
    }

    private List<Line> groupIntoLines(List<Glyph> glyphs) {
        List<Line> lines = new ArrayList<>();
        for (Glyph glyph : glyphs) {
            Line best = null;
            float bestDistance = Float.MAX_VALUE;
            for (int index = lines.size() - 1; index >= 0; index--) {
                Line candidate = lines.get(index);
                if (candidate.page != glyph.page) {
                    break;
                }
                float distance = Math.abs(candidate.y - glyph.y);
                if (distance <= Math.max(2.2f, glyph.height * 0.35f) && distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
                if (candidate.y < glyph.y - 5f) {
                    break;
                }
            }
            if (best == null) {
                best = new Line(glyph.page, glyph.y, glyph.pageWidth);
                lines.add(best);
            }
            best.add(glyph);
        }
        lines.sort(Comparator
                .comparingInt((Line line) -> line.page)
                .thenComparingDouble(line -> line.y));
        return lines;
    }

    private boolean looksLikeValue(String value) {
        if (value == null) {
            return false;
        }
        String clean = value.trim();
        return VALUE_START.matcher(clean).matches()
                || clean.equalsIgnoreCase("положительный")
                || clean.equalsIgnoreCase("отрицательный");
    }

    private boolean containsLetter(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEndMarker(String normalizedName) {
        return normalizedName.startsWith("заключение")
                || normalizedName.startsWith("оказанные услуги")
                || normalizedName.startsWith("медицинские работники")
                || normalizedName.startsWith("дата составления")
                || normalizedName.startsWith("pdf-представление")
                || normalizedName.startsWith("оригинальный электронный документ")
                || normalizedName.startsWith("протокол лабораторного исследования");
    }

    private void addIfValid(List<ReviewResultInput> results, Draft draft) {
        String name = clean(draft.name);
        String value = clean(draft.value);
        if (!containsLetter(name) || value.isEmpty() || name.length() > 260) {
            return;
        }
        results.add(new ReviewResultInput(
                name,
                value,
                clean(draft.unit),
                clean(draft.reference)
        ));
    }

    private String appendText(String existing, String addition) {
        String right = clean(addition);
        if (right.isEmpty()) {
            return clean(existing);
        }
        String left = clean(existing);
        if (left.isEmpty()) {
            return right;
        }
        char last = left.charAt(left.length() - 1);
        if (last == '-' || last == '/' || last == '^' || right.startsWith(")")) {
            return left + right;
        }
        return left + " " + right;
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static final class Draft {
        String name;
        String value;
        String unit;
        String reference;

        Draft(String name, String value, String unit, String reference) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.reference = reference;
        }
    }

    private static final class Line {
        final int page;
        final float y;
        final float pageWidth;
        final List<Glyph>[] cells;
        boolean bold;

        @SuppressWarnings("unchecked")
        Line(int page, float y, float pageWidth) {
            this.page = page;
            this.y = y;
            this.pageWidth = pageWidth;
            cells = new List[6];
            for (int index = 0; index < cells.length; index++) {
                cells[index] = new ArrayList<>();
            }
        }

        void add(Glyph glyph) {
            int column = columnFor(glyph.x / pageWidth);
            if (column >= 0) {
                cells[column].add(glyph);
            }
            bold |= glyph.bold;
        }

        String cellText(int column) {
            List<Glyph> content = cells[column];
            content.sort(Comparator.comparingDouble(glyph -> glyph.x));
            StringBuilder text = new StringBuilder();
            float previousEnd = Float.NaN;
            float averageHeight = 0f;
            for (Glyph glyph : content) {
                averageHeight = averageHeight == 0f
                        ? glyph.height
                        : (averageHeight + glyph.height) / 2f;
                if (!Float.isNaN(previousEnd)
                        && glyph.x - previousEnd > Math.max(1.2f, averageHeight * 0.14f)
                        && text.length() > 0
                        && !Character.isWhitespace(text.charAt(text.length() - 1))) {
                    text.append(' ');
                }
                text.append(glyph.text);
                previousEnd = Float.isNaN(previousEnd)
                        ? glyph.x + glyph.width
                        : Math.max(previousEnd, glyph.x + glyph.width);
            }
            return text.toString().replaceAll("\\s+", " ").trim();
        }

        private int columnFor(float relativeX) {
            if (relativeX < TABLE_LEFT || relativeX > TABLE_RIGHT) {
                return -1;
            }
            if (relativeX < NAME_END) {
                return 0;
            }
            if (relativeX < VALUE_END) {
                return 1;
            }
            if (relativeX < INTERPRETATION_END) {
                return 2;
            }
            if (relativeX < UNIT_END) {
                return 3;
            }
            if (relativeX < REFERENCE_END) {
                return 4;
            }
            return 5;
        }
    }

    public static final class Glyph {
        public final int page;
        public final float pageWidth;
        public final float pageHeight;
        public final float x;
        public final float y;
        public final float width;
        public final float height;
        public final String text;
        public final boolean bold;

        public Glyph(
                int page,
                float pageWidth,
                float pageHeight,
                float x,
                float y,
                float width,
                float height,
                String text,
                boolean bold
        ) {
            this.page = page;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
            this.bold = bold;
        }
    }
}
