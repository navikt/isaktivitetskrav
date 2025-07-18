package no.nav.syfo.aktivitetskrav

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.Arsak
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.VarselType
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVarsel
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.createAktivitetskravOppfylt
import no.nav.syfo.testhelper.generator.createVurdering
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.getAktivitetskravVarselPdf
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Future

class AktivitetskravServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vurderingProducerMock = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    private val aktivitetskravRepository = AktivitetskravRepository(database = database)
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
    private val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = aktivitetskravRepository,
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
    )

    @BeforeEach
    fun setUp() {
        clearMocks(vurderingProducerMock)
        coEvery {
            vurderingProducerMock.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Nested
    @DisplayName("Create aktivitetskrav")
    inner class CreateAktivitetskrav {

        @Test
        fun `creates aktivitetskrav with previous aktivitetskrav from service`() {
            val previousAktivitetskrav = createAktivitetskravOppfylt(
                createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            )
            val createdAktivitetskrav =
                aktivitetskravService.createAktivitetskrav(
                    UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    previousAktivitetskrav = previousAktivitetskrav
                )
            val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

            verify(exactly = 1) {
                vurderingProducerMock.send(capture(producerRecordSlot))
            }

            val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, createdAktivitetskrav.personIdent.value)
            assertEquals(previousAktivitetskrav.uuid, savedAktivitetskrav?.previousAktivitetskravUuid)
            assertEquals(previousAktivitetskrav.uuid, aktivitetskravVurderingRecord.previousAktivitetskravUuid)
            assertEquals(createdAktivitetskrav.uuid, aktivitetskravVurderingRecord.uuid)
        }

        @Test
        fun `create aktivitetskrav with previous aktivitetskrav not final throws exception`() {
            val previousAktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            assertThrows<ConflictException> {
                aktivitetskravService.createAktivitetskrav(
                    UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    previousAktivitetskrav = previousAktivitetskrav
                )
            }
        }

        @Test
        fun `creates aktivitetskrav without previous aktivitetskrav from service`() {
            val createdAktivitetskrav =
                aktivitetskravService.createAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()

            verify(exactly = 1) {
                vurderingProducerMock.send(capture(producerRecordSlot))
            }

            val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, createdAktivitetskrav.personIdent.value)
            assertNull(savedAktivitetskrav?.previousAktivitetskravUuid)
            assertNull(aktivitetskravVurderingRecord.previousAktivitetskravUuid)
            assertEquals(createdAktivitetskrav.uuid, aktivitetskravVurderingRecord.uuid)
        }
    }

    @Nested
    @DisplayName("Vurder aktivitetskrav")
    inner class VurderAktivitetskrav {

        @Test
        fun `creates vurdering, varsel and pdf for unntak`() {
            var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            val fritekst = "En beskrivelse"
            val vurdering = AktivitetskravVurdering.create(
                AktivitetskravStatus.UNNTAK,
                UserConstants.VEILEDER_IDENT,
                fritekst,
                listOf(Arsak.MEDISINSKE_GRUNNER),
            )

            runBlocking {
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = vurdering,
                    document = generateDocumentComponentDTO(fritekst),
                    callId = "",
                )
            }

            aktivitetskrav =
                aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
            assertEquals(AktivitetskravStatus.UNNTAK, aktivitetskrav.status)
            val latestVurdering = aktivitetskrav.vurderinger.first()
            val varsel =
                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
            assertNotNull(varsel)
            assertEquals(VarselType.UNNTAK.name, varsel.type)
            assertTrue(varsel.document.isNotEmpty())
            assertNull(varsel.svarfrist)
            val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
            assertNotNull(varselPdf)
        }

        @Test
        fun `creates vurdering, varsel and pdf for oppfylt`() {
            var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            val fritekst = "En beskrivelse"
            val vurdering = AktivitetskravVurdering.create(
                AktivitetskravStatus.OPPFYLT,
                UserConstants.VEILEDER_IDENT,
                fritekst,
                listOf(Arsak.GRADERT),
            )

            runBlocking {
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = vurdering,
                    document = generateDocumentComponentDTO(fritekst),
                    callId = "",
                )
            }

            aktivitetskrav =
                aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
            assertEquals(AktivitetskravStatus.OPPFYLT, aktivitetskrav.status)
            val latestVurdering = aktivitetskrav.vurderinger.first()
            val varsel =
                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
            assertNotNull(varsel)
            assertEquals(VarselType.OPPFYLT.name, varsel.type)
            assertTrue(varsel.document.isNotEmpty())
            assertNull(varsel.svarfrist)
            val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
            assertNotNull(varselPdf)
        }

        @Test
        fun `creates vurdering, varsel and pdf for ikke-aktuell`() {
            var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            val fritekst = "En beskrivelse"
            val vurdering = AktivitetskravVurdering.create(
                AktivitetskravStatus.IKKE_AKTUELL,
                UserConstants.VEILEDER_IDENT,
                fritekst,
                listOf(Arsak.INNVILGET_VTA),
            )

            runBlocking {
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = vurdering,
                    document = generateDocumentComponentDTO(fritekst),
                    callId = "",
                )
            }

            aktivitetskrav =
                aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
            assertEquals(AktivitetskravStatus.IKKE_AKTUELL, aktivitetskrav.status)
            val latestVurdering = aktivitetskrav.vurderinger.first()
            val varsel =
                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
            assertNotNull(varsel)
            assertEquals(VarselType.IKKE_AKTUELL.name, varsel.type)
            assertTrue(varsel.document.isNotEmpty())
            assertNull(varsel.svarfrist)
            val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
            assertNotNull(varselPdf)
        }

        @Test
        fun `creates vurdering and no varsel for avvent`() {
            var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            val fritekst = "En beskrivelse"
            val vurdering = AktivitetskravVurdering.create(
                AktivitetskravStatus.AVVENT,
                UserConstants.VEILEDER_IDENT,
                fritekst,
                listOf(Arsak.DROFTES_MED_ROL),
            )

            runBlocking {
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = vurdering,
                    document = generateDocumentComponentDTO(fritekst),
                    callId = "",
                )
            }

            aktivitetskrav =
                aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
            assertEquals(AktivitetskravStatus.AVVENT, aktivitetskrav.status)
            val latestVurdering = aktivitetskrav.vurderinger.first()
            val varsel =
                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
            assertNull(varsel)
        }

        @Test
        fun `creates vurdering, journalpost and pdf for INNSTILLING_OM_STANS`() {
            var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            val fritekst = "En beskrivelse"
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.INNSTILLING_OM_STANS,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = fritekst,
                stansFom = LocalDate.now(),
                varsel = AktivitetskravVarsel.create(
                    type = VarselType.INNSTILLING_OM_STANS,
                    document = generateDocumentComponentDTO(fritekst),
                )
            )

            runBlocking {
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskrav,
                    aktivitetskravVurdering = vurdering,
                    document = generateDocumentComponentDTO(fritekst),
                    callId = "",
                )
            }

            aktivitetskrav =
                aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
            assertEquals(AktivitetskravStatus.INNSTILLING_OM_STANS, aktivitetskrav.status)
            val latestVurdering = aktivitetskrav.vurderinger.first()
            val varsel =
                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
            assertNotNull(varsel)
            assertEquals(VarselType.INNSTILLING_OM_STANS.name, varsel.type)
            assertTrue(varsel.document.isNotEmpty())
            assertNull(varsel.svarfrist)
            val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
            assertNotNull(varselPdf)
        }
    }

    @Nested
    @DisplayName("Get aktivitetskrav for persons")
    inner class GetAktivitetskravForPersons {

        @Test
        fun `gets aktivitetskrav with only the most recent vurdering for persons`() {
            val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
            aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
            createVurdering(AktivitetskravStatus.FORHANDSVARSEL, frist = LocalDate.now().plusDays(14))
                .also {
                    firstAktivitetskrav.vurder(it)
                    aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                }
            createVurdering(AktivitetskravStatus.IKKE_OPPFYLT)
                .also {
                    firstAktivitetskrav.vurder(it)
                    aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                }

            val aktivitetskravForPersons =
                aktivitetskravService.getAktivitetskravForPersons(listOf(ARBEIDSTAKER_PERSONIDENT))
            assertEquals(1, aktivitetskravForPersons[ARBEIDSTAKER_PERSONIDENT]?.vurderinger?.size)
            assertEquals(
                AktivitetskravStatus.IKKE_OPPFYLT,
                aktivitetskravForPersons[ARBEIDSTAKER_PERSONIDENT]?.vurderinger?.first()?.status
            )
        }

        @Test
        fun `gets the most recently created aktivitetskrav`() {
            val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                .copy(createdAt = OffsetDateTime.now().minusDays(5))
            aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
            val secondAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                .copy(createdAt = OffsetDateTime.now().minusDays(2))
            aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
            val thirdAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                .copy(createdAt = OffsetDateTime.now().minusDays(3))
            aktivitetskravRepository.createAktivitetskrav(thirdAktivitetskrav, UUID.randomUUID())

            val aktivitetskravForPersons =
                aktivitetskravService.getAktivitetskravForPersons(listOf(ARBEIDSTAKER_PERSONIDENT))
            assertEquals(secondAktivitetskrav.uuid, aktivitetskravForPersons[ARBEIDSTAKER_PERSONIDENT]?.uuid)
        }
    }
}
