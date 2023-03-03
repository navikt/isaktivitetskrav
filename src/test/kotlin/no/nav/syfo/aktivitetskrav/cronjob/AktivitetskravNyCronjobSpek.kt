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
import no.nav.syfo.testhelper.generator.createAktivitetskravAutomatiskOppfylt
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class AktivitetskravNyCronjobSpek : Spek({
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

        val aktivitetskravNyCronjob = AktivitetskravNyCronjob(
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

        describe(AktivitetskravNyCronjob::class.java.simpleName) {
            val aktivitetskrav1 = createAktivitetskravAutomatiskOppfylt(
                tilfelleStart = LocalDate.now().minusWeeks(10),
            )
            val aktivitetskrav2 = createAktivitetskravAutomatiskOppfylt(
                tilfelleStart = LocalDate.now().minusWeeks(50),
            )
            val aktivitetskravNy = createAktivitetskravNy(
                tilfelleStart = LocalDate.now().minusWeeks(50),
            )

            it("Setter aktivitetskrav med uuid til NY") {
                database.createAktivitetskrav(aktivitetskrav1, aktivitetskrav2)

                runBlocking {
                    val aktivitetskravUuids = listOf(aktivitetskrav1.uuid)
                    val result =
                        aktivitetskravNyCronjob.runJob(aktivitetskravUuids = aktivitetskravUuids)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) {
                    kafkaProducer.send(capture(producerRecordSlot))
                }

                val pAktivitetskravList =
                    database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val aktivitetskravNyList =
                    pAktivitetskravList.filter { it.status == AktivitetskravStatus.NY.name }
                aktivitetskravNyList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravNyList.first()
                aktivitetskrav.uuid shouldBeEqualTo aktivitetskrav1.uuid

                val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status
            }
            it("Setter bare aktivitetskrav AUTOMATISK_OPPFYLT til NY") {
                database.createAktivitetskrav(aktivitetskravNy)

                runBlocking {
                    val aktivitetskravUuids = listOf(aktivitetskravNy.uuid)
                    val result =
                        aktivitetskravNyCronjob.runJob(aktivitetskravUuids = aktivitetskravUuids)

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }
            }
            it("Setter ingen aktivitetskrav til NY når tom liste med uuider") {
                database.createAktivitetskrav(aktivitetskrav1, aktivitetskrav2)

                runBlocking {
                    val result =
                        aktivitetskravNyCronjob.runJob(aktivitetskravUuids = emptyList())

                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    kafkaProducer.send(any())
                }

                val pAktivitetskravList =
                    database.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                pAktivitetskravList.any { it.status == AktivitetskravStatus.NY.name } shouldBeEqualTo false
            }
        }
    }
})