package ru.labhistory.app.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UnitNormalizerTest {
    @Test
    public void normalizesEquivalentSpellings() {
        assertEquals("Ед/л", UnitNormalizer.normalize("U/L"));
        assertEquals("Ед/л", UnitNormalizer.normalize("ед/л"));
        assertEquals("Ед/л", UnitNormalizer.normalize("ЕдД/Л"));
        assertEquals("Ед/л", UnitNormalizer.normalize("Повыш Ед/л"));
        assertEquals("Ед/л", UnitNormalizer.normalize("Повыш ЕдД/л"));
        assertEquals("Ед/л", UnitNormalizer.normalize("Повыше Ед/л"));
        assertEquals("Ед/л", UnitNormalizer.normalize("Повышено: U/L"));
        assertEquals("Ед/л", UnitNormalizer.normalize("55 ЕдД/Л"));
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
