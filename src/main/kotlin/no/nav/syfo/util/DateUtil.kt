package no.nav.syfo.util

import java.time.*
import java.time.temporal.ChronoUnit

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun tomorrow(): LocalDate = LocalDate.now().plusDays(1)

fun OffsetDateTime.millisekundOpplosning(): OffsetDateTime = this.truncatedTo(ChronoUnit.MILLIS)

fun OffsetDateTime.sekundOpplosning(): OffsetDateTime = this.truncatedTo(ChronoUnit.SECONDS)

fun LocalDate.isAfterOrEqual(date: LocalDate) = !this.isBefore(date)

infix fun LocalDate.isMoreThanDaysAgo(days: Long): Boolean = this.isBefore(LocalDate.now().minusDays(days))
