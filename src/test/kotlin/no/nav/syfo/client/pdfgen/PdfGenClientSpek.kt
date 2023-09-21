package no.nav.syfo.client.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdfGenClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdfGenClient = externalMockEnvironment.pdfgenClient

    describe("${PdlClient::class.java.simpleName}: navn") {
        it("returns bytearray of pdf when requestBody is set correct") {
            runBlocking {
                val forhandsvarselPdfDTO = ForhandsvarselPdfDTO.create(
                    mottakerNavn = UserConstants.PERSON_FULLNAME,
                    mottakerFodselsnummer = UserConstants.ARBEIDSTAKER_PERSONIDENT.value,
                    documentComponents = emptyList(),
                )

                val pdf = pdfGenClient.createForhandsvarselPdf(
                    callId = "",
                    forhandsvarselPdfDTO = forhandsvarselPdfDTO,
                )
                pdf shouldBeEqualTo UserConstants.PDF_FORHANDSVARSEL
            }
        }
    }
})
