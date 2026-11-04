package ru.labhistory.app.parser;

public class ParsedResult {
    public final String testName;
    public final String rawValue;
    public final String unit;
    public final String referenceText;
    public final double confidence;

    public ParsedResult(
            String testName,
            String rawValue,
            String unit,
            String referenceText,
            double confidence
    ) {
        this.testName = testName;
        this.rawValue = rawValue;
        this.unit = unit;
        this.referenceText = referenceText;
        this.confidence = confidence;
    }
}
