package no.nav.syfo.client.dokarkiv

import io.ktor.client.plugins.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.client.dokarkiv.model.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.model.JournalpostType
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import no.nav.syfo.testhelper.mock.conflictResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class DokarkivClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val dokarkivClient = DokarkivClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        dokarkivEnvironment = externalMockEnvironment.environment.clients.dokarkiv,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    private val pdf = byteArrayOf(23)

    @Test
    fun `journalfører aktivitetskrav`() {
        val journalpostRequestForhandsvarsel = generateJournalpostRequest(
            tittel = "Forhåndsvarsel om stans av sykepenger",
            brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
            pdf = pdf,
            varselId = UUID.randomUUID(),
            journalpostType = JournalpostType.UTGAAENDE.name
        )

        runBlocking {
            val response = dokarkivClient.journalfor(journalpostRequest = journalpostRequestForhandsvarsel)

            assertEquals(1, response.journalpostId)
        }
    }

    @Test
    fun `handles conflict from api when eksternRefeanseId exists, and uses the existing journalpostId`() {
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

            assertEquals(conflictResponse.journalpostId, journalpostResponse.journalpostId)
            assertEquals(conflictResponse.journalstatus, journalpostResponse.journalstatus)
        }
    }
}
