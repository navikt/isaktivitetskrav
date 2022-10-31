package no.nav.syfo.oppfolgingstilfelle.kafka

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
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
            database = database
        )

        val kafkaOppfolgingstilfellePerson = createKafkaOppfolgingstilfellePerson()
        val kafkaOppfolgingstilfellePersonRecord =
            createKafkaOppfolgingstilfellePersonConsumerRecord(kafkaOppfolgingstilfellePerson)
        val kafkaOppfolgingstilfellePersonTopicPartition = createKafkaOppfolgingstilfellePersonTopicPartition()

        val mockKafkaConsumerOppfolgingstilfellePerson = mockk<KafkaConsumer<String, KafkaOppfolgingstilfellePerson>>()
        every { mockKafkaConsumerOppfolgingstilfellePerson.commitSync() } returns Unit

        describe("${KafkaOppfolgingstilfellePersonService::class.java.simpleName}: pollAndProcessRecords") {
            it("commits offset") {
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
            }
        }
    }
})
