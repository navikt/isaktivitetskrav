package no.nav.syfo.oppfolgingstilfelle.kafka

import io.mockk.*
import no.nav.syfo.application.AktivitetskravService
import no.nav.syfo.application.VarselPdfService
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVurderingRecord
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePerson
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.KafkaOppfolgingstilfellePersonService
import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.OLD_TILFELLE_CUTOFF
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.LocalDate
import java.util.*
import java.util.concurrent.Future

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val eightWeeksAgo = LocalDate.now().minusWeeks(8)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)
private val yearAgo = LocalDate.now().minusYears(1)

class KafkaOppfolgingstilfellePersonServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val arenaCutoff = externalMockEnvironment.environment.arenaCutoff
    private val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    private val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
        producer = kafkaProducer,
    )
    private val aktivitetskravRepository = AktivitetskravRepository(database)
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = aktivitetskravRepository,
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        arenaCutoff = arenaCutoff,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
    )
    private val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
        database = database,
        aktivitetskravService = aktivitetskravService,
        arenaCutoff = arenaCutoff,
    )

    private val kafkaOppfolgingstilfellePersonTopicPartition = createKafkaOppfolgingstilfellePersonTopicPartition()
    private val mockKafkaConsumerOppfolgingstilfellePerson = mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

    private val kafkaOppfolgingstilfelleNineWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = false,
    )
    private val kafkaOppfolgingstilfelleNineWeeksGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = true,
    )
    private val kafkaOppfolgingstilfellePersonSevenWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = sevenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = false,
    )
    private val kafkaOppfolgingstilfellePersonWithDodsdato = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = tenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = false,
        dodsdato = LocalDate.now(),
    )
    private val kafkaOppfolgingstilfellePersonSevenWeeksGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = sevenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = true,
    )
    private val kafkaOppfolgingstilfelleTenWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = tenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = false,
    )
    private val kafkaOppfolgingstilfelleTenWeeksGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = tenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = true,
    )
    private val kafkaOppfolgingstilfelleinFutureNineWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = LocalDate.now().plusDays(1),
        tilfelleEnd = LocalDate.now().plusWeeks(9),
        gradert = false,
    )

    @BeforeEach
    fun setUp() {
        database.dropData()
        clearMocks(kafkaProducer, mockKafkaConsumerOppfolgingstilfellePerson)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
        every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
    }

    private fun mockKafkaConsumerOppfolgingstilfellePerson(
        vararg kafkaOppfolgingstilfellePerson: KafkaOppfolgingstilfellePerson,
    ) {
        val consumerRecords =
            kafkaOppfolgingstilfellePerson.map { createKafkaOppfolgingstilfellePersonConsumerRecord(it) }
        every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(kafkaOppfolgingstilfellePersonTopicPartition to consumerRecords)
        )
    }

    @Nested
    @DisplayName("no Aktivitetskrav exists for oppfolgingstilfelle")
    inner class NoAktivitetskravExistsForOppfolgingstilfelle {

        @Test
        fun `creates Aktivitetskrav(NY) for oppfolgingstilfelle lasting 9 weeks, not gradert`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfelleNineWeeksNotGradert
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, aktivitetskrav.status)
            assertEquals(nineWeeksAgo.plusWeeks(8).minusDays(1), aktivitetskrav.stoppunktAt)
            assertEquals(
                kafkaOppfolgingstilfelleNineWeeksNotGradert.referanseTilfelleBitUuid,
                aktivitetskrav.referanseTilfelleBitUuid.toString()
            )

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(aktivitetskrav.personIdent.value, kafkaAktivitetskravVurdering.personIdent)
            assertEquals(aktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(aktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }

        @Test
        fun `creates Aktivitetskrav(AUTOMATISK_OPPFYLT) for oppfolgingstilfelle lasting 9 weeks, gradert`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfelleNineWeeksGradert
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.AUTOMATISK_OPPFYLT, aktivitetskrav.status)
            assertEquals(nineWeeksAgo.plusWeeks(8).minusDays(1), aktivitetskrav.stoppunktAt)
            assertEquals(
                kafkaOppfolgingstilfelleNineWeeksGradert.referanseTilfelleBitUuid,
                aktivitetskrav.referanseTilfelleBitUuid.toString()
            )

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(aktivitetskrav.personIdent.value, kafkaAktivitetskravVurdering.personIdent)
            assertEquals(aktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(aktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }

        @Test
        fun `creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, not gradert`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfellePersonSevenWeeksNotGradert
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())

            verify(exactly = 0) { kafkaProducer.send(any()) }
        }

        @Test
        fun `creates no Aktivitetskrav for person with dodsdato`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfellePersonWithDodsdato
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())

            verify(exactly = 0) { kafkaProducer.send(any()) }
        }

        @Test
        fun `creates no Aktivitetskrav for oppfolgingstilfelle starting before OLD_TILFELLE_CUTOFF`() {
            val oldKafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = OLD_TILFELLE_CUTOFF.minusDays(1),
                tilfelleEnd = OLD_TILFELLE_CUTOFF.plusWeeks(9),
                gradert = false,
            )
            mockKafkaConsumerOppfolgingstilfellePerson(oldKafkaOppfolgingstilfellePerson)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())
        }

        @Test
        fun `creates no Aktivitetskrav for oppfolgingstilfelle ending before arenaCutoff date`() {
            val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = arenaCutoff.minusWeeks(12),
                tilfelleEnd = arenaCutoff.minusDays(1),
                gradert = false,
            )

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfellePerson)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())
        }

        @Test
        fun `creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, gradert`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfellePersonSevenWeeksGradert
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList = aktivitetskravRepository.getAktivitetskrav(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
            )

            assertTrue(aktivitetskravList.isEmpty())
        }

        @Test
        fun `creates Aktivitetskrav(NY) once for oppfolgingstilfelle polled twice lasting 8 weeks, not gradert`() {
            val kafkaOppfolgingstilfelleEightWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = eightWeeksAgo,
                tilfelleEnd = LocalDate.now(),
                gradert = false,
            )
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfelleEightWeeksNotGradert,
                kafkaOppfolgingstilfelleEightWeeksNotGradert,
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
            )

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList = aktivitetskravRepository.getAktivitetskrav(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
            )

            assertEquals(1, aktivitetskravList.size)
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav(NY) exists for oppfolgingstilfelle")
    inner class AktivitetskravNyExistsForOppfolgingstilfelle {
        private val nyAktivitetskrav = createAktivitetskravNy(
            tilfelleStart = nineWeeksAgo,
        )

        @Test
        fun `does not update Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle-start unchanged`() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

            var aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            var aktivitetskrav = aktivitetskravList.first()
            val updatedAt = aktivitetskrav.updatedAt

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            aktivitetskrav = aktivitetskravList.first()
            assertEquals(updatedAt, aktivitetskrav.updatedAt)
        }

        @Test
        fun `updates Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle gradert and start changed`() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleTenWeeksGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, latestAktivitetskrav.status)
            assertEquals(tenWeeksAgo.plusWeeks(8).minusDays(1), latestAktivitetskrav.stoppunktAt)
            assertEquals(nyAktivitetskrav.uuid, latestAktivitetskrav.uuid)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(latestAktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }

        @Test
        fun `updates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert and start changed`() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleTenWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, latestAktivitetskrav.status)
            assertEquals(tenWeeksAgo.plusWeeks(8).minusDays(1), latestAktivitetskrav.stoppunktAt)
            assertEquals(nyAktivitetskrav.uuid, latestAktivitetskrav.uuid)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(latestAktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav(AUTOMATISK_OPPFYLT) exists for oppfolgingstilfelle")
    inner class AktivitetskravAutomatiskOppfyltExistsForOppfolgingstilfelle {
        private val automatiskOppfyltAktivitetskrav =
            createAktivitetskravAutomatiskOppfylt(tilfelleStart = nineWeeksAgo)

        @Test
        fun `creates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert`() {
            aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(2, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, latestAktivitetskrav.status)
            assertNotEquals(automatiskOppfyltAktivitetskrav.uuid, latestAktivitetskrav.uuid)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(AktivitetskravStatus.NY.name, kafkaAktivitetskravVurdering.status)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }

        @Test
        fun `updates Aktivitetskrav(AUTOMATISK_OPPFYLT) if oppfolgingstilfelle gradert and start changed`() {
            aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleTenWeeksGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.AUTOMATISK_OPPFYLT, latestAktivitetskrav.status)
            assertEquals(tenWeeksAgo.plusWeeks(8).minusDays(1), latestAktivitetskrav.stoppunktAt)
            assertEquals(automatiskOppfyltAktivitetskrav.uuid, latestAktivitetskrav.uuid)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(latestAktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
            assertNull(kafkaAktivitetskravVurdering.beskrivelse)
            assertNull(kafkaAktivitetskravVurdering.updatedBy)
            assertNull(kafkaAktivitetskravVurdering.sistVurdert)
        }

        @Test
        fun `does not update Aktivitetskrav(AUTOMATISK_OPPFYLT) stoppunkt_at if oppfolgingstilfelle-start unchanged`() {
            aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

            var aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            var aktivitetskrav = aktivitetskravList.first()
            val updatedAt = aktivitetskrav.updatedAt

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
            )

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            aktivitetskrav = aktivitetskravList.first()
            assertEquals(updatedAt, aktivitetskrav.updatedAt)
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav(UNNTAK/OPPFYLT/AVVENT/IKKE_OPPFYLT/IKKE_AKTUELL) exists for oppfolgingstilfelle")
    inner class AktivitetskravVurdertExistsForOppfolgingstilfelle {

        @ParameterizedTest
        @MethodSource("no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonServiceTest#testcasesNineWeeksAgo")
        fun `updates Aktivitetskrav stoppunkt_at if oppfolgingstilfelle not gradert and start changed`(aktivitetskrav: Aktivitetskrav) {
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleTenWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(aktivitetskrav.status, latestAktivitetskrav.status)
            assertEquals(aktivitetskrav.uuid, latestAktivitetskrav.uuid)
            assertNotEquals(aktivitetskrav.stoppunktAt, latestAktivitetskrav.stoppunktAt)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(latestAktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
        }

        @ParameterizedTest
        @MethodSource("no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonServiceTest#testcasesNineWeeksAgo")
        fun `updates Aktivitetskrav stoppunkt_at if oppfolgingstilfelle gradert and start changed`(aktivitetskrav: Aktivitetskrav) {
            val aktivitetskravStatus = aktivitetskrav.status
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleTenWeeksGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val latestAktivitetskrav = aktivitetskravList.first()
            assertEquals(aktivitetskravStatus, latestAktivitetskrav.status)
            assertEquals(aktivitetskrav.uuid, latestAktivitetskrav.uuid)
            assertNotEquals(aktivitetskrav.stoppunktAt, latestAktivitetskrav.stoppunktAt)

            val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
            val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
            assertEquals(latestAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
            assertEquals(latestAktivitetskrav.stoppunktAt, kafkaAktivitetskravVurdering.stoppunktAt)
        }

        @ParameterizedTest
        @MethodSource("no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonServiceTest#testcasesNineWeeksAgo")
        fun `does not update Aktivitetskrav stoppunkt_at if oppfolgingstilfelle-start unchanged`(aktivitetskrav: Aktivitetskrav) {
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

            var aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            var pAktivitetskrav = aktivitetskravList.first()
            val updatedAt = pAktivitetskrav.updatedAt

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskrav = aktivitetskravList.first()
            assertEquals(updatedAt, pAktivitetskrav.updatedAt)
        }
    }

    @Nested
    @DisplayName("Oppfolgingstilfelle start in future")
    inner class OppfolgingstilfelleStartInFuture {
        @Test
        fun `creates no Aktivitetskrav for future oppfolgingstilfelle lasting 9 weeks, not gradert`() {
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleinFutureNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav(NY) exists for earlier oppfolgingstilfelle")
    inner class AktivitetskravNyExistsForEarlierOppfolgingstilfelle {
        private val nyAktivitetskrav = createAktivitetskravNy(tilfelleStart = yearAgo)

        @Test
        fun `updates aktivitetskrav for tidligere oppfolgingstilfelle til AUTOMATISK_OPPFYLT når nyeste oppfolgingstilfelle varer i 9 uker (ikke gradert)`() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(2, aktivitetskravList.size)

            val aktivitetskravTidligereOppfolgingstilfelle = aktivitetskravList.last()
            assertEquals(AktivitetskravStatus.AUTOMATISK_OPPFYLT, aktivitetskravTidligereOppfolgingstilfelle.status)
            assertEquals(nyAktivitetskrav.uuid, aktivitetskravTidligereOppfolgingstilfelle.uuid)

            val kafkaRecordSlot1 = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            val kafkaRecordSlot2 = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verifyOrder {
                kafkaProducer.send(capture(kafkaRecordSlot1))
                kafkaProducer.send(capture(kafkaRecordSlot2))
            }

            assertEquals(kafkaRecordSlot1.captured.value().status, aktivitetskravTidligereOppfolgingstilfelle.status.name)
        }

        @Test
        fun `do not update aktivitetskrav for tidligere oppfolgingstilfelle når nyeste oppfolgingstilfelle varer i 7 uker`() {
            aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfellePersonSevenWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskravTidligereOppfolgingstilfelle = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, aktivitetskravTidligereOppfolgingstilfelle.status)
            assertEquals(nyAktivitetskrav.uuid, aktivitetskravTidligereOppfolgingstilfelle.uuid)
        }
    }

    @Nested
    @DisplayName("Aktivitetskrav(AUTOMATISK_OPPFYLT/UNNTAK/OPPFYLT/AVVENT/IKKE_OPPFYLT/IKKE_AKTUELL) exists for tidligere oppfolgingstilfelle")
    inner class AktivitetskravVurdertExistsForTidligereOppfolgingstilfelle {

        @ParameterizedTest
        @MethodSource("no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonServiceTest#testcasesOneYearAgo")
        fun `do not update aktivitetskrav for tidligere oppfolgingstilfelle når nyeste oppfolgingstilfelle varer i 9 uker`(aktivitetskrav: Aktivitetskrav) {
            val aktivitetskravStatus = aktivitetskrav.status
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelleNineWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(2, aktivitetskravList.size)
            val aktivitetskravTidligereOppfolgingstilfelle = aktivitetskravList.last()
            assertEquals(aktivitetskravStatus, aktivitetskravTidligereOppfolgingstilfelle.status)
            assertEquals(aktivitetskrav.uuid, aktivitetskravTidligereOppfolgingstilfelle.uuid)
        }

        @ParameterizedTest
        @MethodSource("no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePersonServiceTest#testcasesOneYearAgo")
        fun `do not update aktivitetskrav for tidligere oppfolgingstilfelle når nyeste oppfolgingstilfelle varer i 7 uker`(aktivitetskrav: Aktivitetskrav) {
            val aktivitetskravStatus = aktivitetskrav.status
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfellePersonSevenWeeksNotGradert)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }
            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskravTidligereOppfolgingstilfelle = aktivitetskravList.first()
            assertEquals(aktivitetskravStatus, aktivitetskravTidligereOppfolgingstilfelle.status)
            assertEquals(aktivitetskrav.uuid, aktivitetskravTidligereOppfolgingstilfelle.uuid)
        }
    }

    @Nested
    @DisplayName("Oppfolgingstilfelle is exactly 56 days")
    inner class OppfolgingstilfelleExactly56Days {
        private val startDate = LocalDate.now().minusDays(5)
        private val endDate = LocalDate.now().plusDays(50)
        private val kafkaOppfolgingstilfelle56Days = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = startDate,
            tilfelleEnd = endDate,
            gradert = false,
        )
        private val secondKafkaOppfolgingstilfelle56Days = kafkaOppfolgingstilfelle56Days.copy(
            referanseTilfelleBitInntruffet = kafkaOppfolgingstilfelle56Days.referanseTilfelleBitInntruffet.plusDays(
                1
            ),
            referanseTilfelleBitUuid = UUID.randomUUID().toString(),
        )

        @Test
        fun `creates Aktivitetskrav(NY) for oppfolgingstilfelle lasting exactly 56 days`() {
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaOppfolgingstilfelle56Days)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, aktivitetskrav.status)
            assertEquals(endDate, aktivitetskrav.stoppunktAt)
            assertEquals(kafkaOppfolgingstilfelle56Days.referanseTilfelleBitUuid, aktivitetskrav.referanseTilfelleBitUuid.toString())
        }

        @Test
        fun `doesn't create Aktivitetskrav for second oppfolgingstilfelle lasting exactly 56 days`() {
            mockKafkaConsumerOppfolgingstilfellePerson(
                kafkaOppfolgingstilfelle56Days,
                secondKafkaOppfolgingstilfelle56Days,
            )
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertEquals(1, aktivitetskravList.size)
            val aktivitetskrav = aktivitetskravList.first()
            assertEquals(AktivitetskravStatus.NY, aktivitetskrav.status)
            assertEquals(endDate, aktivitetskrav.stoppunktAt)
            assertEquals(kafkaOppfolgingstilfelle56Days.referanseTilfelleBitUuid, aktivitetskrav.referanseTilfelleBitUuid.toString())
        }
    }

    @Nested
    @DisplayName("Inactive oppfolgingstilfelle")
    inner class InactiveOppfolgingstilfelle {
        private val startDate = LocalDate.now().minusDays(90)
        private val endDate = LocalDate.now().minusDays(31)
        private val kafkaInactiveOppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = startDate,
            tilfelleEnd = endDate,
            gradert = false,
        )

        @Test
        fun `does not create Aktivitetskrav(NY) for oppfolgingstilfelle ending more than 30 days ago`() {
            mockKafkaConsumerOppfolgingstilfellePerson(kafkaInactiveOppfolgingstilfelle)
            kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson)

            verify(exactly = 1) {
                mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
            }

            val aktivitetskravList =
                aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

            assertTrue(aktivitetskravList.isEmpty())
        }
    }

    companion object {
        @JvmStatic
        private fun testcasesNineWeeksAgo(): List<Aktivitetskrav> {
            val nyAktivitetskrav = createAktivitetskravNy(tilfelleStart = nineWeeksAgo)
            return listOf(
                createAktivitetskravUnntak(nyAktivitetskrav),
                createAktivitetskravOppfylt(nyAktivitetskrav),
                createAktivitetskravAvvent(nyAktivitetskrav),
                createAktivitetskravIkkeOppfylt(nyAktivitetskrav),
                createAktivitetskravIkkeAktuell(nyAktivitetskrav)
            )
        }

        @JvmStatic
        private fun testcasesOneYearAgo(): List<Aktivitetskrav> {
            val nyAktivitetskrav = createAktivitetskravNy(tilfelleStart = yearAgo)
            return listOf(
                createAktivitetskravUnntak(nyAktivitetskrav),
                createAktivitetskravOppfylt(nyAktivitetskrav),
                createAktivitetskravAvvent(nyAktivitetskrav),
                createAktivitetskravIkkeOppfylt(nyAktivitetskrav),
                createAktivitetskravIkkeAktuell(nyAktivitetskrav)
            )
        }
    }
}
