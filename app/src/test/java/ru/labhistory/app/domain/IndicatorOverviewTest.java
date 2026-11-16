package ru.labhistory.app.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

import ru.labhistory.app.data.IndicatorSummary;

public class IndicatorOverviewTest {
    @Test
    public void countsOutsideAndUnknownLatestReferences() {
        IndicatorOverview overview = IndicatorOverview.from(Arrays.asList(
                summary("5", "4", "6"),
                summary("7", "4", "6"),
                summary("3", "4", "6"),
                summary("5", null, null)
        ));

        assertEquals(4, overview.totalCount);
        assertEquals(2, overview.outsideReferenceCount);
        assertEquals(1, overview.unknownReferenceCount);
    }

    @Test
    public void handlesMissingList() {
        IndicatorOverview overview = IndicatorOverview.from(null);

        assertEquals(0, overview.totalCount);
        assertEquals(0, overview.outsideReferenceCount);
        assertEquals(0, overview.unknownReferenceCount);
    }

    private IndicatorSummary summary(String value, String low, String high) {
        IndicatorSummary summary = new IndicatorSummary();
        summary.latestNumericValue = value;
        summary.latestReferenceLow = low;
        summary.latestReferenceHigh = high;
        return summary;
    }
}
