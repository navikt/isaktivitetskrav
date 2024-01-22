package no.nav.syfo.client.dokarkiv

import io.ktor.client.plugins.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import org.amshove.kluent.internal.assertFailsWith
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
                varselId = UUID.randomUUID()
            )

            runBlocking {
                val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)

                response.journalpostId shouldBeEqualTo 1
            }
        }

        it("handles conflict from api when eksternRefeanseId exists") {
            val journalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om stans av sykepenger",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = UserConstants.EXISTING_EKSTERN_REFERANSE_UUID,
            )

            assertFailsWith<ClientRequestException> {
                runBlocking {
                    dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)
                }
            }
        }
    }
})
