package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.createAktivitetskrav
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class DeleteVurderingCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
        val aktivitetskravVurderingProducer =
            AktivitetskravVurderingProducer(kafkaProducerAktivitetskravVurdering = kafkaProducer)

        val aktivitetskravService = AktivitetskravService(
            database = database,
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
            arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        )

        val deleteVurderingCronjob = DeleteVurderingCronjob(
            database = database,
            aktivitetskravService = aktivitetskravService,
        )

        beforeEachTest {
            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        describe(DeleteVurderingCronjob::class.java.simpleName) {
            val aktivitetskravNy = createAktivitetskravNy(
                tilfelleStart = LocalDate.now().minusWeeks(50),
            )

            it("Sletter ingen vurdering når tom liste med uuider") {
                database.createAktivitetskrav(aktivitetskravNy)
                val unntakVurdering = createUnntakVurdering()
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskravNy,
                    aktivitetskravVurdering = unntakVurdering
                )

                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                runBlocking {
                    val result = deleteVurderingCronjob.runJob(emptyList())

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                val aktivitetskrav = aktivitetskravService.getAktivitetskrav(aktivitetskravNy.uuid)!!
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.UNNTAK
                aktivitetskrav.vurderinger.size shouldBeEqualTo 1
                aktivitetskrav.vurderinger.first().uuid shouldBeEqualTo unntakVurdering.uuid

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 0) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }
            }

            it("Sletter angitt vurdering og oppdaterer aktivitetskrav-status til NY når ingen andre vurderinger eksisterer") {
                database.createAktivitetskrav(aktivitetskravNy)
                val unntakVurdering = createUnntakVurdering()
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskravNy,
                    aktivitetskravVurdering = unntakVurdering
                )

                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                runBlocking {
                    val result = deleteVurderingCronjob.runJob(listOf(unntakVurdering.uuid))

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val aktivitetskrav = aktivitetskravService.getAktivitetskrav(aktivitetskravNy.uuid)!!
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                aktivitetskrav.vurderinger.shouldBeEmpty()

                val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
            }

            it("Sletter angitt vurdering og oppdaterer aktivitetskrav-status til forrige vurdering") {
                database.createAktivitetskrav(aktivitetskravNy)
                val avventVurdering = createAvventVurdering()
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskravNy,
                    aktivitetskravVurdering = avventVurdering
                )
                val unntakVurdering = createUnntakVurdering()
                aktivitetskravService.vurderAktivitetskrav(
                    aktivitetskrav = aktivitetskravNy,
                    aktivitetskravVurdering = unntakVurdering
                )

                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                runBlocking {
                    val result = deleteVurderingCronjob.runJob(listOf(unntakVurdering.uuid))

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val aktivitetskrav = aktivitetskravService.getAktivitetskrav(aktivitetskravNy.uuid)!!
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                aktivitetskrav.vurderinger.size shouldBeEqualTo 1
                aktivitetskrav.vurderinger.first().uuid shouldBeEqualTo avventVurdering.uuid

                val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
            }
        }
    }
})
