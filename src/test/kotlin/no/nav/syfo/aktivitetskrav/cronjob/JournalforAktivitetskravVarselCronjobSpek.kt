package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

const val anyJournalpostId = 1
const val anyJournalpostIdAsString = anyJournalpostId.toString()
val anyJournalpostResponse = JournalpostResponse(
    dokumenter = null,
    journalpostId = anyJournalpostId,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)

val pdf = byteArrayOf(23)
val aktivitetskravVarsel = AktivitetskravVarsel.create(document = emptyList())

class JournalforAktivitetskravVarselCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val azureAdClient = AzureAdClient(
            azureEnvironment = externalMockEnvironment.environment.azure,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
        val pdlClient = PdlClient(
            azureAdClient = azureAdClient,
            pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
            httpClient = externalMockEnvironment.mockHttpClient,
        )

        val aktivitetskravVarselService = mockk<AktivitetskravVarselService>()
        val dokarkivClient = mockk<DokarkivClient>()

        val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
            aktivitetskravVarselService = aktivitetskravVarselService,
            dokarkivClient = dokarkivClient,
            pdlClient = pdlClient,
        )

        beforeEachTest {
            clearMocks(dokarkivClient, aktivitetskravVarselService)
            every { aktivitetskravVarselService.updateJournalpostId(any(), anyJournalpostIdAsString) } returns Unit
        }
        afterEachTest {
            database.dropData()
        }

        describe("${JournalforAktivitetskravVarselCronjob::class.java.simpleName} runJob") {
            it("Journalfører og oppdaterer journalpostId for ikke-journalført forhandsvarsel") {
                val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                    tittel = "Forhåndsvarsel om stans av sykepenger",
                    brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                    pdf = pdf,
                    kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
                )

                every { aktivitetskravVarselService.getIkkeJournalforte() } returns listOf(
                    Triple(
                        UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        aktivitetskravVarsel,
                        pdf
                    )
                )
                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1

                    coVerify {
                        dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
                    }
                    verify {
                        aktivitetskravVarselService.updateJournalpostId(aktivitetskravVarsel, anyJournalpostIdAsString)
                    }
                }
            }
            it("Journalfører ikke og oppdaterer ingenting når forhandsvarsel er journalført fra før") {
                every { aktivitetskravVarselService.getIkkeJournalforte() } returns emptyList()
                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0

                    coVerify(exactly = 0) {
                        dokarkivClient.journalfor(any())
                    }
                    verify(exactly = 0) {
                        aktivitetskravVarselService.updateJournalpostId(any(), any())
                    }
                }
            }
            it("Feiler og oppdaterer ingenting når person tilknyttet forhåndsvarsel mangler navn") {
                every { aktivitetskravVarselService.getIkkeJournalforte() } returns listOf(
                    Triple(
                        UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME,
                        aktivitetskravVarsel,
                        pdf
                    )
                )
                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0

                    coVerify(exactly = 0) {
                        dokarkivClient.journalfor(any())
                    }
                    verify(exactly = 0) {
                        aktivitetskravVarselService.updateJournalpostId(any(), any())
                    }
                }
            }
            it("Oppdaterer ikke journalpostId når journalføring feiler") {
                val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                    tittel = "Forhåndsvarsel om stans av sykepenger",
                    brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                    pdf = pdf,
                    kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
                )

                every { aktivitetskravVarselService.getIkkeJournalforte() } returns listOf(
                    Triple(
                        UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        aktivitetskravVarsel,
                        pdf
                    )
                )
                coEvery { dokarkivClient.journalfor(any()) } throws RuntimeException("Journalføring feilet")

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0

                    coVerify(exactly = 1) {
                        dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
                    }
                    verify(exactly = 0) {
                        aktivitetskravVarselService.updateJournalpostId(any(), any())
                    }
                }
            }
        }
    }
})
