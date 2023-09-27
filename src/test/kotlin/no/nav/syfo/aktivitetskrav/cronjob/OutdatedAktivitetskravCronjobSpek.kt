package no.nav.syfo.aktivitetskrav.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
import no.nav.syfo.testhelper.dropData
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class OutdatedAktivitetskravCronjobSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()

    val arenaCutoff = externalMockEnvironment.environment.arenaCutoff
    val outdatedCutoff = externalMockEnvironment.environment.outdatedCutoff

    val aktivitetskravService = AktivitetskravService(
        database = database,
        aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(kafkaProducerAktivitetskravVurdering = kafkaProducer),
        arenaCutoff = arenaCutoff,
        aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database),
        pdfGenClient = mockk<PdfGenClient>(),
        pdlClient = externalMockEnvironment.pdlClient,
        krrClient = externalMockEnvironment.krrClient,
    )
    val outdatedAktivitetskravCronjob = OutdatedAktivitetskravCronjob(
        outdatedCutoff = outdatedCutoff,
        aktivitetskravService = aktivitetskravService,
    )

    beforeEachTest {
        database.dropData()

        clearMocks(kafkaProducer)
        coEvery {
            kafkaProducer.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    fun createNyttAktivitetskrav(stoppunktAt: LocalDate): Aktivitetskrav = Aktivitetskrav.ny(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = stoppunktAt.minusWeeks(8),
    )

    describe("${OutdatedAktivitetskravCronjob::class.java.simpleName}: run job") {
        it("Lukker ingen aktivitetskrav når det ikke finnes aktivitetskrav") {
            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }
        }
        it("Lukker nytt aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff") {
            val aktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = arenaCutoff.plusDays(1)
            )
            database.createAktivitetskrav(aktivitetskrav)

            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 1
            }

            val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
            verify(exactly = 1) {
                kafkaProducer.send(capture(producerRecordSlot))
            }

            val pAktivitetskravList = database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskravList.size shouldBeEqualTo 1
            val lukketAktivitetskrav = pAktivitetskravList.first()
            lukketAktivitetskrav.uuid shouldBeEqualTo aktivitetskrav.uuid

            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
            kafkaAktivitetskravVurdering.status shouldBeEqualTo lukketAktivitetskrav.status
        }
        it("Lukker ikke vurdert aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff") {
            val aktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = arenaCutoff.plusDays(1)
            )
                .vurder(
                    AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.UNNTAK,
                        createdBy = UserConstants.VEILEDER_IDENT,
                        beskrivelse = null,
                        listOf(VurderingArsak.MEDISINSKE_GRUNNER),
                    )
                )
            database.createAktivitetskrav(aktivitetskrav)

            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val pAktivitetskravList = database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET.name } shouldBeEqualTo false
        }
        it("Lukker ikke nytt aktivitetskrav hvor stoppunkt er før arena-cutoff") {
            val aktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = arenaCutoff.minusDays(1)
            )
            database.createAktivitetskrav(aktivitetskrav)

            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val pAktivitetskravList = database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET.name } shouldBeEqualTo false
        }
        it("Lukker ikke nytt aktivitetskrav hvor stoppunkt er etter arena-cutoff og etter outdated-cutoff") {
            val aktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = outdatedCutoff.plusDays(1)
            )
            database.createAktivitetskrav(aktivitetskrav)

            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val pAktivitetskravList = database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET.name } shouldBeEqualTo false
        }
        it("Lukker ikke nytt aktivitetskrav hvor stoppunkt er etter arena-cutoff og før outdated-cutoff hvis det finnes aktivitetskrav for samme person hvor stoppunkt er etter outdated-cutoff") {
            val aktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = arenaCutoff.plusDays(1)
            )
            val nyttAktivitetskrav = createNyttAktivitetskrav(
                stoppunktAt = outdatedCutoff.plusDays(1)
            )
            database.createAktivitetskrav(aktivitetskrav, nyttAktivitetskrav)

            runBlocking {
                val result = outdatedAktivitetskravCronjob.runJob()

                result.failed shouldBeEqualTo 0
                result.updated shouldBeEqualTo 0
            }

            verify(exactly = 0) {
                kafkaProducer.send(any())
            }

            val pAktivitetskravList = database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
            pAktivitetskravList.any { it.status == AktivitetskravStatus.LUKKET.name } shouldBeEqualTo false
        }
    }
})
