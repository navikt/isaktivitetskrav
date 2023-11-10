package no.nav.syfo.aktivitetskrav.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val AKTIVITETSKRAV_KAFKA_METRIC_BASE = "${METRICS_NS}_kafka_producer"
const val AKTIVITETSKRAV_VURDERING_KAFKA = "${AKTIVITETSKRAV_KAFKA_METRIC_BASE}_vurdering"
const val UNNTAK = "${AKTIVITETSKRAV_VURDERING_KAFKA}_unntak"
const val NY_VURDERING = "${AKTIVITETSKRAV_VURDERING_KAFKA}_ny_vurdering"
const val AVVENT = "${AKTIVITETSKRAV_VURDERING_KAFKA}_avvent"
const val OPPFYLT = "${AKTIVITETSKRAV_VURDERING_KAFKA}_oppfylt"
const val STANS = "${AKTIVITETSKRAV_VURDERING_KAFKA}_stans"
const val FORHANDSVARSEL = "${AKTIVITETSKRAV_VURDERING_KAFKA}_forhandsvarsel"
const val IKKE_OPPFYLT = "${AKTIVITETSKRAV_VURDERING_KAFKA}_ikke_oppfylt"
const val IKKE_AKTUELL = "${AKTIVITETSKRAV_VURDERING_KAFKA}_ikke_aktuell"

val COUNT_FORHANDSVARSEL: Counter =
    Counter.builder(FORHANDSVARSEL)
        .description("Counts the number vurderinger with status FORHANDSVARSEL created")
        .register(METRICS_REGISTRY)

val COUNT_UNNTAK: Counter =
    Counter.builder(UNNTAK)
        .description("Counts the number vurderinger with status UNNTAK created")
        .register(METRICS_REGISTRY)

val COUNT_NY_VURDERING: Counter =
    Counter.builder(NY_VURDERING)
        .description("Counts the number vurderinger with status NY_VURDERING created")
        .register(METRICS_REGISTRY)

val COUNT_AVVENT: Counter =
    Counter.builder(AVVENT)
        .description("Counts the number vurderinger with status AVVENT created")
        .register(METRICS_REGISTRY)

val COUNT_OPPFYLT: Counter =
    Counter.builder(OPPFYLT)
        .description("Counts the number vurderinger with status OPPFYLT created")
        .register(METRICS_REGISTRY)

val COUNT_STANS: Counter =
    Counter.builder(STANS)
        .description("Counts the number vurderinger with status STANS created")
        .register(METRICS_REGISTRY)

val COUNT_IKKE_OPPFYLT: Counter =
    Counter.builder(IKKE_OPPFYLT)
        .description("Counts the number vurderinger with status IKKE_OPPFYLT created")
        .register(METRICS_REGISTRY)

val COUNT_IKKE_AKTUELL: Counter =
    Counter.builder(IKKE_AKTUELL)
        .description("Counts the number vurderinger with status IKKE_AKTUELL created")
        .register(METRICS_REGISTRY)
