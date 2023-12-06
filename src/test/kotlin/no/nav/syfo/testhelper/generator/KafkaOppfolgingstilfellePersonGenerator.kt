package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.kafka.*
import no.nav.syfo.testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import no.nav.syfo.util.nowUTC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

fun createKafkaOppfolgingstilfellePerson(
    personIdent: PersonIdent,
    tilfelleStart: LocalDate,
    tilfelleEnd: LocalDate,
    gradert: Boolean,
    dodsdato: LocalDate? = null,
): KafkaOppfolgingstilfellePerson = KafkaOppfolgingstilfellePerson(
    uuid = UUID.randomUUID().toString(),
    createdAt = nowUTC(),
    personIdentNumber = personIdent.value,
    oppfolgingstilfelleList = listOf(
        KafkaOppfolgingstilfelle(
            arbeidstakerAtTilfelleEnd = true,
            start = tilfelleStart,
            end = tilfelleEnd,
            antallSykedager = ChronoUnit.DAYS.between(tilfelleStart, tilfelleEnd).toInt(),
            virksomhetsnummerList = listOf(
                VIRKSOMHETSNUMMER_DEFAULT.value,
            ),
            gradertAtTilfelleEnd = gradert,
        ),
    ),
    referanseTilfelleBitUuid = UUID.randomUUID().toString(),
    referanseTilfelleBitInntruffet = nowUTC().minusDays(1),
    dodsdato = dodsdato,
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
