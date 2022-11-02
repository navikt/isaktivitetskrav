package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.aktivitetskrav.AktivitetskravVurderingService
import no.nav.syfo.aktivitetskrav.database.getAktivitetskravVurderinger
import no.nav.syfo.aktivitetskrav.database.toAktivitetskravVurderinger
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class KafkaOppfolgingstilfellePersonServiceSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        beforeEachTest {
            database.dropData()
        }

        val kafkaOppfolgingstilfellePersonService = KafkaOppfolgingstilfellePersonService(
            database = database,
            aktivitetskravVurderingService = AktivitetskravVurderingService()
        )

        val kafkaOppfolgingstilfellePersonTopicPartition = createKafkaOppfolgingstilfellePersonTopicPartition()
        val mockKafkaConsumerOppfolgingstilfellePerson = mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {

            beforeEachTest {
                clearMocks(mockKafkaConsumerOppfolgingstilfellePerson)
                every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit
            }

            it("creates AktivitetskravVurdering(NY) for oppfolgingstilfelle lasting 9 weeks(INGEN_AKTIVITET)") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = 7 * 9L
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
            }
            it("creates no AktivitetskravVurdering for oppfolgingstilfelle lasting 7 weeks(INGEN_AKTIVITET)") {
                val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    tilfelleDurationInDays = 7 * 7L
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
