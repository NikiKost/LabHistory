package ru.labhistory.app.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProfileValidatorTest {
    @Test
    public void acceptsEmptyAndRealCalendarDate() {
        assertTrue(ProfileValidator.isValidBirthDate(""));
        assertTrue(ProfileValidator.isValidBirthDate("29.02.2000"));
    }

    @Test
    public void rejectsInvalidCalendarDateAndWrongFormat() {
        assertFalse(ProfileValidator.isValidBirthDate("31.02.2020"));
        assertFalse(ProfileValidator.isValidBirthDate("2020-02-29"));
    }

    @Test
    public void rejectsFutureDate() {
        assertFalse(ProfileValidator.isValidBirthDate("31.12.2999"));
    }
}
