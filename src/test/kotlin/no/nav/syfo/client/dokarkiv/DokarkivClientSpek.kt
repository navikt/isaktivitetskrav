package no.nav.syfo.client.dokarkiv

import io.ktor.client.plugins.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostType
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import no.nav.syfo.testhelper.mock.conflictResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class DokarkivClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val dokarkivClient = DokarkivClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        dokarkivEnvironment = externalMockEnvironment.environment.clients.dokarkiv,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    describe("${DokarkivClient::class.java.simpleName}: journalføring") {
        val pdf = byteArrayOf(23)

        it("journalfører aktivitetskrav") {
            val journalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om stans av sykepenger",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = UUID.randomUUID(),
                journalpostType = JournalpostType.UTGAAENDE.name
            )

            runBlocking {
                val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)

                response.journalpostId shouldBeEqualTo 1
            }
        }

        it("handles conflict from api when eksternRefeanseId exists, and uses the existing journalpostId") {
            val journalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om stans av sykepenger",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID,
                journalpostType = JournalpostType.UTGAAENDE.name,
            )

            runBlocking {
                val journalpostResponse =
                    dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)

                journalpostResponse.journalpostId shouldBeEqualTo conflictResponse.journalpostId
                journalpostResponse.journalstatus shouldBeEqualTo conflictResponse.journalstatus
            }
        }
    }
})
