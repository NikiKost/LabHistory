package ru.labhistory.app.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TestNameNormalizerTest {
    @Test
    public void altAbbreviationAndFullNameShareKey() {
        String shortKey = TestNameNormalizer.normalize("АЛТ").key;
        String latinKey = TestNameNormalizer.normalize("ALT").key;
        String fullKey = TestNameNormalizer.normalize(
                "Аланинаминотрансфераза, каталитическая концентрация в сыворотке крови"
        ).key;

        assertEquals(shortKey, latinKey);
        assertEquals(shortKey, fullKey);
        assertEquals("alt", shortKey);
    }

    @Test
    public void verboseAndSimpleLeukocyteNamesShareKey() {
        assertEquals(
                TestNameNormalizer.normalize("Лейкоциты").key,
                TestNameNormalizer.normalize(
                        "Лейкоциты, количество в крови методом автоматизированного подсчёта"
                ).key
        );
    }

    @Test
    public void hemoglobinAndErythrocyteIndexStaySeparate() {
        assertNotEquals(
                TestNameNormalizer.normalize("Гемоглобин общий").key,
                TestNameNormalizer.normalize(
                        "Среднее содержание гемоглобина в эритроците"
                ).key
        );
    }

    @Test
    public void cyrillicAndOcrAstAliasesShareKey() {
        assertEquals(
                TestNameNormalizer.normalize("АСТ").key,
                TestNameNormalizer.normalize("ACT").key
        );
        assertEquals(
                TestNameNormalizer.normalize("АСТ").key,
                TestNameNormalizer.normalize("Аспартатаминотрансфераза").key
        );
    }
}
