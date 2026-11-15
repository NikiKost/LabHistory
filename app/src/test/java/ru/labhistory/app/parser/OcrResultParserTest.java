package ru.labhistory.app.parser;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import ru.labhistory.app.domain.ReviewResultInput;

public class OcrResultParserTest {
    @Test
    public void parse_skipsPersonalHeaderAndBuildsEditableRows() {
        String text = "Пациент: Тестовый Пользователь\n"
                + "Номер заказа: 000000\n"
                + "Название теста\n"
                + "Результат Ед.изм. Норма\n"
                + "Аналит A расширенный\n"
                + "в плазме\n"
                + "5,4 ммоль/л\n"
                + "4,0 - 6,0\n"
                + "Аналит B\n"
                + "12 ед/л\n"
                + "10–20\n";

        List<ReviewResultInput> results = new OcrResultParser().parse(text);

        assertEquals(2, results.size());
        assertEquals("Аналит A расширенный в плазме", results.get(0).testName);
        assertEquals("5,4", results.get(0).value);
        assertEquals("ммоль/л", results.get(0).unit);
        assertEquals("4,0 - 6,0", results.get(0).referenceText);
        assertEquals("Аналит B", results.get(1).testName);
        assertEquals("12", results.get(1).value);
        assertEquals("ед/л", results.get(1).unit);
        assertEquals("10–20", results.get(1).referenceText);
    }

    @Test
    public void parse_emptyTextReturnsNoRows() {
        assertEquals(0, new OcrResultParser().parse("  ").size());
    }

    @Test
    public void parse_readsLab4uStyleRowsAndRejectsFooterDate() {
        String text = "Служебные данные лаборатории\n"
                + "Наименование теста  Результат  Единицы измерения  Референсные значения\n"
                + "Гемоглобин 150 г/л 120-170\n"
                + "Эозинофилы 4.00 % 0.80-6.20\n"
                + "Нейтрофилы 5.18 10^9/л 1.90-8.60\n"
                + "СОЭ (Westergren) 1 мм/час 1-15\n"
                + "Тест со строки выше\n"
                + "5,2 ммоль/л 4,0-6,0\n"
                + "Тестовый В.П., Врач КЛД ОГРН 0000000000000\n"
                + "Дата выдачи: 22/09/2026\n";

        List<ReviewResultInput> results = new OcrResultParser().parse(text);

        assertEquals(5, results.size());
        assertEquals("Гемоглобин", results.get(0).testName);
        assertEquals("150", results.get(0).value);
        assertEquals("г/л", results.get(0).unit);
        assertEquals("120-170", results.get(0).referenceText);
        assertEquals("Нейтрофилы", results.get(2).testName);
        assertEquals("10^9/л", results.get(2).unit);
        assertEquals("СОЭ (Westergren)", results.get(3).testName);
        assertEquals("Тест со строки выше", results.get(4).testName);
        assertEquals("5,2", results.get(4).value);
    }

    @Test
    public void parse_doesNotTreatSplitDateAsResult() {
        String text = "Наименование теста\n"
                + "Результат Единицы измерения Референсные значения\n"
                + "Тестовый В.П., Врач КЛД ОГРН\n"
                + "22\n"
                + "/09/2026\n";

        assertEquals(0, new OcrResultParser().parse(text).size());
    }

    @Test
    public void parse_readsStructuredPdfBlocksWithoutMovingFields() {
        String text = "=== Показатели из текстового слоя PDF ===\n\n"
                + "Название: Тестовый показатель\n"
                + "Значение: 12.5\n"
                + "Единица: ед/л\n"
                + "Референс: 10-20\n\n"
                + "Название: Качественный тест\n"
                + "Значение: 0.10 Отрицательный\n"
                + "Единица: \n"
                + "Референс: \n";

        List<ReviewResultInput> results = new OcrResultParser().parse(text);

        assertEquals(2, results.size());
        assertEquals("Тестовый показатель", results.get(0).testName);
        assertEquals("12.5", results.get(0).value);
        assertEquals("ед/л", results.get(0).unit);
        assertEquals("10-20", results.get(0).referenceText);
        assertEquals("0.10 Отрицательный", results.get(1).value);
    }
}
