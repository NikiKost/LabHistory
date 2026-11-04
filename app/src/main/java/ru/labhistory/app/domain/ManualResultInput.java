package ru.labhistory.app.domain;

public class ManualResultInput {
    public final String labName;
    public final String testName;
    public final String value;
    public final String unit;
    public final String referenceLow;
    public final String referenceHigh;

    public ManualResultInput(
            String labName,
            String testName,
            String value,
            String unit,
            String referenceLow,
            String referenceHigh
    ) {
        this.labName = labName;
        this.testName = testName;
        this.value = value;
        this.unit = unit;
        this.referenceLow = referenceLow;
        this.referenceHigh = referenceHigh;
    }
}
