package ru.labhistory.app.importer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LabFormatDetectorTest {
    @Test
    public void detectsInvitroInRussianText() {
        assertEquals("ИНВИТРО", LabFormatDetector.detect("Результаты исследований ИНВИТРО"));
    }

    @Test
    public void detectsLab4uIgnoringCase() {
        assertEquals("Lab4U", LabFormatDetector.detect("lab4u result.pdf"));
    }

    @Test
    public void returnsUnknownForGenericDocument() {
        assertEquals("Не определена", LabFormatDetector.detect("results.pdf"));
    }
}
