package no.nav.syfo.aktivitetskrav.cronjob

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.aktivitetskrav.api.Arsak
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class JournalforAktivitetskravVarselCronjobTest {
    private val anyJournalpostId = 1
    private val anyJournalpostResponse = JournalpostResponse(
        dokumenter = null,
        journalpostId = anyJournalpostId,
        journalpostferdigstilt = null,
        journalstatus = "status",
        melding = null,
    )
    private val pdf = byteArrayOf(23)

    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val aktivitetskrav = Aktivitetskrav.create(
        personIdent = personIdent,
        oppfolgingstilfelleStart = LocalDate.now(),
    )
    private val personIdentManglerNavn = UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME
    private val aktivitetskravPersonManglerNavn = Aktivitetskrav.create(
        personIdent = personIdentManglerNavn,
        oppfolgingstilfelleStart = LocalDate.now(),
    )

    private val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val dokarkivClient = mockk<DokarkivClient>()
    private val aktivitetskravRepository = AktivitetskravRepository(database = database)

    private val aktivitetskravVarselService = AktivitetskravVarselService(
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = mockk(),
        aktivitetskravVarselProducer = mockk(),
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
    )

    private val journalforAktivitetskravVarselCronjob = JournalforAktivitetskravVarselCronjob(
        dokarkivClient = dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        aktivitetskravVarselService = aktivitetskravVarselService,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )

    private fun createVarsel(
        aktivitetskrav: Aktivitetskrav,
        pdf: ByteArray,
        varselType: VarselType,
        frist: LocalDate? = LocalDate.now().plusDays(30),
        document: List<DocumentComponentDTO>,
    ): AktivitetskravVarsel {
        val forhandsvarsel = AktivitetskravVarsel.create(
            type = varselType,
            frist = frist,
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

    @BeforeEach
    fun setUp() {
        clearMocks(dokarkivClient)
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
        aktivitetskravRepository.createAktivitetskrav(aktivitetskravPersonManglerNavn)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Nested
    @DisplayName("runJob")
    inner class RunJob {

        @Test
        fun `journalfører og oppdaterer journalpostId for ikke journalført unntak-vurdering`() {
            val fritekst = "Aktivitetskravet er oppfylt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.UNNTAK,
                beskrivelse = fritekst,
                arsaker = listOf(Arsak.MEDISINSKE_GRUNNER),
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
                tittel = "Vurdering av aktivitetskravet - Unntak",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestUnntakVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNotNull(first.journalpostId)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
        }

        @Test
        fun `journalfører og oppdaterer journalpostId for ikke journalført ikke aktuell-vurdering`() {
            val fritekst = "Aktivitetskravet er ikke aktuelt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.IKKE_AKTUELL,
                beskrivelse = fritekst,
                arsaker = listOf(Arsak.INNVILGET_VTA),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.IKKE_AKTUELL,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestIkkeAktuellVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet - Ikke aktuell",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestIkkeAktuellVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNotNull(first.journalpostId)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
        }

        @Test
        fun `journalfører og oppdaterer journalpostId for ikke journalført oppfylt-vurdering`() {
            val fritekst = "Aktivitetskravet er oppfylt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                beskrivelse = fritekst,
                arsaker = listOf(Arsak.FRISKMELDT),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.OPPFYLT,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestOppfyltVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet - Oppfylt",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_VURDERING,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestOppfyltVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNotNull(first.journalpostId)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
        }

        @Test
        fun `journalfører og oppdaterer journalpostId for ikke-journalført forhandsvarsel`() {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )

            val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet - Forhåndsvarsel om stans",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.UTGAAENDE.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNotNull(first.journalpostId)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
        }

        @Test
        fun `journalfører og oppdaterer journalpostId for ikke-journalført stans-vurdering`() {
            val fritekst = "Aktivitetskravet er oppfylt"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.INNSTILLING_OM_STANS,
                beskrivelse = fritekst,
                stansFom = LocalDate.now(),
                createdBy = UserConstants.VEILEDER_IDENT,
            )
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.INNSTILLING_OM_STANS,
                frist = null,
                document = generateDocumentComponentDTO(fritekst),
            )

            val expectedJournalpostRequestUnntakVarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet - Innstilling om stans",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_STANS,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.NOTAT.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }

            coVerify {
                dokarkivClient.journalfor(expectedJournalpostRequestUnntakVarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            assertEquals(VarselType.INNSTILLING_OM_STANS.name, varsler.first().type)
        }

        @Test
        fun `journalfører ikke og oppdaterer ingenting når forhandsvarsel er journalført fra før`() {
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

                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }

            coVerify(exactly = 0) {
                dokarkivClient.journalfor(any())
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNotNull(first.journalpostId)
        }

        @Test
        fun `journalfører ikke og oppdaterer ingenting når det ikke finnes forhandsvarsel`() {
            coEvery { dokarkivClient.journalfor(any()) } returns anyJournalpostResponse

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }

            coVerify(exactly = 0) {
                dokarkivClient.journalfor(any())
            }

            val varsler = database.getVarsler(personIdent)
            assertTrue(varsler.isEmpty())
        }

        @Test
        fun `feiler og oppdaterer ingenting når person tilknyttet forhåndsvarsel mangler navn`() {
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

                assertEquals(1, result.failed)
                assertEquals(0, result.updated)
            }

            coVerify(exactly = 0) {
                dokarkivClient.journalfor(any())
            }

            val varsler = database.getVarsler(personIdentManglerNavn)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNull(first.journalpostId)
            assertEquals(first.createdAt.sekundOpplosning(), first.updatedAt.sekundOpplosning())
        }

        @Test
        fun `oppdaterer ikke journalpostId når journalføring feiler`() {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val varsel = createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )

            val expectedJournalpostRequestForhandsvarsel = generateJournalpostRequest(
                tittel = "Vurdering av aktivitetskravet - Forhåndsvarsel om stans",
                brevkodeType = BrevkodeType.AKTIVITETSKRAV_FORHANDSVARSEL,
                pdf = pdf,
                varselId = varsel.uuid,
                journalpostType = JournalpostType.UTGAAENDE.name,
            )

            coEvery { dokarkivClient.journalfor(any()) } throws RuntimeException("Journalføring feilet")

            runBlocking {
                val result = journalforAktivitetskravVarselCronjob.runJob()

                assertEquals(1, result.failed)
                assertEquals(0, result.updated)
            }

            coVerify(exactly = 1) {
                dokarkivClient.journalfor(expectedJournalpostRequestForhandsvarsel)
            }

            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertNull(first.journalpostId)
            assertEquals(first.createdAt.sekundOpplosning(), first.updatedAt.sekundOpplosning())
        }
    }
}
