package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
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
private val yearAgo = LocalDate.now().minusYears(1)

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
        val kafkaOppfolgingstilfelleinFutureNineWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            tilfelleStart = LocalDate.now().plusDays(1),
            tilfelleEnd = LocalDate.now().plusWeeks(9),
            gradert = false,
        )

        beforeEachTest {
            database.dropData()
            clearMocks(kafkaProducer, mockKafkaConsumerOppfolgingstilfellePerson)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
            every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
        }

        fun mockKafkaConsumerOppfolgingstilfellePerson(
            vararg kafkaOppfolgingstilfellePerson: KafkaOppfolgingstilfellePerson,
        ) {
            val consumerRecords =
                kafkaOppfolgingstilfellePerson.map { createKafkaOppfolgingstilfellePersonConsumerRecord(it) }
            every { mockKafkaConsumerOppfolgingstilfellePerson.poll(any<Duration>()) } returns ConsumerRecords(
                mapOf(kafkaOppfolgingstilfellePersonTopicPartition to consumerRecords)
            )
        }

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {
            describe("no Aktivitetskrav exists for oppfolgingstilfelle") {
                it("creates Aktivitetskrav(NY) for oppfolgingstilfelle lasting 9 weeks, not gradert") {
                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskrav = aktivitetskravList.first()
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
                it("creates Aktivitetskrav(AUTOMATISK_OPPFYLT) for oppfolgingstilfelle lasting 9 weeks, gradert") {
                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskrav = aktivitetskravList.first()
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
                it("creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, not gradert") {
                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksNotGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.shouldBeEmpty()
                }
                it("creates no Aktivitetskrav for oppfolgingstilfelle lasting 7 weeks, gradert") {
                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.shouldBeEmpty()
                }
            }
            describe("Aktivitetskrav(NY) exists for oppfolgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("updates Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle gradert") {
                    database.createAktivitetskrav(nyAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
                it("updates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert") {
                    database.createAktivitetskrav(nyAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
            }
            describe("Aktivitetskrav(AUTOMATISK_OPPFYLT) exists for oppfolgingstilfelle") {
                val automatiskOppfyltAktivitetskrav = Aktivitetskrav.automatiskOppfylt(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                it("creates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert") {
                    database.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    latestAktivitetskrav.uuid shouldNotBeEqualTo automatiskOppfyltAktivitetskrav.uuid

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
                it("updates Aktivitetskrav(AUTOMATISK_OPPFYLT) if oppfolgingstilfelle gradert") {
                    database.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8)
                    latestAktivitetskrav.uuid shouldBeEqualTo automatiskOppfyltAktivitetskrav.uuid

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                    kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                    kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                }
            }
            describe("Aktivitetskrav(UNNTAK) exists for oppfolgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                val unntakAktivitetskrav = createAktivitetskravUnntak(nyAktivitetskrav)

                it("updates Aktivitetskrav(UNNTAK) stoppunkt_at") {
                    database.createAktivitetskrav(unntakAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.UNNTAK.name
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo nyAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Aktivitetskrav(OPPFYLT) exists for oppfolgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                val oppfyltAktivitetskrav = createAktivitetskravOppfylt(nyAktivitetskrav)

                it("updates Aktivitetskrav(OPPFYLT) stoppunkt_at") {
                    database.createAktivitetskrav(oppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT.name
                    latestAktivitetskrav.uuid shouldBeEqualTo oppfyltAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo oppfyltAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Aktivitetskrav(AVVENT) exists for oppfolgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                val avventAktivitetskrav = createAktivitetskravAvvent(nyAktivitetskrav)

                it("updates Aktivitetskrav(AVVENT) stoppunkt_at") {
                    database.createAktivitetskrav(avventAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT.name
                    latestAktivitetskrav.uuid shouldBeEqualTo avventAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo avventAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Aktivitetskrav vurdert, then oppfolgingstilfelle gradert, then not gradert") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo,
                )
                val unntakAktivitetskrav = createAktivitetskravUnntak(nyAktivitetskrav)

                it("creates Aktivitetskrav(AUTOMATISK_OPPFYLT) and then Aktivitetskrav(NY)") {
                    database.createAktivitetskrav(unntakAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksGradert,
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 3

                    aktivitetskravList[2].status shouldBeEqualTo AktivitetskravStatus.UNNTAK.name
                    aktivitetskravList[2].uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    aktivitetskravList[1].status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    aktivitetskravList[1].uuid shouldNotBeEqualTo nyAktivitetskrav.uuid

                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    latestAktivitetskrav.uuid shouldNotBeEqualTo nyAktivitetskrav.uuid

                    val kafkaRecordSlot1 = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    val kafkaRecordSlot2 = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verifyOrder {
                        kafkaProducer.send(capture(kafkaRecordSlot1))
                        kafkaProducer.send(capture(kafkaRecordSlot2))
                    }

                    val kafkaAktivitetskravVurdering = kafkaRecordSlot2.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
            }
            describe("Oppfolgingstilfelle start in future") {
                it("creates no Aktivitetskrav for future oppfolgingstilfelle lasting 9 weeks, not gradert") {
                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleinFutureNineWeeksNotGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.shouldBeEmpty()
                }
            }
            describe("Aktivitetskrav(NY) exists for earlier oppf??lgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = yearAgo,
                )

                it("updates aktivitetskrav for earlier oppf??lgingstilfelle to AUTOMATISK_OPPFYLT when latest oppf??lgingstilfelle lasting 9 weeks (not gradert)") {
                    database.createAktivitetskrav(nyAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2

                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                    val kafkaRecordSlot1 = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    val kafkaRecordSlot2 = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verifyOrder {
                        kafkaProducer.send(capture(kafkaRecordSlot1))
                        kafkaProducer.send(capture(kafkaRecordSlot2))
                    }

                    kafkaRecordSlot1.captured.value().status shouldBeEqualTo aktivitetskravEarlierOppfolgingstilfelle.status
                }
                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 7 weeks") {
                    database.createAktivitetskrav(nyAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksNotGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.NY.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                }
            }
            describe("Aktivitetskrav(AUTOMATISK_OPPFYLT) exists for earlier oppf??lgingstilfelle") {
                val automatiskOppfyltAktivitetskrav = Aktivitetskrav.automatiskOppfylt(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = yearAgo,
                )

                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 9 weeks (not gradert)") {
                    database.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo automatiskOppfyltAktivitetskrav.uuid
                }
                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 7 weeks") {
                    database.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksNotGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo automatiskOppfyltAktivitetskrav.uuid
                }
            }
            describe("Aktivitetskrav(UNNTAK) exists for earlier oppf??lgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = yearAgo,
                )
                val unntakAktivitetskrav = createAktivitetskravUnntak(nyAktivitetskrav)

                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 9 weeks (gradert)") {
                    database.createAktivitetskrav(unntakAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.UNNTAK.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo unntakAktivitetskrav.uuid
                }
                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 7 weeks") {
                    database.createAktivitetskrav(unntakAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.UNNTAK.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo unntakAktivitetskrav.uuid
                }
            }
            describe("Aktivitetskrav(OPPFYLT) exists for earlier oppf??lgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = yearAgo,
                )
                val oppfyltAktivitetskrav = createAktivitetskravOppfylt(nyAktivitetskrav)

                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 9 weeks (gradert)") {
                    database.createAktivitetskrav(oppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo oppfyltAktivitetskrav.uuid
                }
                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 7 weeks") {
                    database.createAktivitetskrav(oppfyltAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo oppfyltAktivitetskrav.uuid
                }
            }
            describe("Aktivitetskrav(AVVENT) exists for earlier oppf??lgingstilfelle") {
                val nyAktivitetskrav = Aktivitetskrav.ny(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = yearAgo,
                )
                val avventAktivitetskrav = createAktivitetskravAvvent(nyAktivitetskrav)

                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 9 weeks (not gradert)") {
                    database.createAktivitetskrav(avventAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 2
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AVVENT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo avventAktivitetskrav.uuid
                }
                it("do not update aktivitetskrav for earlier oppf??lgingstilfelle when latest oppf??lgingstilfelle lasting 7 weeks") {
                    database.createAktivitetskrav(avventAktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfellePersonSevenWeeksGradert
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

                    val aktivitetskravList = database.getAktivitetskrav(
                        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                    )

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AVVENT.name
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo avventAktivitetskrav.uuid
                }
            }
        }
    }
})
