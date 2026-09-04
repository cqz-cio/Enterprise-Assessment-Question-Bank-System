package com.yf.modules.exam.assignment.service;

import com.yf.base.api.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccessCodeManagerTest {

    private final AccessCodeManager manager =
            new AccessCodeManager("test-pepper-must-be-at-least-32-characters-long");

    @Test
    void generatedCodesMeetTheContractAndCanBeVerified() {
        String code = manager.generate();
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(ch -> AccessCodeManager.ALPHABET.indexOf(ch) >= 0));

        String encoded = manager.hash(code);
        assertNotEquals(code, encoded);
        assertTrue(manager.matches(code, encoded));
        assertFalse(manager.matches(manager.generate(), encoded));
    }

    @Test
    void lookupIsDeterministicButHashUsesRandomSalt() {
        String code = "AB2345";
        assertEquals(manager.lookup(code), manager.lookup(code));
        assertNotEquals(manager.hash(code), manager.hash(code));
    }

    @Test
    void normalizationRejectsAmbiguousOrMalformedCodes() {
        assertEquals("AB2345", manager.normalize(" ab2345 "));
        assertThrows(ServiceException.class, () -> manager.normalize("AB01IO"));
        assertThrows(ServiceException.class, () -> manager.normalize("ABC"));
    }
}
