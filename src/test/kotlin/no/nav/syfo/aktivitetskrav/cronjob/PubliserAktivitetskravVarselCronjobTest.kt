package no.nav.syfo.aktivitetskrav.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.AktivitetskravVarselService
import no.nav.syfo.application.VarselPdfService
import no.nav.syfo.api.dto.Arsak
import no.nav.syfo.api.dto.DocumentComponentDTO
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.cronjob.PubliserAktivitetskravVarselCronjob
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVarselProducer
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVarselRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import no.nav.syfo.testhelper.getVarsler
import no.nav.syfo.util.sekundOpplosning
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.Future

class PubliserAktivitetskravVarselCronjobTest {
    private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    private val aktivitetskrav = Aktivitetskrav.create(
        personIdent = personIdent,
        oppfolgingstilfelleStart = LocalDate.now(),
    )
    private val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")
    private val pdf = byteArrayOf(23)
    private val defaultJournalpostId = "9"

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravRepository = AktivitetskravRepository(database = database)

    private val aktivitetskravVarselKafkaProducer = mockk<KafkaProducer<String, AktivitetskravVarselRecord>>()
    private val aktivitetskravVarselProducer = AktivitetskravVarselProducer(
        kafkaProducer = aktivitetskravVarselKafkaProducer,
    )
    private val aktivitetskravVarselService = AktivitetskravVarselService(
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = mockk(),
        aktivitetskravVarselProducer = aktivitetskravVarselProducer,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
    )

    private val publiserAktivitetskravVarselCronjob = PubliserAktivitetskravVarselCronjob(
        aktivitetskravVarselService = aktivitetskravVarselService,
    )

