package no.nav.syfo.aktivitetskrav.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.aktivitetskrav.api.Arsak
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.LocalDate
import java.util.concurrent.Future

class OutdatedAktivitetskravCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()

    private val arenaCutoff = externalMockEnvironment.environment.arenaCutoff
    private val outdatedCutoff = externalMockEnvironment.environment.outdatedCutoff
    private val aktivitetskravRepository = AktivitetskravRepository(database)
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = aktivitetskravRepository,
        aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(producer = kafkaProducer),
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        arenaCutoff = arenaCutoff,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
    )
    private val outdatedAktivitetskravCronjob = OutdatedAktivitetskravCronjob(
        outdatedCutoff = outdatedCutoff,
        aktivitetskravService = aktivitetskravService,
    )

    @BeforeEach
    fun setUp() {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    private fun createNyttAktivitetskrav(stoppunktAt: LocalDate): Aktivitetskrav = Aktivitetskrav.create(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        oppfolgingstilfelleStart = stoppunktAt.minusWeeks(8).plusDays(1),
    )

    @Test
    fun `Lukker ingen aktivitetskrav når det ikke finnes aktivitetskrav`() {
        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
    }

    @Test
    fun `Lukker nytt aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff`() {
        val aktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = arenaCutoff.plusDays(1)
        )
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()

            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
        verify(exactly = 1) {
            kafkaProducer.send(capture(producerRecordSlot))
        }

        val pAktivitetskravList = aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertEquals(1, pAktivitetskravList.size)
        val lukketAktivitetskrav = pAktivitetskravList.first()
        assertEquals(aktivitetskrav.uuid, lukketAktivitetskrav.uuid)

        val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
        assertEquals(lukketAktivitetskrav.status.name, kafkaAktivitetskravVurdering.status)
    }

    @Test
    fun `Lukker ikke vurdert aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff`() {
        val aktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = arenaCutoff.plusDays(1)
        ).vurder(
            AktivitetskravVurdering.create(
                status = AktivitetskravStatus.UNNTAK,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = null,
                listOf(Arsak.MEDISINSKE_GRUNNER),
            )
        )
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val pAktivitetskravList = aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertFalse(pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET })
    }

    @Test
    fun `Lukker ikke aktivitetskrav med stoppunkt før arena-cutoff`() {
        val aktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = arenaCutoff.minusDays(1)
        )
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val pAktivitetskravList = aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertFalse(pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET })
    }

    @Test
    fun `Lukker ikke aktivitetskrav med stoppunkt etter outdated-cutoff`() {
        val aktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = outdatedCutoff.plusDays(1)
        )
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val pAktivitetskravList = aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertFalse(pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET })
    }

    @Test
    fun `Lukker ikke nytt aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff hvis det finnes aktivitetskrav for samme person hvor stoppunkt er etter outdated-cutoff`() {
        val aktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = arenaCutoff.plusDays(1)
        )
        val nyttAktivitetskrav = createNyttAktivitetskrav(
            stoppunktAt = outdatedCutoff.plusDays(1)
        )
        aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
        aktivitetskravRepository.createAktivitetskrav(nyttAktivitetskrav)

        runBlocking {
            val result = outdatedAktivitetskravCronjob.runJob()

            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }

        verify(exactly = 0) {
            kafkaProducer.send(any())
        }

        val pAktivitetskravList = aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
        assertFalse(pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET })
    }
}
