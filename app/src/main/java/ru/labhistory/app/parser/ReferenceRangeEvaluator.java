package ru.labhistory.app.parser;

public final class ReferenceRangeEvaluator {
    public enum Status {
        BELOW,
        ABOVE,
        WITHIN,
        UNKNOWN
    }

    private ReferenceRangeEvaluator() {
    }

    public static Status evaluate(String numericValue, String referenceLow, String referenceHigh) {
        Double value = parseFinite(numericValue);
        Double low = parseFinite(referenceLow);
        Double high = parseFinite(referenceHigh);
        if (value == null || (low == null && high == null)) {
            return Status.UNKNOWN;
        }
        if (low != null && value < low) {
            return Status.BELOW;
        }
        if (high != null && value > high) {
            return Status.ABOVE;
        }
        return Status.WITHIN;
    }

    private static Double parseFinite(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim().replace(',', '.'));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
