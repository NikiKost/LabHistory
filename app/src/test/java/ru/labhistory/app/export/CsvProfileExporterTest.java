package ru.labhistory.app.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringWriter;
import java.util.Collections;

import ru.labhistory.app.data.ProfileEntity;
import ru.labhistory.app.data.ProfileExportRow;

public class CsvProfileExporterTest {
    @Test
    public void escapesSeparatorsQuotesAndNewlines() {
        assertEquals("обычный", CsvProfileExporter.escape("обычный"));
        assertEquals("\"а;б\"", CsvProfileExporter.escape("а;б"));
        assertEquals("\"а\"\"б\"", CsvProfileExporter.escape("а\"б"));
        assertEquals("\"а\nб\"", CsvProfileExporter.escape("а\nб"));
    }

    @Test
    public void writesBomHeaderAndReferenceStatus() throws Exception {
        ProfileEntity profile = new ProfileEntity("Я", "01.01.2000", "Мужской", 0L);
        ProfileExportRow row = new ProfileExportRow();
        row.analysisDate = 1_700_000_000_000L;
        row.labName = "Lab;4U";
        row.originalFileName = "result.pdf";
        row.testName = "АЛТ";
        row.canonicalName = "АЛТ";
        row.rawValue = "55";
        row.numericValue = "55";
        row.unit = "Ед/л";
        row.referenceLow = "0";
        row.referenceHigh = "40";
        row.referenceText = "0–40";
        StringWriter output = new StringWriter();

        int exported = CsvProfileExporter.write(
                output,
                profile,
                Collections.singletonList(row)
        );

        assertEquals(1, exported);
        String csv = output.toString();
        assertTrue(csv.startsWith("\ufeffПрофиль;Дата рождения;Пол;Дата сдачи;"));
        assertTrue(csv.contains("\"Lab;4U\";АЛТ;55;Ед/л;0–40;Выше референса бланка;result.pdf"));
    }
}
