package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PAktivitetskravVurdering(
    val id: Int,
    val uuid: UUID,
    val aktivitetskravId: Int,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: String,
    val beskrivelse: String?,
    val arsaker: List<String>,
    val frist: LocalDate?,
) {
    fun toAktivitetskravVurdering() =
        AktivitetskravVurdering.createFromDatabase(this)
}
