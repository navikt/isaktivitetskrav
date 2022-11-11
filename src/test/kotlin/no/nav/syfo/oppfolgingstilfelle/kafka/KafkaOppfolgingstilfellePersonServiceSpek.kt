package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
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
import java.time.LocalDate
import java.util.concurrent.Future

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
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

        val kafkaOppfolgingstilfelleNineWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = nineWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = false,
        )
        val kafkaOppfolgingstilfelleNineWeeksGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = nineWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = true,
        )
        val kafkaOppfolgingstilfellePersonSevenWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = sevenWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = false,
        )
        val kafkaOppfolgingstilfellePersonSevenWeeksGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = sevenWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = true,
        )
        val kafkaOppfolgingstilfelleTenWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = tenWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = false,
        )
        val kafkaOppfolgingstilfelleTenWeeksGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = tenWeeksAgo,
            tilfelleEnd = LocalDate.now(),
            gradert = true,
        )

        beforeEachTest {
            database.dropData()
            clearMocks(kafkaProducer, mockKafkaConsumerOppfolgingstilfellePerson)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
            every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
        }

        fun createAktivitetskravVurdering(aktivitetskravVurdering: AktivitetskravVurdering) {
            database.connection.use {
                it.createAktivitetskravVurdering(aktivitetskravVurdering)
                it.commit()
            }
        }

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {
            describe("no AktivitetskravVurdering exists for oppfolgingstilfelle") {
                it("creates AktivitetskravVurdering(NY) for oppfolgingstilfelle lasting 9 weeks, not gradert") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleNineWeeksNotGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 1
                    val aktivitetskravVurdering = aktivitetskravVurderinger.first()
                    aktivitetskravVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.NY
                    aktivitetskravVurdering.updatedBy shouldBeEqualTo null
                    aktivitetskravVurdering.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskravVurdering.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskravVurdering.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskravVurdering.stoppunktAt
                }
                it("creates AktivitetskravVurdering(AUTOMATISK_OPPFYLT) for oppfolgingstilfelle lasting 9 weeks, gradert") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleNineWeeksGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 1
                    val aktivitetskravVurdering = aktivitetskravVurderinger.first()
                    aktivitetskravVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT
                    aktivitetskravVurdering.updatedBy shouldBeEqualTo null
                    aktivitetskravVurdering.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskravVurdering.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskravVurdering.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskravVurdering.stoppunktAt
                }
                it("creates no AktivitetskravVurdering for oppfolgingstilfelle lasting 7 weeks, not gradert") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfellePersonSevenWeeksNotGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }
                    verify(exactly = 0) {
                        kafkaProducer.send(any())
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.shouldBeEmpty()
                }
                it("creates no AktivitetskravVurdering for oppfolgingstilfelle lasting 7 weeks, gradert") {
                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfellePersonSevenWeeksGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }
                    verify(exactly = 0) {
                        kafkaProducer.send(any())
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.shouldBeEmpty()
                }
            }
            describe("AktivitetskravVurdering(NY) exists for oppfolgingstilfelle") {
                val nyAktivitetskravVurdering = AktivitetskravVurdering.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("updates AktivitetskravVurdering stoppunkt_at and status to AUTOMATISK_OPPFYLT if oppfolgingstilfelle gradert") {
                    createAktivitetskravVurdering(nyAktivitetskravVurdering)

                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleTenWeeksGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 1
                    val latestVurdering = aktivitetskravVurderinger.first()
                    latestVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT
                    latestVurdering.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestVurdering.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestVurdering.stoppunktAt
                }
                it("updates AktivitetskravVurdering stoppunkt_at if oppfolgingstilfelle not gradert") {
                    createAktivitetskravVurdering(nyAktivitetskravVurdering)

                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleTenWeeksNotGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 1
                    val latestVurdering = aktivitetskravVurderinger.first()
                    latestVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.NY
                    latestVurdering.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestVurdering.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestVurdering.stoppunktAt
                }
            }
            describe("AktivitetskravVurdering(AUTOMATISK_OPPFYLT) exists for oppfolgingstilfelle") {
                val automatiskOppfyltAktivitetskravVurdering = AktivitetskravVurdering.automatiskOppfyltGradert(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("creates AktivitetskravVurdering(NY) if oppfolgingstilfelle not gradert") {
                    createAktivitetskravVurdering(automatiskOppfyltAktivitetskravVurdering)

                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleNineWeeksNotGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 2
                    val latestVurdering = aktivitetskravVurderinger.first()
                    latestVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.NY

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.NY.name
                }
                it("updates AktivitetskravVurdering stoppunkt_at if oppfolgingstilfelle gradert") {
                    createAktivitetskravVurdering(automatiskOppfyltAktivitetskravVurdering)

                    every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            kafkaOppfolgingstilfellePersonTopicPartition to listOf(
                                createKafkaOppfolgingstilfellePersonConsumerRecord(
                                    kafkaOppfolgingstilfelleTenWeeksGradert
                                )
                            )
                        )
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravVurderinger = database.connection.getAktivitetskravVurderinger(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravVurderinger()

                    aktivitetskravVurderinger.size shouldBeEqualTo 1
                    val latestVurdering = aktivitetskravVurderinger.first()
                    latestVurdering.status shouldBeEqualTo AktivitetskravVurderingStatus.AUTOMATISK_OPPFYLT
                    latestVurdering.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestVurdering.uuid shouldBeEqualTo automatiskOppfyltAktivitetskravVurdering.uuid

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestVurdering.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestVurdering.stoppunktAt
                }
            }
        }
    }
})
