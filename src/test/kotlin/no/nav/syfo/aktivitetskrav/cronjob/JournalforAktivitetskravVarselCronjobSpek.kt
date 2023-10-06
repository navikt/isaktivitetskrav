package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import no.nav.syfo.util.sekundOpplosning
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

const val anyJournalpostId = 1
val anyJournalpostResponse = JournalpostResponse(
    dokumenter = null,
    journalpostId = anyJournalpostId,
    journalpostferdigstilt = null,
    journalstatus = "status",
    melding = null,
)
val pdf = byteArrayOf(23)

val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
val aktivitetskrav = Aktivitetskrav.ny(
    personIdent = personIdent,
    tilfelleStart = LocalDate.now()
)
val personIdentManglerNavn = UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME
val aktivitetskravPersonManglerNavn = Aktivitetskrav.ny(
    personIdent = personIdentManglerNavn,
    tilfelleStart = LocalDate.now()
)

val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")

class JournalforAktivitetskravVarselCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
        val dokarkivClient = mockk<DokarkivClient>()

        val aktivitetskravVarselService = AktivitetskravVarselService(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            aktivitetskravVurderingProducer = mockk(),
            arbeidstakervarselProducer = mockk(),
            aktivitetskravVarselProducer = mockk(),
            expiredVarselProducer = mockk(),
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
            krrClient = externalMockEnvironment.krrClient,
        )

        val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
            dokarkivClient = dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
            aktivitetskravVarselService = aktivitetskravVarselService,
        )

        fun createForhandsvarsel(aktivitetskrav: Aktivitetskrav, pdf: ByteArray): AktivitetskravVarsel {
            database.createAktivitetskrav(aktivitetskrav)

            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val forhandsvarsel = AktivitetskravVarsel.create(forhandsvarselDTO.document)
            aktivitetskravVarselRepository.create(
                aktivitetskrav = updatedAktivitetskrav,
                varsel = forhandsvarsel,
                pdf = pdf,
            )

            return forhandsvarsel
        }

        beforeEachTest {
            clearMocks(dokarkivClient)
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

                val varsel = createForhandsvarsel(aktivitetskrav = aktivitetskrav, pdf = pdf)

                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                coVerify {
                    dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
                }

                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.journalpostId.shouldNotBeNull()
                first.updatedAt shouldBeGreaterThan first.createdAt
            }
            it("Journalfører ikke og oppdaterer ingenting når forhandsvarsel er journalført fra før") {
                val varsel = createForhandsvarsel(aktivitetskrav = aktivitetskrav, pdf = pdf)
                aktivitetskravVarselRepository.updateJournalpostId(varsel, "1")

                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                coVerify(exactly = 0) {
                    dokarkivClient.journalfor(any())
                }

                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.journalpostId.shouldNotBeNull()
            }
            it("Journalfører ikke og oppdaterer ingenting når det ikke finnes forhandsvarsel") {
                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                coVerify(exactly = 0) {
                    dokarkivClient.journalfor(any())
                }

                val varsler = database.getVarsler(personIdent)
                varsler.shouldBeEmpty()
            }
            it("Feiler og oppdaterer ingenting når person tilknyttet forhåndsvarsel mangler navn") {
                val varsel = createForhandsvarsel(aktivitetskrav = aktivitetskravPersonManglerNavn, pdf = pdf)

                coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }

                coVerify(exactly = 0) {
                    dokarkivClient.journalfor(any())
                }

                val varsler = database.getVarsler(personIdentManglerNavn)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.journalpostId.shouldBeNull()
                first.updatedAt.sekundOpplosning() shouldBeEqualTo first.createdAt.sekundOpplosning()
            }
            it("Oppdaterer ikke journalpostId når journalføring feiler") {
                val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                    tittel = "Forhåndsvarsel om stans av sykepenger",
                    brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                    pdf = pdf,
                    kanal = JournalpostKanal.SENTRAL_UTSKRIFT.value,
                )

                val varsel = createForhandsvarsel(aktivitetskrav = aktivitetskrav, pdf = pdf)

                coEvery { dokarkivClient.journalfor(any()) } throws RuntimeException("Journalføring feilet")

                runBlocking {
                    val result = journalforAktivitetskravVarselCronjob.runJob()

                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 0
                }

                coVerify(exactly = 1) {
                    dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
                }

                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.journalpostId.shouldBeNull()
                first.updatedAt.sekundOpplosning() shouldBeEqualTo first.createdAt.sekundOpplosning()
            }
        }
    }
})