    private fun createVarsel(
        aktivitetskrav: Aktivitetskrav,
        pdf: ByteArray,
        journalpostId: String? = defaultJournalpostId,
        varselType: VarselType,
        frist: LocalDate? = LocalDate.now().plusDays(30),
        document: List<DocumentComponentDTO>,
    ): AktivitetskravVarsel {
        val varsel = AktivitetskravVarsel.create(
            type = varselType,
            frist = frist,
            document = document,
        )
        aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
            aktivitetskrav = aktivitetskrav,
            varsel = varsel,
            newVurdering = aktivitetskrav.vurderinger.first(),
            pdf = pdf,
        )
        if (journalpostId != null) {
            aktivitetskravVarselRepository.updateJournalpostId(varsel, journalpostId)
        }
        return varsel
    }

    @BeforeEach
    fun setUp() {
        clearMocks(aktivitetskravVarselKafkaProducer)
        coEvery {
            aktivitetskravVarselKafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)

        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Nested
    @DisplayName("runJob")
    inner class RunJob {

        @Test
        fun `publiserer journalfort forhandsvarsel`() {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )
            val varslerBefore = database.getVarsler(personIdent)
            assertEquals(1, varslerBefore.size)
            assertNull(varslerBefore.first().publishedAt)

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varslerBefore.first().uuid, first.uuid)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
            assertNotNull(first.publishedAt)

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVarselRecord>>()
            verify(exactly = 1) {
                aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVarsel.personIdent)
            assertNotEquals(kafkaAktivitetskravVarsel.aktivitetskravUuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(aktivitetskrav.uuid, kafkaAktivitetskravVarsel.aktivitetskravUuid)
            assertEquals(first.uuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(first.createdAt, kafkaAktivitetskravVarsel.createdAt)
            assertNotNull(kafkaAktivitetskravVarsel.journalpostId)
            assertEquals(first.journalpostId, kafkaAktivitetskravVarsel.journalpostId)
            assertFalse(kafkaAktivitetskravVarsel.document.isEmpty())
            assertEquals(first.svarfrist, kafkaAktivitetskravVarsel.svarfrist)
            assertEquals(vurdering.uuid, kafkaAktivitetskravVarsel.vurderingUuid)
            assertEquals(VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER.name, kafkaAktivitetskravVarsel.type)
        }

        @Test
        fun `publiserer ikke forhandsvarsel som ikke er journalfort`() {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
                journalpostId = null,
            )
            val varslerBefore = database.getVarsler(personIdent)
            assertEquals(1, varslerBefore.size)
            assertNull(varslerBefore.first().publishedAt)

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varslerBefore.first().uuid, first.uuid)
            assertEquals(first.createdAt.sekundOpplosning(), first.updatedAt.sekundOpplosning())
            assertNull(first.publishedAt)
        }

        @Test
        fun `publiserer ikke forhandsvarsel som allerede er publisert`() {
            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            createVarsel(
                aktivitetskrav = updatedAktivitetskrav,
                pdf = pdf,
                varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                document = forhandsvarselDTO.document,
            )
            val varslerBefore = database.getVarsler(personIdent)
            assertEquals(1, varslerBefore.size)
            assertNull(varslerBefore.first().publishedAt)

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(0, result.updated)
            }
        }

        @Test
        fun `publiserer journalført varsel for UNNTAK`() {
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

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
            assertNotNull(first.publishedAt)

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVarselRecord>>()
            verify(exactly = 1) {
                aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVarsel.personIdent)
            assertNotEquals(kafkaAktivitetskravVarsel.aktivitetskravUuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(aktivitetskrav.uuid, kafkaAktivitetskravVarsel.aktivitetskravUuid)
            assertEquals(first.uuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(first.createdAt, kafkaAktivitetskravVarsel.createdAt)
            assertNotNull(kafkaAktivitetskravVarsel.journalpostId)
            assertEquals(first.journalpostId, kafkaAktivitetskravVarsel.journalpostId)
            assertFalse(kafkaAktivitetskravVarsel.document.isEmpty())
            assertEquals(first.svarfrist, kafkaAktivitetskravVarsel.svarfrist)
            assertEquals(vurdering.uuid, kafkaAktivitetskravVarsel.vurderingUuid)
            assertEquals(VarselType.UNNTAK.name, kafkaAktivitetskravVarsel.type)
        }

        @Test
        fun `publiserer journalført varsel for OPPFYLT`() {
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

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
            assertNotNull(first.publishedAt)

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVarselRecord>>()
            verify(exactly = 1) {
                aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVarsel.personIdent)
            assertNotEquals(kafkaAktivitetskravVarsel.aktivitetskravUuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(aktivitetskrav.uuid, kafkaAktivitetskravVarsel.aktivitetskravUuid)
            assertEquals(first.uuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(first.createdAt, kafkaAktivitetskravVarsel.createdAt)
            assertNotNull(kafkaAktivitetskravVarsel.journalpostId)
            assertEquals(first.journalpostId, kafkaAktivitetskravVarsel.journalpostId)
            assertFalse(kafkaAktivitetskravVarsel.document.isEmpty())
            assertEquals(first.svarfrist, kafkaAktivitetskravVarsel.svarfrist)
            assertEquals(vurdering.uuid, kafkaAktivitetskravVarsel.vurderingUuid)
            assertEquals(VarselType.OPPFYLT.name, kafkaAktivitetskravVarsel.type)
        }

        @Test
        fun `publiserer journalført varsel for IKKE AKTUELL`() {
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

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
            assertNotNull(first.publishedAt)

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVarselRecord>>()
            verify(exactly = 1) {
                aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVarsel.personIdent)
            assertNotEquals(kafkaAktivitetskravVarsel.aktivitetskravUuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(aktivitetskrav.uuid, kafkaAktivitetskravVarsel.aktivitetskravUuid)
            assertEquals(first.uuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(first.createdAt, kafkaAktivitetskravVarsel.createdAt)
            assertNotNull(kafkaAktivitetskravVarsel.journalpostId)
            assertEquals(first.journalpostId, kafkaAktivitetskravVarsel.journalpostId)
            assertFalse(kafkaAktivitetskravVarsel.document.isEmpty())
            assertEquals(first.svarfrist, kafkaAktivitetskravVarsel.svarfrist)
            assertEquals(vurdering.uuid, kafkaAktivitetskravVarsel.vurderingUuid)
            assertEquals(VarselType.IKKE_AKTUELL.name, kafkaAktivitetskravVarsel.type)
        }

        @Test
        fun `publiserer journalført vurdering om STANS`() {
            val fritekst = "Aktivitetskravet er ikke oppfylt, og sykepengene stanses"
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

            runBlocking {
                val result = publiserAktivitetskravVarselCronjob.runJob()
                assertEquals(0, result.failed)
                assertEquals(1, result.updated)
            }
            val varsler = database.getVarsler(personIdent)
            assertEquals(1, varsler.size)
            val first = varsler.first()
            assertEquals(varsel.uuid, first.uuid)
            assertTrue(first.updatedAt.isAfter(first.createdAt))
            assertNotNull(first.publishedAt)

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVarselRecord>>()
            verify(exactly = 1) {
                aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVarsel.personIdent)
            assertNotEquals(kafkaAktivitetskravVarsel.aktivitetskravUuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(aktivitetskrav.uuid, kafkaAktivitetskravVarsel.aktivitetskravUuid)
            assertEquals(first.uuid, kafkaAktivitetskravVarsel.varselUuid)
            assertEquals(first.createdAt, kafkaAktivitetskravVarsel.createdAt)
            assertNotNull(kafkaAktivitetskravVarsel.journalpostId)
            assertEquals(first.journalpostId, kafkaAktivitetskravVarsel.journalpostId)
            assertFalse(kafkaAktivitetskravVarsel.document.isEmpty())
            assertEquals(first.svarfrist, kafkaAktivitetskravVarsel.svarfrist)
            assertEquals(vurdering.uuid, kafkaAktivitetskravVarsel.vurderingUuid)
            assertEquals(VarselType.INNSTILLING_OM_STANS.name, kafkaAktivitetskravVarsel.type)
        }
    }
}
