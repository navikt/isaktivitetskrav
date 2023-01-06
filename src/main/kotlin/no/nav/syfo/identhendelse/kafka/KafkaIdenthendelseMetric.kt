package no.nav.syfo.identhendelse.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_PDL_AKTOR_BASE = "${METRICS_NS}_kafka_consumer_pdl_aktor_v2"
const val KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE = "${KAFKA_CONSUMER_PDL_AKTOR_BASE}_tombstone"

val COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE: Counter =
    Counter.builder(KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE)
        .description("Counts the number of tombstones received from topic - pdl-aktor-v2")
        .register(METRICS_REGISTRY)
