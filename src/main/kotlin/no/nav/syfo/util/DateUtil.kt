package no.nav.syfo.util

import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)
