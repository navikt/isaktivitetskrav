package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.infrastructure.database.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
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

class AktivitetskravNyCronjobSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
        val aktivitetskravVurderingProducer =
            AktivitetskravVurderingProducer(producer = kafkaProducer)
        val aktivitetskravRepository = AktivitetskravRepository(database)
        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
        val aktivitetskravService = AktivitetskravService(
            aktivitetskravRepository = aktivitetskravRepository,
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
            arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
            varselPdfService = VarselPdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            )
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
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav1)
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav2)

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
                    aktivitetskravRepository.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val aktivitetskravNyList =
                    pAktivitetskravList.filter { it.status == AktivitetskravStatus.NY }
                aktivitetskravNyList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravNyList.first()
                aktivitetskrav.uuid shouldBeEqualTo aktivitetskrav1.uuid

                val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
            }
            it("Setter bare aktivitetskrav AUTOMATISK_OPPFYLT til NY") {
                aktivitetskravRepository.createAktivitetskrav(aktivitetskravNy)

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
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav1)
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav2)

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
                    aktivitetskravRepository.getAktivitetskrav(personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                pAktivitetskravList.any { it.status == AktivitetskravStatus.NY } shouldBeEqualTo false
            }
        }
    }
})
