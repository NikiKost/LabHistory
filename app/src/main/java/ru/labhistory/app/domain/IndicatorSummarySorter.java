package ru.labhistory.app.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import ru.labhistory.app.data.IndicatorSummary;

public final class IndicatorSummarySorter {
    public enum Order {
        NAME,
        LATEST_DATE,
        OUTSIDE_FIRST
    }

    private IndicatorSummarySorter() {
    }

    public static void sort(List<IndicatorSummary> indicators, Order order) {
        if (indicators == null || indicators.size() < 2) {
            return;
        }
        Comparator<IndicatorSummary> byName = (left, right) -> normalize(left.testName)
                .compareTo(normalize(right.testName));
        Comparator<IndicatorSummary> byLatestDate = Comparator
                .comparingLong((IndicatorSummary indicator) -> indicator.latestDate)
                .reversed()
                .thenComparing(byName);

        Order safeOrder = order == null ? Order.NAME : order;
        if (safeOrder == Order.LATEST_DATE) {
            indicators.sort(byLatestDate);
        } else if (safeOrder == Order.OUTSIDE_FIRST) {
            indicators.sort(Comparator
                    .comparing((IndicatorSummary indicator) -> !indicator.isLatestOutsideReference())
                    .thenComparing(byLatestDate));
        } else {
            indicators.sort(byName);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.forLanguageTag("ru-RU"));
    }
}
