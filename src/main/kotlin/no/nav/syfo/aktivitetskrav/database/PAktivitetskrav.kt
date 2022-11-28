package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskrav(
    val id: Int,
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: String,
    val stoppunktAt: LocalDate,
    val beskrivelse: String?,
    val updatedBy: String?,
)

fun List<PAktivitetskrav>.toAktivitetskravList() = map { it.toAktivitetskrav() }
fun PAktivitetskrav.toAktivitetskrav() = Aktivitetskrav.createFromDatabase(this)
