package ru.labhistory.app.parser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ResultValueParserTest {
    @Test
    public void normalizeNumber_acceptsExactNumericValueOnly() {
        assertEquals("12.5", ResultValueParser.normalizeNumber(" 12,5 "));
        assertEquals("-2", ResultValueParser.normalizeNumber("-2"));
        assertNull(ResultValueParser.normalizeNumber("< 5"));
        assertNull(ResultValueParser.normalizeNumber("12 ед/л"));
    }

    @Test
    public void parseReferenceRange_supportsCommonSeparators() {
        assertArrayEquals(
                new String[]{"4.0", "10.0"},
                ResultValueParser.parseReferenceRange("4,0 — 10,0")
        );
        assertArrayEquals(
                new String[]{null, "10"},
                ResultValueParser.parseReferenceRange("до 10")
        );
        assertArrayEquals(
                new String[]{"4.5", null},
                ResultValueParser.parseReferenceRange("≥ 4,5")
        );
        assertArrayEquals(
                new String[]{null, "7.2"},
                ResultValueParser.parseReferenceRange("<7,2")
        );
    }
}
