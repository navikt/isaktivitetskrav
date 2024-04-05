package no.nav.syfo.client.dokarkiv

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_DOKARKIV_BASE = "${METRICS_NS}_call_dokarkiv"

const val CALL_DOKARKIV_JOURNALPOST_BASE = "${CALL_DOKARKIV_BASE}_journalpost"
const val CALL_DOKARKIV_JOURNALPOST_SUCCESS = "${CALL_DOKARKIV_JOURNALPOST_BASE}_success_count"
const val CALL_DOKARKIV_JOURNALPOST_FAIL = "${CALL_DOKARKIV_JOURNALPOST_BASE}_fail_count"
const val CALL_DOKARKIV_JOURNALPOST_CONFLICT = "${CALL_DOKARKIV_JOURNALPOST_BASE}_conflict_count"

val COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS: Counter = Counter
    .builder(CALL_DOKARKIV_JOURNALPOST_SUCCESS)
    .description("Counts the number of successful calls to Dokarkiv - Journalpost")
    .register(METRICS_REGISTRY)
val COUNT_CALL_DOKARKIV_JOURNALPOST_FAIL: Counter = Counter
    .builder(CALL_DOKARKIV_JOURNALPOST_FAIL)
    .description("Counts the number of failed calls to Dokarkiv - Journalpost")
    .register(METRICS_REGISTRY)
val COUNT_CALL_DOKARKIV_JOURNALPOST_CONFLICT: Counter = Counter
    .builder(CALL_DOKARKIV_JOURNALPOST_CONFLICT)
    .description("Counts the number of calls to Dokarkiv - Journalpost resulting in 409 Conflict")
    .register(METRICS_REGISTRY)
