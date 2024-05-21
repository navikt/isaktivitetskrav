package no.nav.syfo.application.metric

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "isaktivitetskrav"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
