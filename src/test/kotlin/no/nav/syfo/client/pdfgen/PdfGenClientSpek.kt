package no.nav.syfo.client.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdfGenClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdfGenClient = externalMockEnvironment.pdfgenClient

    describe("${PdfGenClient::class.java.simpleName}: navn") {
        it("returns bytearray of pdf for forhandsvarsel") {
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

            pdf shouldBeEqualTo UserConstants.PDF_FORHANDSVARSEL
        }
        it("returns bytearray of pdf for vurdering") {
            val pdf = runBlocking {
                pdfGenClient.createVurderingPdf(
                    callId = "",
                    VurderingPdfDTO.create(emptyList()),
                )
            }

            pdf shouldBeEqualTo UserConstants.PDF_VURDERING
        }
    }
})
