package ru.labhistory.app.domain;

public class ReviewResultInput {
    public final String testName;
    public final String value;
    public final String unit;
    public final String referenceText;

    public ReviewResultInput(
            String testName,
            String value,
            String unit,
            String referenceText
    ) {
        this.testName = testName;
        this.value = value;
        this.unit = unit;
        this.referenceText = referenceText;
    }
}
