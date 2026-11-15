package ru.labhistory.app.parser;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AnalysisDateExtractorTest {
    @Test
    public void extractsMaterialCollectionDate() {
        Long result = AnalysisDateExtractor.extract(
                "Дата рождения: 01.02.1990\nДата приема взятия материала: 22/09/2026 13:00"
        );

        assertDate(result, 2026, Calendar.SEPTEMBER, 22);
    }

    @Test
    public void collectionDateHasPriorityOverIssueDate() {
        Long result = AnalysisDateExtractor.extract(
                "Дата выдачи: 24.09.2026\nДата забора биоматериала: 22.09.2026"
        );

        assertDate(result, 2026, Calendar.SEPTEMBER, 22);
    }

    @Test
    public void usesIssueDateOnlyAsFallback() {
        Long result = AnalysisDateExtractor.extract("Дата составления:\n06.11.2026");

        assertDate(result, 2026, Calendar.NOVEMBER, 6);
    }

    @Test
    public void ignoresBirthDateWithoutAnalysisLabel() {
        assertNull(AnalysisDateExtractor.extract("Дата рождения: 01.02.1990"));
    }

    private void assertDate(Long millis, int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        assertEquals(year, calendar.get(Calendar.YEAR));
        assertEquals(month, calendar.get(Calendar.MONTH));
        assertEquals(day, calendar.get(Calendar.DAY_OF_MONTH));
    }
}
