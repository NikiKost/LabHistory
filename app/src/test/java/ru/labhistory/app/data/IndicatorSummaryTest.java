package ru.labhistory.app.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IndicatorSummaryTest {
    @Test
    public void detectsLatestValueBelowReference() {
        IndicatorSummary summary = summary("3.9", "4.0", "10.0");

        assertTrue(summary.isLatestOutsideReference());
    }

    @Test
    public void detectsLatestValueAboveReference() {
        IndicatorSummary summary = summary("10.1", "4.0", "10.0");

        assertTrue(summary.isLatestOutsideReference());
    }

    @Test
    public void keepsValueInsideReferenceOutOfOutsideFilter() {
        IndicatorSummary summary = summary("7.0", "4.0", "10.0");

        assertFalse(summary.isLatestOutsideReference());
    }

    @Test
    public void keepsUnknownReferenceOutOfOutsideFilter() {
        IndicatorSummary summary = summary("7.0", null, null);

        assertFalse(summary.isLatestOutsideReference());
    }

    private IndicatorSummary summary(String value, String low, String high) {
        IndicatorSummary summary = new IndicatorSummary();
        summary.latestNumericValue = value;
        summary.latestReferenceLow = low;
        summary.latestReferenceHigh = high;
        return summary;
    }
}
