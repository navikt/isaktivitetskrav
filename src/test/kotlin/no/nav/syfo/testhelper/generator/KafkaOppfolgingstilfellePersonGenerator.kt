package no.nav.syfo.testhelper.generator

import no.nav.syfo.oppfolgingstilfelle.kafka.*
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.nowUTC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import java.time.LocalDate
import java.util.*

fun createKafkaOppfolgingstilfellePerson(): KafkaOppfolgingstilfellePerson = KafkaOppfolgingstilfellePerson(
    uuid = UUID.randomUUID().toString(),
    createdAt = nowUTC(),
    personIdentNumber = ARBEIDSTAKER_PERSONIDENT.value,
    oppfolgingstilfelleList = listOf(
        KafkaOppfolgingstilfelle(
            arbeidstakerAtTilfelleEnd = true,
            start = LocalDate.now().minusDays(1),
            end = LocalDate.now().plusDays(1),
            virksomhetsnummerList = listOf(
                VIRKSOMHETSNUMMER_DEFAULT.value,
            )
        ),
    ),
    referanseTilfelleBitUuid = UUID.randomUUID().toString(),
    referanseTilfelleBitInntruffet = nowUTC().minusDays(1),
)

fun createKafkaOppfolgingstilfellePersonConsumerRecord(
    kafkaOppfolgingstilfellePerson: KafkaOppfolgingstilfellePerson,
) = ConsumerRecord(
    OPPFOLGINGSTILFELLE_PERSON_TOPIC,
    0,
    1,
    "key1",
    kafkaOppfolgingstilfellePerson
)

fun createKafkaOppfolgingstilfellePersonTopicPartition() = TopicPartition(
    OPPFOLGINGSTILFELLE_PERSON_TOPIC,
    0,
)
