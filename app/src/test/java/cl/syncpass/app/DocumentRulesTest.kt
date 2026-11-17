package cl.syncpass.app

import cl.syncpass.app.domain.DocumentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentRulesTest {
    @Test
    fun normalizeRemovesDotsSpacesAndHyphens() {
        assertEquals("123456789", DocumentRules.normalize(" 12.345.678-9 "))
        assertEquals("ABC123", DocumentRules.normalize("abc 123"))
    }

    @Test
    fun validatesDocuments() {
        assertNull(DocumentRules.validateDocument("12.345.678-9"))
        assertNull(DocumentRules.validateDocument("PASSPORT123"))

        assertNotNull(DocumentRules.validateDocument(""))
        assertNotNull(DocumentRules.validateDocument("ab"))
        assertNotNull(DocumentRules.validateDocument("12#34"))
        assertNotNull(DocumentRules.validateDocument("A".repeat(31)))
    }

    @Test
    fun validatesEmails() {
        assertNull(DocumentRules.validateEmail(""))
        assertNull(DocumentRules.validateEmail("ana.perez@example.cl"))
        assertNotNull(DocumentRules.validateEmail("ana@"))
        assertNotNull(DocumentRules.validateEmail("sin arroba.cl"))
    }

    @Test
    fun validatesNames() {
        assertNull(DocumentRules.validateName("Ana Pérez"))
        assertNotNull(DocumentRules.validateName("A"))
        assertNotNull(DocumentRules.validateName("A".repeat(81)))
    }
}
