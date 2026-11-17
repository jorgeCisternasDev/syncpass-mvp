package cl.syncpass.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.syncpass.app.domain.QrCodec
import cl.syncpass.app.domain.QrGenerator
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QrRoundTripTest {

    @Test
    fun generatedQrIsDecodedByMlKit() {
        val payload = QrCodec.encode(123, "SPAB12CD")
        val bitmap = QrGenerator.generate(payload, 640)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
        try {
            val barcodes = Tasks.await(
                scanner.process(InputImage.fromBitmap(bitmap, 0)),
                30,
                TimeUnit.SECONDS
            )
            val raw = barcodes.firstOrNull()?.rawValue
            assertNotNull(raw)
            assertEquals(payload, raw)
            val parsed = QrCodec.parse(raw!!)
            assertEquals(123L, parsed?.eventId)
            assertEquals("SPAB12CD", parsed?.participantCode)
        } finally {
            scanner.close()
        }
    }
}
