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
                val varselPdfDTO = VarselPdfDTO.create(
                    mottakerNavn = UserConstants.PERSON_FULLNAME,
                    mottakerPersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    documentComponents = emptyList(),
                )

                val pdf = pdfGenClient.createForhandsvarselPdf(
                    callId = "",
                    varselPdfDTO = varselPdfDTO,
                )
                pdf shouldBeEqualTo UserConstants.PDF_FORHANDSVARSEL
            }
        }
    }
})
