package ru.labhistory.app.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ru.labhistory.app.data.IndicatorSummary;

public class IndicatorSummarySorterTest {
    @Test
    public void sortsByNameIgnoringCase() {
        List<IndicatorSummary> indicators = list(
                summary("билирубин", 20L, false),
                summary("АЛТ", 10L, false)
        );

        IndicatorSummarySorter.sort(indicators, IndicatorSummarySorter.Order.NAME);

        assertEquals("АЛТ", indicators.get(0).testName);
        assertEquals("билирубин", indicators.get(1).testName);
    }

    @Test
    public void sortsNewestMeasurementFirst() {
        List<IndicatorSummary> indicators = list(
                summary("АЛТ", 10L, false),
                summary("АСТ", 20L, false)
        );

        IndicatorSummarySorter.sort(indicators, IndicatorSummarySorter.Order.LATEST_DATE);

        assertEquals("АСТ", indicators.get(0).testName);
    }

    @Test
    public void putsOutsideReferenceBeforeOtherIndicators() {
        List<IndicatorSummary> indicators = list(
                summary("В норме", 30L, false),
                summary("Выше", 10L, true)
        );

        IndicatorSummarySorter.sort(indicators, IndicatorSummarySorter.Order.OUTSIDE_FIRST);

        assertEquals("Выше", indicators.get(0).testName);
    }

    private List<IndicatorSummary> list(IndicatorSummary... indicators) {
        return new ArrayList<>(Arrays.asList(indicators));
    }

    private IndicatorSummary summary(String name, long date, boolean outside) {
        IndicatorSummary summary = new IndicatorSummary();
        summary.testName = name;
        summary.latestDate = date;
        summary.latestNumericValue = outside ? "11" : "5";
        summary.latestReferenceLow = "1";
        summary.latestReferenceHigh = "10";
        return summary;
    }
}
