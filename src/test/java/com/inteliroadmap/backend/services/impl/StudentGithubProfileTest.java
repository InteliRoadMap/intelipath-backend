package com.inteliroadmap.backend.services.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The profile field is meant to hold a link. It used to store whatever was typed, so a
 * student who entered their handle ended up with a "GitHub profile" that pointed nowhere.
 */
class StudentGithubProfileTest {

    @Test
    void keepsAnAddressThatAlreadyHasAScheme() {
        assertEquals("https://github.com/dangp",
                StudentServiceImpl.normalizeGithubProfile("https://github.com/dangp"));
        assertEquals("http://github.com/dangp",
                StudentServiceImpl.normalizeGithubProfile("  http://github.com/dangp  "));
    }

    @Test
    void addsTheSchemeToABareDomain() {
        assertEquals("https://github.com/dangp",
                StudentServiceImpl.normalizeGithubProfile("github.com/dangp"));
        assertEquals("https://www.github.com/dangp",
                StudentServiceImpl.normalizeGithubProfile("www.github.com/dangp"));
    }

    @Test
    void resolvesAHandleAgainstGithub() {
        assertEquals("https://github.com/dangp", StudentServiceImpl.normalizeGithubProfile("dangp"));
        // People type the @ because that is how a handle is written everywhere else.
        assertEquals("https://github.com/dangp", StudentServiceImpl.normalizeGithubProfile("@dangp"));
    }

    @Test
    void clearsTheFieldRatherThanStoringNothing() {
        assertNull(StudentServiceImpl.normalizeGithubProfile(""));
        assertNull(StudentServiceImpl.normalizeGithubProfile("   "));
        assertNull(StudentServiceImpl.normalizeGithubProfile("@"));
    }
}
