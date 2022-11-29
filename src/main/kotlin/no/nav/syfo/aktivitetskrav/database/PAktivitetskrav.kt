package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
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
)

fun PAktivitetskrav.toAktivitetskrav(aktivitetskravVurderinger: List<AktivitetskravVurdering>) =
    Aktivitetskrav.createFromDatabase(this, aktivitetskravVurderinger = aktivitetskravVurderinger)
