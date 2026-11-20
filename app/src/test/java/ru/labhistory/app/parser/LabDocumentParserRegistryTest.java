package ru.labhistory.app.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LabDocumentParserRegistryTest {
    private final LabDocumentParserRegistry registry = new LabDocumentParserRegistry();

    @Test
    public void lab4uHintSelectsParserAndBuildsRows() {
        ParsedAnalysis analysis = registry.parse(
                "Lab4U",
                "Наименование исследования Результат Единицы измерения Референсные значения\n"
                        + "Гемоглобин 1 176 г/л 135-175\n"
                        + "Эритроциты Tt 5.87 10^12/л 4.32-5.72\n"
                        + "Т4 свободный { 5.48 пмоль/л 7.70-14.20\n"
                        + "Эозинофилы 4.00 $ 0.80-6.20\n"
                        + "СОЭ (Westergren) 1 мм/час 1-15\n"
        );

        assertEquals("Lab4U", analysis.labName);
        assertEquals(5, analysis.results.size());
        assertEquals("Гемоглобин", analysis.results.get(0).testName);
        assertEquals("176", analysis.results.get(0).rawValue);
        assertEquals("г/л", analysis.results.get(0).unit);
        assertEquals("Эритроциты", analysis.results.get(1).testName);
        assertEquals("5.87", analysis.results.get(1).rawValue);
        assertEquals("Т4 свободный", analysis.results.get(2).testName);
        assertEquals("5.48", analysis.results.get(2).rawValue);
        assertEquals("%", analysis.results.get(3).unit);
        assertEquals("1", analysis.results.get(4).rawValue);
        assertEquals("мм/час", analysis.results.get(4).unit);
    }

    @Test
    public void invitroTextSelectsParserAndRecognizesAlternativeHeader() {
        ParsedAnalysis analysis = registry.parse(
                "Не определена",
                "ИНВИТРО\n"
                        + "Исследование Результат Единицы Референсные значения Комментарий\n"
                        + "АЛТ 18 Ед/л 0-41\n"
        );

        assertEquals("ИНВИТРО", analysis.labName);
        assertEquals(1, analysis.results.size());
        assertEquals("АЛТ", analysis.results.get(0).testName);
    }

    @Test
    public void unknownLabUsesSafeGenericParser() {
        ParsedAnalysis analysis = registry.parse(
                "Не определена",
                "Название теста Результат Ед.изм. Норма\n"
                        + "Тестовый показатель 5 ммоль/л 3-7\n"
        );

        assertEquals("Не определена", analysis.labName);
        assertEquals(1, analysis.results.size());
    }
}
