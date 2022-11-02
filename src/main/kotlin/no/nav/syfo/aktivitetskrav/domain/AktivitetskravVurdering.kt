package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.database.PAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val AKTIVITETSKRAV_VURDERING_STOPPUNKT_WEEKS = 8

enum class AktivitetskravVurderingStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    STANS
}

data class AktivitetskravVurdering private constructor(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val status: AktivitetskravVurderingStatus,
    val tilfelleStart: LocalDate,
    val beskrivelse: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskravVurdering: PAktivitetskravVurdering) = AktivitetskravVurdering(
            uuid = pAktivitetskravVurdering.uuid,
            personIdent = pAktivitetskravVurdering.personIdent,
            createdAt = pAktivitetskravVurdering.createdAt,
            updatedAt = pAktivitetskravVurdering.updatedAt,
            status = AktivitetskravVurderingStatus.valueOf(pAktivitetskravVurdering.status),
            beskrivelse = pAktivitetskravVurdering.beskrivelse,
            tilfelleStart = pAktivitetskravVurdering.tilfelleStart,
        )

        fun ny(personIdent: PersonIdent, tilfelleStart: LocalDate): AktivitetskravVurdering =
            AktivitetskravVurdering(
                uuid = UUID.randomUUID(),
                personIdent = personIdent,
                createdAt = nowUTC(),
                updatedAt = nowUTC(),
                status = AktivitetskravVurderingStatus.NY,
                tilfelleStart = tilfelleStart,
                beskrivelse = null,
            )
    }
}
