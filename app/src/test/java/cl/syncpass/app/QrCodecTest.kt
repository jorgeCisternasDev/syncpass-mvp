package cl.syncpass.app

import cl.syncpass.app.domain.QrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {
    @Test
    fun encodeAndParseRoundTrip() {
        val raw = QrCodec.encode(42, "spab12cd")
        assertEquals("SYNCPASS:V1:42:SPAB12CD", raw)
        val payload = QrCodec.parse(raw)
        assertEquals(42L, payload?.eventId)
        assertEquals("SPAB12CD", payload?.participantCode)
    }

    @Test
    fun parserAcceptsDifferentLetterCase() {
        val payload = QrCodec.parse("syncpass:v1:7:spxyz234")
        assertEquals(7L, payload?.eventId)
        assertEquals("SPXYZ234", payload?.participantCode)
    }

    @Test
    fun parserRejectsInvalidPayloads() {
        assertNull(QrCodec.parse("SYNCPASS:V2:1:SPAB12CD"))
        assertNull(QrCodec.parse("SYNCPASS:V1:0:SPAB12CD"))
        assertNull(QrCodec.parse("SYNCPASS:V1:1:ab"))
        assertNull(QrCodec.parse("SYNCPASS:V1:1:SPAB12CD:extra"))
        assertNull(QrCodec.parse("SPAB12CD"))
    }

    @Test
    fun detectsQrPrefix() {
        assertTrue(QrCodec.looksLikeQr("  syncpass:V1:1:SPAB12CD"))
        assertFalse(QrCodec.looksLikeQr("SPAB12CD"))
    }
}
