package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravRepository
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class AktivitetskravAutomatiskOppfyltCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
        val aktivitetskravVurderingProducer =
            AktivitetskravVurderingProducer(kafkaProducerAktivitetskravVurdering = kafkaProducer)

        val aktivitetskravService = AktivitetskravService(
            aktivitetskravRepository = AktivitetskravRepository(database),
            database = database,
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
            arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
        )

        val aktivitetskravAutomatiskOppfyltCronjob = AktivitetskravAutomatiskOppfyltCronjob(
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

        describe(AktivitetskravAutomatiskOppfyltCronjob::class.java.simpleName) {
            val aktivitetskrav1 = createAktivitetskravNy(
                tilfelleStart = LocalDate.now().minusWeeks(10),
            )
            val aktivitetskrav2 = createAktivitetskravNy(
                tilfelleStart = LocalDate.now().minusWeeks(50),
            )
            val automatiskOppfylt = createAktivitetskravAutomatiskOppfylt(
                tilfelleStart = LocalDate.now().minusWeeks(50),
            )

            it("Setter aktivitetskrav med uuid til AUTOMATISK_OPPFYLT") {
                database.createAktivitetskrav(aktivitetskrav1, aktivitetskrav2)

                runBlocking {
                    val aktivitetskravUuids = listOf(aktivitetskrav1.uuid)
                    val result =
                        aktivitetskravAutomatiskOppfyltCronjob.runJob(aktivitetskravUuids = aktivitetskravUuids)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val pAktivitetskravList =
                    database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val automatiskOppfylteAktivitetskrav =
                    pAktivitetskravList.filter { it.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT.name }
                automatiskOppfylteAktivitetskrav.size shouldBeEqualTo 1
                val automatiskOppfyltAktivitetskrav = automatiskOppfylteAktivitetskrav.first()
                automatiskOppfyltAktivitetskrav.uuid shouldBeEqualTo aktivitetskrav1.uuid

                val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo automatiskOppfyltAktivitetskrav.status
            }
            it("Setter bare aktivitetskrav NY til AUTOMATISK_OPPFYLT") {
                database.createAktivitetskrav(automatiskOppfylt)

                runBlocking {
                    val aktivitetskravUuids = listOf(automatiskOppfylt.uuid)
                    val result =
                        aktivitetskravAutomatiskOppfyltCronjob.runJob(aktivitetskravUuids = aktivitetskravUuids)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }
            }
            it("Setter ingen aktivitetskrav til AUTOMATISK_OPPFYLT når tom liste med uuider") {
                database.createAktivitetskrav(aktivitetskrav1, aktivitetskrav2)

                runBlocking {
                    val result =
                        aktivitetskravAutomatiskOppfyltCronjob.runJob(aktivitetskravUuids = emptyList())

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val pAktivitetskravList =
                    database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                pAktivitetskravList.any { it.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT.name } shouldBeEqualTo false
            }
        }
    }
})
