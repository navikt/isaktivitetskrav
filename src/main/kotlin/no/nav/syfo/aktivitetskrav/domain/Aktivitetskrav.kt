package no.nav.syfo.aktivitetskrav.domain

import no.nav.syfo.aktivitetskrav.api.AktivitetskravResponseDTO
import no.nav.syfo.aktivitetskrav.database.PAktivitetskrav
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

const val AKTIVITETSKRAV_STOPPUNKT_WEEKS = 8L
const val AUTOMATISK_OPPFYLT_BESKRIVELSE = "Gradert aktivitet"

enum class AktivitetskravStatus {
    NY,
    AVVENT,
    UNNTAK,
    OPPFYLT,
    AUTOMATISK_OPPFYLT,
    STANS
}

data class Aktivitetskrav private constructor(
    val uuid: UUID,
    val personIdent: PersonIdent,
    val createdAt: OffsetDateTime,
    val sistEndret: OffsetDateTime,
    val status: AktivitetskravStatus,
    val stoppunktAt: LocalDate,
    val beskrivelse: String?,
    val updatedBy: String?,
) {
    companion object {
        fun createFromDatabase(pAktivitetskrav: PAktivitetskrav) = Aktivitetskrav(
            uuid = pAktivitetskrav.uuid,
            personIdent = pAktivitetskrav.personIdent,
            createdAt = pAktivitetskrav.createdAt,
            sistEndret = pAktivitetskrav.updatedAt,
            status = AktivitetskravStatus.valueOf(pAktivitetskrav.status),
            stoppunktAt = pAktivitetskrav.stoppunktAt,
            beskrivelse = pAktivitetskrav.beskrivelse,
            updatedBy = pAktivitetskrav.updatedBy,
        )

        fun ny(personIdent: PersonIdent, tilfelleStart: LocalDate): Aktivitetskrav =
            create(
                personIdent = personIdent,
                status = AktivitetskravStatus.NY,
                tilfelleStart = tilfelleStart,
            )

        fun automatiskOppfyltGradert(
            personIdent: PersonIdent,
            tilfelleStart: LocalDate,
        ): Aktivitetskrav = create(
            personIdent = personIdent,
            status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
            tilfelleStart = tilfelleStart,
            beskrivelse = AUTOMATISK_OPPFYLT_BESKRIVELSE,
        )

        private fun create(
            personIdent: PersonIdent,
            status: AktivitetskravStatus,
            tilfelleStart: LocalDate,
            beskrivelse: String? = null,
        ) = Aktivitetskrav(
            uuid = UUID.randomUUID(),
            personIdent = personIdent,
            createdAt = nowUTC(),
            sistEndret = nowUTC(),
            status = status,
            stoppunktAt = stoppunktDato(tilfelleStart),
            beskrivelse = beskrivelse,
            updatedBy = null,
        )

        fun stoppunktDato(tilfelleStart: LocalDate): LocalDate =
            tilfelleStart.plusWeeks(AKTIVITETSKRAV_STOPPUNKT_WEEKS)
    }
}

fun Aktivitetskrav.toKafkaAktivitetskravVurdering() = KafkaAktivitetskravVurdering(
    uuid = this.uuid.toString(),
    personIdent = this.personIdent.value,
    createdAt = this.createdAt,
    updatedAt = this.sistEndret,
    status = this.status.name,
    beskrivelse = this.beskrivelse,
    stoppunktAt = this.stoppunktAt,
    updatedBy = this.updatedBy,
)

infix fun Aktivitetskrav.gjelder(oppfolgingstilfelle: Oppfolgingstilfelle): Boolean =
    this.personIdent == oppfolgingstilfelle.personIdent && this.stoppunktAt.isAfter(oppfolgingstilfelle.tilfelleStart) && oppfolgingstilfelle.tilfelleEnd.isAfter(
        stoppunktAt
    )

fun Aktivitetskrav.isAutomatiskOppfylt(): Boolean =
    this.status == AktivitetskravStatus.AUTOMATISK_OPPFYLT

fun List<Aktivitetskrav>.toResponseDTOList() = this.map {
    AktivitetskravResponseDTO(
        uuid = it.uuid.toString(),
        createdAt = it.createdAt.toLocalDateTime(),
        sistEndret = it.sistEndret.toLocalDateTime(),
        status = it.status,
        updatedBy = it.updatedBy,
        beskrivelse = it.beskrivelse,
    )
}

internal fun Aktivitetskrav.updateFrom(oppfolgingstilfelle: Oppfolgingstilfelle): Aktivitetskrav {
    val stoppunktDato = Aktivitetskrav.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
    return this.copy(
        stoppunktAt = stoppunktDato,
        sistEndret = nowUTC(),
    )
}

internal fun Aktivitetskrav.vurder(
    status: AktivitetskravStatus,
    vurdertAv: String,
    beskrivelse: String?,
): Aktivitetskrav = this.copy(
    status = status,
    beskrivelse = beskrivelse,
    sistEndret = nowUTC(),
    updatedBy = vurdertAv,
)
