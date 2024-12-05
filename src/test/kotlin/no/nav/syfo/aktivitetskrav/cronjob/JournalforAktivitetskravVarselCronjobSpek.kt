package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.domain.*
import no.nav.syfo.client.dokarkiv.DokarkivClient
import no.nav.syfo.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.client.dokarkiv.domain.JournalpostResponse
import no.nav.syfo.client.dokarkiv.domain.JournalpostType
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import no.nav.syfo.testhelper.generator.generateJournalpostRequest
import no.nav.syfo.testhelper.getVarsler
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
val aktivitetskrav = Aktivitetskrav.create(
    personIdent = personIdent,
    oppfolgingstilfelleStart = LocalDate.now(),
)
val personIdentManglerNavn = UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME
val aktivitetskravPersonManglerNavn = Aktivitetskrav.create(
    personIdent = personIdentManglerNavn,
    oppfolgingstilfelleStart = LocalDate.now(),
)

val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")

class JournalforAktivitetskravVarselCronjobSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database

    val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    val dokarkivClient = mockk<DokarkivClient>()
    val aktivitetskravRepository = AktivitetskravRepository(database = database)

    val aktivitetskravVarselService = AktivitetskravVarselService(
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = mockk(),
        aktivitetskravVarselProducer = mockk(),
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
    )

    val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
        dokarkivClient = dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        aktivitetskravVarselService = aktivitetskravVarselService,
    )

    fun createVarsel(
        aktivitetskrav: Aktivitetskrav,
        pdf: ByteArray,
        varselType: VarselType,
        document: List<DocumentComponentDTO>,
    ): AktivitetskravVarsel {
        val forhandsvarsel = AktivitetskravVarsel.create(
            type = varselType,
            frist = LocalDate.now().plusDays(30),
            document = document,
        )
        aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
            aktivitetskrav = aktivitetskrav,
            varsel = forhandsvarsel,
            newVurdering = aktivitetskrav.vurderinger.first(),
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
        beforeEachTest {
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            aktivitetskravRepository.createAktivitetskrav(aktivitetskravPersonManglerNavn)
        }
        it("Journalfører og oppdaterer journalpostId for ikke journalført unntak-vurdering") {
            val fritekst = "Aktivitetskravet er oppfylt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.UNNTAK,
                beskrivelse = fritekst,
                arsaker = listOf(VurderingArsak.Unntak.MedisinskeGrunner),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.UNNTAK,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestUnntakVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestUnntakVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            varsler.size shouldBeEqualTo 1
            val first = varsler.first()
            first.uuid shouldBeEqualTo varsel.uuid
            first.journalpostId.shouldNotBeNull()
            first.updatedAt shouldBeGreaterThan first.createdAt
        }

        it("Journalfører og oppdaterer journalpostId for ikke journalført ikke aktuell-vurdering") {
            val fritekst = "Aktivitetskravet er ikke aktuelt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.IKKE_AKTUELL,
                beskrivelse = fritekst,
                arsaker = listOf(VurderingArsak.IkkeAktuell.InnvilgetVTA),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.IKKE_AKTUELL,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestUnntakVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestUnntakVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            varsler.size shouldBeEqualTo 1
            val first = varsler.first()
            first.uuid shouldBeEqualTo varsel.uuid
            first.journalpostId.shouldNotBeNull()
            first.updatedAt shouldBeGreaterThan first.createdAt
        }

        it("Journalfører og oppdaterer journalpostId for ikke journalført oppfylt-vurdering") {
            val fritekst = "Aktivitetskravet er oppfylt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                beskrivelse = fritekst,
                arsaker = listOf(VurderingArsak.Oppfylt.Friskmeldt),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.OPPFYLT,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestUnntakVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestUnntakVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            varsler.size shouldBeEqualTo 1
            val first = varsler.first()
            first.uuid shouldBeEqualTo varsel.uuid
            first.journalpostId.shouldNotBeNull()
            first.updatedAt shouldBeGreaterThan first.createdAt
        }

        it("Journalfører og oppdaterer journalpostId for ikke-journalført forhandsvarsel") {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )

            val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om stans av sykepenger",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.UTGAAENDE.name,
            )

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
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )
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
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskravPersonManglerNavn.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )

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
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )

            val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Forhåndsvarsel om stans av sykepenger",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.UTGAAENDE.name,
            )

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
})
