package no.nav.syfo.infrastructure.database

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
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
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val referanseTilfelleBitUuid: UUID?,
    val previousAktivitetskravUuid: UUID?,
    val vurderinger: List<PAktivitetskravVurdering> = emptyList(),
) {

    fun toAktivitetskrav() =
        Aktivitetskrav(
            uuid = uuid,
            personIdent = personIdent,
            createdAt = createdAt,
            updatedAt = updatedAt,
            status = status,
            stoppunktAt = stoppunktAt,
            vurderinger = vurderinger.map { it.toAktivitetskravVurdering() },
        )
}
