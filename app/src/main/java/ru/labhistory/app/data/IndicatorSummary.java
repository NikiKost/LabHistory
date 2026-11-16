package ru.labhistory.app.data;

import ru.labhistory.app.parser.ReferenceRangeEvaluator;

public class IndicatorSummary {
    public String canonicalKey;
    public String testName;
    public String unit;
    public int measurementCount;
    public long latestDate;
    public String latestNumericValue;
    public String latestReferenceLow;
    public String latestReferenceHigh;

    public ReferenceRangeEvaluator.Status getLatestReferenceStatus() {
        return ReferenceRangeEvaluator.evaluate(
                latestNumericValue,
                latestReferenceLow,
                latestReferenceHigh
        );
    }

    public boolean isLatestOutsideReference() {
        ReferenceRangeEvaluator.Status status = getLatestReferenceStatus();
        return status == ReferenceRangeEvaluator.Status.BELOW
                || status == ReferenceRangeEvaluator.Status.ABOVE;
    }
}
