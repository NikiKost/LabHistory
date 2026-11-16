package ru.labhistory.app.domain;

import java.util.List;

import ru.labhistory.app.data.IndicatorSummary;
import ru.labhistory.app.parser.ReferenceRangeEvaluator;

public final class IndicatorOverview {
    public final int totalCount;
    public final int outsideReferenceCount;
    public final int unknownReferenceCount;

    private IndicatorOverview(
            int totalCount,
            int outsideReferenceCount,
            int unknownReferenceCount
    ) {
        this.totalCount = totalCount;
        this.outsideReferenceCount = outsideReferenceCount;
        this.unknownReferenceCount = unknownReferenceCount;
    }

    public static IndicatorOverview from(List<IndicatorSummary> indicators) {
        int total = 0;
        int outside = 0;
        int unknown = 0;
        if (indicators != null) {
            for (IndicatorSummary indicator : indicators) {
                if (indicator == null) {
                    continue;
                }
                total++;
                ReferenceRangeEvaluator.Status status = indicator.getLatestReferenceStatus();
                if (status == ReferenceRangeEvaluator.Status.BELOW
                        || status == ReferenceRangeEvaluator.Status.ABOVE) {
                    outside++;
                } else if (status == ReferenceRangeEvaluator.Status.UNKNOWN) {
                    unknown++;
                }
            }
        }
        return new IndicatorOverview(total, outside, unknown);
    }
}
