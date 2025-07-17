// filepath: /Users/vetle/projects/NAV/isyfo/isaktivitetskrav/src/test/kotlin/no/nav/syfo/client/pdfgen/PdfGenClientTest.kt
package no.nav.syfo.client.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("PdfGenClient")
class PdfGenClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val pdfGenClient = externalMockEnvironment.pdfgenClient

    @Nested
    @DisplayName("PDF Generation")
    inner class PdfGeneration {
        @Test
        fun `returns bytearray of pdf for forhandsvarsel`() {
            val forhandsvarselPdfDTO = ForhandsvarselPdfDTO.create(
                mottakerNavn = UserConstants.PERSON_FULLNAME,
                mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                documentComponents = emptyList(),
            )

            val pdf = runBlocking {
                pdfGenClient.createForhandsvarselPdf(
                    callId = "",
                    forhandsvarselPdfDTO = forhandsvarselPdfDTO,
                )
            }

            assertArrayEquals(UserConstants.PDF_FORHANDSVARSEL, pdf)
        }

        @Test
        fun `returns bytearray of pdf for vurdering`() {
            val pdf = runBlocking {
                pdfGenClient.createVurderingPdf(
                    callId = "",
                    VurderingPdfDTO.create(emptyList()),
                )
            }

            assertArrayEquals(UserConstants.PDF_VURDERING, pdf)
        }
    }
}
