package ru.labhistory.app.parser;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ru.labhistory.app.domain.ReviewResultInput;

public class PdfTableRowAssemblerTest {
    private static final float PAGE_WIDTH = 600f;
    private static final float PAGE_HEIGHT = 840f;

    @Test
    public void assemble_keepsTableColumnsAndJoinsWrappedNames() {
        List<PdfTableRowAssembler.Glyph> glyphs = new ArrayList<>();
        glyphs.add(glyph(1, 35, 60, "Показатель", true));
        glyphs.add(glyph(1, 195, 60, "Значение", true));
        glyphs.add(glyph(1, 35, 80, "Клинический анализ", true));

        glyphs.add(glyph(1, 35, 100, "Тест первый, количество", false));
        glyphs.add(glyph(1, 195, 100, "174", false));
        glyphs.add(glyph(1, 250, 100, "Повышенный", false));
        glyphs.add(glyph(1, 300, 100, "г/л", false));
        glyphs.add(glyph(1, 350, 100, "130-160", false));
        glyphs.add(glyph(1, 35, 114, "в крови", false));

        glyphs.add(glyph(2, 35, 40, "Тест второй", false));
        glyphs.add(glyph(2, 195, 40, "2.85", false));
        glyphs.add(glyph(2, 300, 40, "10^9/л", false));
        glyphs.add(glyph(2, 350, 40, "0.8-3.6", false));
        glyphs.add(glyph(2, 35, 80, "Заключение", false));

        List<ReviewResultInput> results = new PdfTableRowAssembler().assemble(glyphs);

        assertEquals(2, results.size());
        assertEquals("Тест первый, количество в крови", results.get(0).testName);
        assertEquals("174", results.get(0).value);
        assertEquals("г/л", results.get(0).unit);
        assertEquals("130-160", results.get(0).referenceText);
        assertEquals("Тест второй", results.get(1).testName);
        assertEquals("2.85", results.get(1).value);
    }

    private PdfTableRowAssembler.Glyph glyph(
            int page,
            float x,
            float y,
            String text,
            boolean bold
    ) {
        return new PdfTableRowAssembler.Glyph(
                page,
                PAGE_WIDTH,
                PAGE_HEIGHT,
                x,
                y,
                Math.max(5f, text.length() * 5f),
                10f,
                text,
                bold
        );
    }
}
