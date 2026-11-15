package ru.labhistory.app.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ru.labhistory.app.data.AnalysisDocumentEntity;

public class DocumentSearchTest {
    private final AnalysisDocumentEntity document = new AnalysisDocumentEntity(
            1L,
            "Lab4U",
            0L,
            "DOCUMENT",
            "PDF",
            "Общий анализ крови.pdf",
            null,
            null,
            "Показатели проверены пользователем",
            null,
            0L
    );

    @Test
    public void matchesLabFileTypeAndStatusIgnoringCase() {
        assertTrue(DocumentSearch.matches(document, "lab4u"));
        assertTrue(DocumentSearch.matches(document, "КРОВИ"));
        assertTrue(DocumentSearch.matches(document, "pdf"));
        assertTrue(DocumentSearch.matches(document, "проверены"));
    }

    @Test
    public void emptyQueryShowsAllAndUnknownTextDoesNotMatch() {
        assertTrue(DocumentSearch.matches(document, "  "));
        assertFalse(DocumentSearch.matches(document, "инвитро"));
    }
}
