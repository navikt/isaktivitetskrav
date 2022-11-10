package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskravVurdering(
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

fun List<PAktivitetskravVurdering>.toAktivitetskravVurderinger() = map { it.toAktivitetskravVurdering() }
fun PAktivitetskravVurdering.toAktivitetskravVurdering() = AktivitetskravVurdering.createFromDatabase(this)
