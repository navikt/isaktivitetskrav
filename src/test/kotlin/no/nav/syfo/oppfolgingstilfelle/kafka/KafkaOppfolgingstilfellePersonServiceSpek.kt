package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Future

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val eightWeeksAgo = LocalDate.now().minusWeeks(8)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)
private val yearAgo = LocalDate.now().minusYears(1)

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val arenaCutoff = externalMockEnvironment.environment.arenaCutoff
    val kafkaProducer = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(
        producer = kafkaProducer,
    )
    val aktivitetskravRepository = AktivitetskravRepository(database)
    val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    val aktivitetskravService = AktivitetskravService(
        aktivitetskravRepository = aktivitetskravRepository,
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        arenaCutoff = arenaCutoff,
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )
    )
    val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
        database = database,
        aktivitetskravService = aktivitetskravService,
        arenaCutoff = arenaCutoff,
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
    val kafkaOppfolgingstilfellePersonWithDodsdato = createKafkaOppfolgingstilfellePerson(
        personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = tenWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        gradert = false,
        dodsdato = LocalDate.now(),
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8).minusDays(1)
                aktivitetskrav.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelleNineWeeksNotGradert.referanseTilfelleBitUuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
                kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                aktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8).minusDays(1)
                aktivitetskrav.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelleNineWeeksGradert.referanseTilfelleBitUuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo aktivitetskrav.personIdent.value
                kafkaAktivitetskravVurdering.status shouldBeEqualTo aktivitetskrav.status.name
                kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo aktivitetskrav.stoppunktAt
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.shouldBeEmpty()
            }
            it("creates no Aktivitetskrav for oppfolgingstilfelle when dodsdato != null") {
                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfellePersonWithDodsdato
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.shouldBeEmpty()
            }
            it("creates no Aktivitetskrav for oppfolgingstilfelle starting before OLD_TILFELLE_CUTOFF") {
                val oldKafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = OLD_TILFELLE_CUTOFF.minusDays(1),
                    tilfelleEnd = OLD_TILFELLE_CUTOFF.plusWeeks(9),
                    gradert = false,
                )

                mockKafkaConsumerOppfolgingstilfellePerson(
                    oldKafkaOppfolgingstilfellePerson
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.shouldBeEmpty()
            }
            it("creates no Aktivitetskrav for oppfolgingstilfelle ending before arenaCutoff date") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = arenaCutoff.minusWeeks(12),
                    tilfelleEnd = arenaCutoff.minusDays(1),
                    gradert = false,
                )

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfellePerson
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

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

                val aktivitetskravList = aktivitetskravRepository.getAktivitetskrav(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                )

                aktivitetskravList.shouldBeEmpty()
            }
            it("creates Aktivitetskrav(NY) once for oppfolgingstilfelle polled twice lasting 8 weeks, not gradert") {
                val kafkaOppfolgingstilfelleEightWeeksNotGradert = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = eightWeeksAgo,
                    tilfelleEnd = LocalDate.now(),
                    gradert = false,
                )

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleEightWeeksNotGradert,
                    kafkaOppfolgingstilfelleEightWeeksNotGradert,
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList = aktivitetskravRepository.getAktivitetskrav(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                )

                aktivitetskravList.size shouldBeEqualTo 1
            }
        }
        describe("Aktivitetskrav(NY) exists for oppfolgingstilfelle") {
            val nyAktivitetskrav = createAktivitetskravNy(
                tilfelleStart = nineWeeksAgo,
            )
            it("does not update Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle-start unchanged") {
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                var aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                var aktivitetskrav = aktivitetskravList.first()
                val updatedAt = aktivitetskrav.updatedAt

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleNineWeeksNotGradert
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

                aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.updatedAt shouldBeEqualTo updatedAt
            }

            it("updates Aktivitetskrav(NY) stoppunkt_at if oppfolgingstilfelle gradert and start changed") {
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleTenWeeksGradert
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val latestAktivitetskrav = aktivitetskravList.first()
                latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8).minusDays(1)
                latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
            }
            it("updates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert and start changed") {
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleTenWeeksNotGradert
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val latestAktivitetskrav = aktivitetskravList.first()
                latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8).minusDays(1)
                latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
            }
        }
        describe("Aktivitetskrav(AUTOMATISK_OPPFYLT) exists for oppfolgingstilfelle") {
            val automatiskOppfyltAktivitetskrav =
                createAktivitetskravAutomatiskOppfylt(tilfelleStart = nineWeeksAgo)

            it("creates Aktivitetskrav(NY) if oppfolgingstilfelle not gradert") {
                aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleNineWeeksNotGradert
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 2
                val latestAktivitetskrav = aktivitetskravList.first()
                latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                latestAktivitetskrav.uuid shouldNotBeEqualTo automatiskOppfyltAktivitetskrav.uuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.NY.name
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
            }
            it("updates Aktivitetskrav(AUTOMATISK_OPPFYLT) if oppfolgingstilfelle gradert and start changed") {
                aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleTenWeeksGradert
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val latestAktivitetskrav = aktivitetskravList.first()
                latestAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                latestAktivitetskrav.stoppunktAt shouldBeEqualTo tenWeeksAgo.plusWeeks(8).minusDays(1)
                latestAktivitetskrav.uuid shouldBeEqualTo automatiskOppfyltAktivitetskrav.uuid

                val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
                kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
                kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
            }
            it("does not update Aktivitetskrav(AUTOMATISK_OPPFYLT) stoppunkt_at if oppfolgingstilfelle-start unchanged") {
                aktivitetskravRepository.createAktivitetskrav(automatiskOppfyltAktivitetskrav)

                var aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                var aktivitetskrav = aktivitetskravList.first()
                val updatedAt = aktivitetskrav.updatedAt

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleNineWeeksGradert
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

                aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.updatedAt shouldBeEqualTo updatedAt
            }
        }
        describe("Aktivitetskrav(UNNTAK/OPPFYLT/AVVENT/IKKE_OPPFYLT/IKKE_AKTUELL) exists for oppfolgingstilfelle") {
            val nyAktivitetskrav = createAktivitetskravNy(
                tilfelleStart = nineWeeksAgo,
            )
            val testcases = listOf(
                createAktivitetskravUnntak(nyAktivitetskrav),
                createAktivitetskravOppfylt(nyAktivitetskrav),
                createAktivitetskravAvvent(nyAktivitetskrav),
                createAktivitetskravIkkeOppfylt(nyAktivitetskrav),
                createAktivitetskravIkkeAktuell(nyAktivitetskrav),
            )
            testcases.forEach { aktivitetskrav ->
                val aktivitetskravStatus = aktivitetskrav.status
                it("updates Aktivitetskrav($aktivitetskravStatus) stoppunkt_at if oppfolgingstilfelle not gradert and start changed") {
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo aktivitetskravStatus
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo nyAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
                it("updates Aktivitetskrav($aktivitetskravStatus) stoppunkt_at if oppfolgingstilfelle gradert and start changed") {
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleTenWeeksGradert,
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                    aktivitetskravList.size shouldBeEqualTo 1
                    val latestAktivitetskrav = aktivitetskravList.first()
                    latestAktivitetskrav.status shouldBeEqualTo aktivitetskravStatus
                    latestAktivitetskrav.uuid shouldBeEqualTo nyAktivitetskrav.uuid
                    latestAktivitetskrav.stoppunktAt shouldNotBeEqualTo nyAktivitetskrav.stoppunktAt

                    val kafkaRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
                    val kafkaAktivitetskravVurdering = kafkaRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo latestAktivitetskrav.status.name
                    kafkaAktivitetskravVurdering.stoppunktAt shouldBeEqualTo latestAktivitetskrav.stoppunktAt
                }
                it("does not update Aktivitetskrav($aktivitetskravStatus) stoppunkt_at if oppfolgingstilfelle-start unchanged") {
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

                    var aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    var pAktivitetskrav = aktivitetskravList.first()
                    val updatedAt = pAktivitetskrav.updatedAt

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
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

                    aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    pAktivitetskrav = aktivitetskravList.first()
                    pAktivitetskrav.updatedAt shouldBeEqualTo updatedAt
                }
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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.shouldBeEmpty()
            }
        }
        describe("Aktivitetskrav(NY) exists for earlier oppfolgingstilfelle") {
            val nyAktivitetskrav = createAktivitetskravNy(tilfelleStart = yearAgo)

            it("updates aktivitetskrav for earlier oppfolgingstilfelle to AUTOMATISK_OPPFYLT when latest oppfolgingstilfelle lasting 9 weeks (not gradert)") {
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelleNineWeeksNotGradert
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 2

                val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.AUTOMATISK_OPPFYLT
                aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo nyAktivitetskrav.uuid

                val kafkaRecordSlot1 = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                val kafkaRecordSlot2 = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
                verifyOrder {
                    kafkaProducer.send(capture(kafkaRecordSlot1))
                    kafkaProducer.send(capture(kafkaRecordSlot2))
                }

                kafkaRecordSlot1.captured.value().status shouldBeEqualTo aktivitetskravEarlierOppfolgingstilfelle.status.name
            }
            it("do not update aktivitetskrav for earlier oppfolgingstilfelle when latest oppfolgingstilfelle lasting 7 weeks") {
                aktivitetskravRepository.createAktivitetskrav(nyAktivitetskrav)

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

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo AktivitetskravStatus.NY
                aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo nyAktivitetskrav.uuid
            }
        }
        describe("Aktivitetskrav(AUTOMATISK_OPPFYLT/UNNTAK/OPPFYLT/AVVENT/IKKE_OPPFYLT/IKKE_AKTUELL) exists for earlier oppfolgingstilfelle") {
            val nyAktivitetskrav = createAktivitetskravNy(tilfelleStart = yearAgo)
            val testcases = listOf(
                createAktivitetskravAutomatiskOppfylt(tilfelleStart = yearAgo),
                createAktivitetskravUnntak(nyAktivitetskrav),
                createAktivitetskravOppfylt(nyAktivitetskrav),
                createAktivitetskravAvvent(nyAktivitetskrav),
                createAktivitetskravIkkeOppfylt(nyAktivitetskrav),
                createAktivitetskravIkkeAktuell(nyAktivitetskrav)
            )
            testcases.forEach { aktivitetskrav ->
                val aktivitetskravStatus = aktivitetskrav.status
                it("do not update aktivitetskrav($aktivitetskravStatus) for earlier oppfolgingstilfelle when latest oppfolgingstilfelle lasting 9 weeks") {
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

                    mockKafkaConsumerOppfolgingstilfellePerson(
                        kafkaOppfolgingstilfelleNineWeeksNotGradert
                    )

                    kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                        kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                    }

                    val aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                    aktivitetskravList.size shouldBeEqualTo 2
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.last()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo aktivitetskravStatus
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo aktivitetskrav.uuid
                }
                it("do not update aktivitetskrav($aktivitetskravStatus) for earlier oppfolgingstilfelle when latest oppfolgingstilfelle lasting 7 weeks") {
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

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

                    val aktivitetskravList =
                        aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                    aktivitetskravList.size shouldBeEqualTo 1
                    val aktivitetskravEarlierOppfolgingstilfelle = aktivitetskravList.first()
                    aktivitetskravEarlierOppfolgingstilfelle.status shouldBeEqualTo aktivitetskravStatus
                    aktivitetskravEarlierOppfolgingstilfelle.uuid shouldBeEqualTo aktivitetskrav.uuid
                }
            }
        }

        describe("Oppfolgingstilfelle is exactly 56 days") {
            val startDate = LocalDate.now().minusDays(5)
            val endDate = LocalDate.now().plusDays(50)
            val kafkaOppfolgingstilfelle56Days = createKafkaOppfolgingstilfellePerson(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = startDate,
                tilfelleEnd = endDate,
                gradert = false,
            )
            val secondKafkaOppfolgingstilfelle56Days = kafkaOppfolgingstilfelle56Days.copy(
                referanseTilfelleBitInntruffet = kafkaOppfolgingstilfelle56Days.referanseTilfelleBitInntruffet.plusDays(
                    1
                ),
                referanseTilfelleBitUuid = UUID.randomUUID().toString(),
            )

            it("creates Aktivitetskrav(NY) for oppfolgingstilfelle lasting exactly 56 days") {
                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelle56Days
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                aktivitetskrav.stoppunktAt shouldBeEqualTo endDate
                aktivitetskrav.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelle56Days.referanseTilfelleBitUuid
            }

            it("doesn't create Aktivitetskrav for second oppfolgingstilfelle lasting exactly 56 days") {
                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaOppfolgingstilfelle56Days,
                    secondKafkaOppfolgingstilfelle56Days,
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.size shouldBeEqualTo 1
                val aktivitetskrav = aktivitetskravList.first()
                aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.NY
                aktivitetskrav.stoppunktAt shouldBeEqualTo endDate
                aktivitetskrav.referanseTilfelleBitUuid.toString() shouldBeEqualTo kafkaOppfolgingstilfelle56Days.referanseTilfelleBitUuid
            }
        }

        describe("Inactive oppfolgingstilfelle") {
            val startDate = LocalDate.now().minusDays(90)
            val endDate = LocalDate.now().minusDays(31)
            val kafkaInactiveOppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
                personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = startDate,
                tilfelleEnd = endDate,
                gradert = false,
            )

            it("does not create Aktivitetskrav(NY) for oppfolgingstilfelle ending more than 30 days ago") {
                mockKafkaConsumerOppfolgingstilfellePerson(
                    kafkaInactiveOppfolgingstilfelle
                )

                kafkaOppfolgingstilfellePersonService.pollAndProcessRecords(
                    kafkaConsumer = mockKafkaConsumerOppfolgingstilfellePerson,
                )

                verify(exactly = 1) {
                    mockKafkaConsumerOppfolgingstilfellePerson.commitSync()
                }

                val aktivitetskravList =
                    aktivitetskravRepository.getAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)

                aktivitetskravList.shouldBeEmpty()
            }
        }
    }
})
