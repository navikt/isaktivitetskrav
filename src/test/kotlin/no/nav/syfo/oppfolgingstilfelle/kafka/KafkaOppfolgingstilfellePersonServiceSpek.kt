package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.aktivitetskrav.database.getAktivitetskravVurderinger
import no.nav.syfo.aktivitetskrav.database.toAktivitetskravVurderinger
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurderingStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.concurrent.Future

private const val DAYS_IN_WEEK = 7

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        beforeEachTest {
            database.dropData()
        }

        val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
        val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
            kafkaProducerAktivitetskravVurdering = kafkaProducer,
        )
        val aktivitetskravVurderingService = AktivitetskravVurderingService(
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        )
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravVurderingService = aktivitetskravVurderingService,
        )

        val kafkaOppfolgingstilfellePersonTopicPartition = createKafkaOppfolgingstilfellePersonTopicPartition()
        val mockKafkaConsumerOppfolgingstilfellePerson = mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {

            beforeEachTest {
                clearMocks(kafkaProducer, mockKafkaConsumerOppfolgingstilfellePerson)
                coEvery {
                    kafkaProducer.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
            }

            it("creates AktivitetskravVurdering(NY) for oppfolgingstilfelle lasting 9 weeks, not gradert") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = DAYS_IN_WEEK * 9L,
                    gradert = false,
                )
                val kafkaOppfolgingstilfellePersonRecord =
                    createKafkaOppfolgingstilfellePersonConsumerRecord(kafkaOppfolgingstilfellePerson)
                val consumerRecords = ConsumerRecords(
                    mapOf(
                        kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonRecord
                        )
                    )
                )
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns consumerRecords

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravVurderinger = database.getAktivitetskravVurderinger(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                ).toAktivitetskravVurderinger()

                aktivitetskravVurderinger.size shouldBeEqualTo 1
                val aktivitetskravVurdering = aktivitetskravVurderinger.first()
                aktivitetskravVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.NY

                val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskravVurdering.personIdent.value
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskravVurdering.status.name
            }

            it("creates AktivitetskravVurdering(AUTOMATISK_OPPFYLT) for oppfolgingstilfelle lasting 9 weeks, gradert") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = DAYS_IN_WEEK * 9L,
                    gradert = true,
                )
                val kafkaOppfolgingstilfellePersonRecord =
                    createKafkaOppfolgingstilfellePersonConsumerRecord(kafkaOppfolgingstilfellePerson)
                val consumerRecords = ConsumerRecords(
                    mapOf(
                        kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonRecord
                        )
                    )
                )
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns consumerRecords

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravVurderinger = database.getAktivitetskravVurderinger(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                ).toAktivitetskravVurderinger()

                aktivitetskravVurderinger.size shouldBeEqualTo 1
                val aktivitetskravVurdering = aktivitetskravVurderinger.first()
                aktivitetskravVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT

                val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskravVurdering.personIdent.value
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskravVurdering.status.name
            }

            it("creates no AktivitetskravVurdering for oppfolgingstilfelle lasting 7 weeks, not gradert") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = DAYS_IN_WEEK * 7L,
                    gradert = false,
                )
                val kafkaOppfolgingstilfellePersonRecord =
                    createKafkaOppfolgingstilfellePersonConsumerRecord(kafkaOppfolgingstilfellePerson)
                val consumerRecords = ConsumerRecords(
                    mapOf(
                        kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonRecord
                        )
                    )
                )
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns consumerRecords

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravVurderinger = database.getAktivitetskravVurderinger(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                ).toAktivitetskravVurderinger()

                aktivitetskravVurderinger.shouldBeEmpty()
            }

            it("creates no AktivitetskravVurdering for oppfolgingstilfelle lasting 7 weeks, gradert") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = DAYS_IN_WEEK * 7L,
                    gradert = true,
                )
                val kafkaOppfolgingstilfellePersonRecord =
                    createKafkaOppfolgingstilfellePersonConsumerRecord(kafkaOppfolgingstilfellePerson)
                val consumerRecords = ConsumerRecords(
                    mapOf(
                        kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                            kafkaOppfolgingstilfellePersonRecord
                        )
                    )
                )
                every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns consumerRecords

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravVurderinger = database.getAktivitetskravVurderinger(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                ).toAktivitetskravVurderinger()

                aktivitetskravVurderinger.shouldBeEmpty()
            }
        }
    }
})
