package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.*
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
        val aktivitetskravService = AktivitetskravService(
            aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
            database = database,
        )
        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravService = aktivitetskravService,
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

        fun createAktivitetskrav(aktivitetskrav: Aktivitetskrav) {
            database.connection.use {
                it.createAktivitetskrav(aktivitetskrav)
                it.commit()
            }
        }

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {
            describe("no Aktivitetskrav exists for oppfolgingstilfelle") {
                it("creates Aktivitetskrav(NY) for oppfolgingstilfelle lasting 9 weeks, not gradert") {
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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskrav = aktivitetskravList.first()
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                    aktivitetskrav.updatedBy shouldBeEqualTo null
                    aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                }
                it("creates Aktivitetskrav(AUTOMATISK_OPPFYLT) for oppfolgingstilfelle lasting 9 weeks, gradert") {
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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskrav = aktivitetskravList.first()
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                    aktivitetskrav.updatedBy shouldBeEqualTo null
                    aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)
                    aktivitetskrav.beskrivelse shouldBeEqualTo "Gradert aktivitet"

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo aktivitetskrav.beskrivelse
                }
                it("creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, not gradert") {
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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.shouldBeEmpty()
                }
                it("creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, gradert") {
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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.shouldBeEmpty()
                }
            }
            describe("Aktivitetskrav(NY) exists for oppfolgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("updates Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle gradert") {
                    createAktivitetskrav(nyAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    latestAktivitetskrav.beskrivelse shouldBeEqualTo null

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo latestAktivitetskrav.beskrivelse
                }
                it("updates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert") {
                    createAktivitetskrav(nyAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    latestAktivitetskrav.beskrivelse shouldBeEqualTo null

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo latestAktivitetskrav.beskrivelse
                }
            }
            describe("Aktivitetskrav(AUTOMATISK_OPPFYLT) exists for oppfolgingstilfelle") {
                val automatiskOppfyltAktivitetskrav = Aktivitetskrav.automatiskOppfyltGradert(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("creates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert") {
                    createAktivitetskrav(automatiskOppfyltAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 2
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                    latestAktivitetskrav.uuid shouldNotBeEqualTo automatiskOppfyltAktivitetskrav.uuid
                    latestAktivitetskrav.beskrivelse shouldBeEqualTo null

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo latestAktivitetskrav.beskrivelse
                }
                it("updates Aktivitetskrav(AUTOMATISK_OPPFYLT) if oppfolgingstilfelle gradert") {
                    createAktivitetskrav(automatiskOppfyltAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo automatiskOppfyltAktivitetskrav.uuid
                    latestAktivitetskrav.beskrivelse shouldBeEqualTo "Gradert aktivitet"

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo latestAktivitetskrav.beskrivelse
                }
            }
            describe("Aktivitetskrav(UNNTAK) exists for oppfolgingstilfelle") {
                val unntakAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                ).copy(
                    status = AktivitetskravStatus.UNNTAK
                )

                it("updates Aktivitetskrav(UNNTAK) stoppunkt_at") {
                    createAktivitetskrav(unntakAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.UNNTAK
                    latestAktivitetskrav.uuid shouldBeEqualTo unntakAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo unntakAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Aktivitetskrav(OPPFYLT) exists for oppfolgingstilfelle") {
                val unntakAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                ).copy(
                    status = AktivitetskravStatus.OPPFYLT
                )

                it("updates Aktivitetskrav(OPPFYLT) stoppunkt_at") {
                    createAktivitetskrav(unntakAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                    latestAktivitetskrav.uuid shouldBeEqualTo unntakAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo unntakAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Aktivitetskrav(AVVENT) exists for oppfolgingstilfelle") {
                val unntakAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                ).copy(
                    status = AktivitetskravStatus.AVVENT
                )

                it("updates Aktivitetskrav(AVVENT) stoppunkt_at") {
                    createAktivitetskrav(unntakAktivitetskrav)

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

                    val aktivitetskravList = database.connection.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    ).toAktivitetskravList()

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                    latestAktivitetskrav.uuid shouldBeEqualTo unntakAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo unntakAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
        }
    }
})
