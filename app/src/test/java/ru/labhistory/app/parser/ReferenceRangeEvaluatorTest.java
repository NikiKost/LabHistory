package ru.labhistory.app.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReferenceRangeEvaluatorTest {
    @Test
    public void identifiesValueBelowRange() {
        assertEquals(
                ReferenceRangeEvaluator.Status.BELOW,
                ReferenceRangeEvaluator.evaluate("3.9", "4.0", "10.0")
        );
    }

    @Test
    public void identifiesValueAboveRange() {
        assertEquals(
                ReferenceRangeEvaluator.Status.ABOVE,
                ReferenceRangeEvaluator.evaluate("10,1", "4", "10")
        );
    }

    @Test
    public void boundariesAreWithinRange() {
        assertEquals(
                ReferenceRangeEvaluator.Status.WITHIN,
                ReferenceRangeEvaluator.evaluate("4.0", "4.0", "10.0")
        );
        assertEquals(
                ReferenceRangeEvaluator.Status.WITHIN,
                ReferenceRangeEvaluator.evaluate("10.0", "4.0", "10.0")
        );
    }

    @Test
    public void missingOrInvalidRangeIsUnknown() {
        assertEquals(
                ReferenceRangeEvaluator.Status.UNKNOWN,
                ReferenceRangeEvaluator.evaluate("5", null, null)
        );
        assertEquals(
                ReferenceRangeEvaluator.Status.UNKNOWN,
                ReferenceRangeEvaluator.evaluate("not-a-number", "4", "10")
        );
    }
}
