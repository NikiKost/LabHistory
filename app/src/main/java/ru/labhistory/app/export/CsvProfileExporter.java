package ru.labhistory.app.export;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.data.ProfileEntity;
import ru.labhistory.app.data.ProfileExportRow;
import ru.labhistory.app.parser.ReferenceRangeEvaluator;

public final class CsvProfileExporter {
    private static final String[] HEADERS = {
            "Профиль",
            "Дата рождения",
            "Пол",
            "Дата сдачи",
            "Лаборатория",
            "Показатель",
            "Значение",
            "Единица",
            "Референс",
            "Статус",
            "Документ"
    };

    private CsvProfileExporter() {
    }

    public static int write(
            Writer writer,
            ProfileEntity profile,
            List<ProfileExportRow> rows
    ) throws IOException {
        writer.write('\ufeff');
        writeRow(writer, HEADERS);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.ROOT);
        for (ProfileExportRow row : rows) {
            String name = firstNotEmpty(row.canonicalName, row.testName);
            String value = firstNotEmpty(row.rawValue, row.numericValue);
            String reference = buildReference(row);
            String status = statusText(ReferenceRangeEvaluator.evaluate(
                    row.numericValue,
                    row.referenceLow,
                    row.referenceHigh
            ));
            writeRow(writer, new String[]{
                    profile.name,
                    profile.birthDate,
                    profile.sex,
                    dateFormat.format(new Date(row.analysisDate)),
                    row.labName,
                    name,
                    value,
                    row.unit,
                    reference,
                    status,
                    row.originalFileName
            });
        }
        writer.flush();
        return rows.size();
    }

    static String escape(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(';') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private static void writeRow(Writer writer, String[] cells) throws IOException {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                writer.write(';');
            }
            writer.write(escape(cells[index]));
        }
        writer.write("\r\n");
    }

    private static String buildReference(ProfileExportRow row) {
        if (row.referenceText != null && !row.referenceText.trim().isEmpty()) {
            return row.referenceText.trim();
        }
        if (row.referenceLow != null && row.referenceHigh != null) {
            return row.referenceLow + "–" + row.referenceHigh;
        }
        if (row.referenceLow != null) {
            return "от " + row.referenceLow;
        }
        if (row.referenceHigh != null) {
            return "до " + row.referenceHigh;
        }
        return "";
    }

    private static String statusText(ReferenceRangeEvaluator.Status status) {
        switch (status) {
            case BELOW:
                return "Ниже референса бланка";
            case ABOVE:
                return "Выше референса бланка";
            case WITHIN:
                return "В пределах референса бланка";
            default:
                return "Не определён";
        }
    }

    private static String firstNotEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        return second == null ? "" : second;
    }
}
