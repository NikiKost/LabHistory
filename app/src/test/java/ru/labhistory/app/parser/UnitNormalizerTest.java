package ru.labhistory.app.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UnitNormalizerTest {
    @Test
    public void normalizesEquivalentSpellings() {
        assertEquals("Ед/л", UnitNormalizer.normalize("U/L"));
        assertEquals("Ед/л", UnitNormalizer.normalize("ед/л"));
        assertEquals("10^9/л", UnitNormalizer.normalize("10⁹/L"));
        assertEquals("10^9/л", UnitNormalizer.normalize("10*9/л"));
    }

    @Test
    public void doesNotMergeDifferentScales() {
        assertNotEquals(
                UnitNormalizer.normalize("10^9/л"),
                UnitNormalizer.normalize("10^12/л")
        );
    }
}
