package no.nav.syfo.aktivitetskrav

import no.nav.syfo.infrastructure.database.repository.PAktivitetskrav
import no.nav.syfo.infrastructure.database.repository.PAktivitetskravVurdering
import no.nav.syfo.domain.Aktivitetskrav
import no.nav.syfo.domain.AktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import java.sql.Connection
import java.time.LocalDate
import java.util.*

interface IAktivitetskravRepository {

    fun getAktivitetskrav(uuid: UUID): PAktivitetskrav?

    fun getAktivitetskrav(
        personIdent: PersonIdent,
        connection: Connection? = null,
    ): List<PAktivitetskrav>

    fun getAktivitetskravForPersons(personidenter: List<PersonIdent>): List<Aktivitetskrav>

    fun getOutdatedAktivitetskrav(
        arenaCutoff: LocalDate,
        outdatedCutoff: LocalDate,
    ): List<PAktivitetskrav>

    fun createAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        previousAktivitetskravUuid: UUID? = null,
        referanseTilfelleBitUuid: UUID? = null,
        connection: Connection? = null,
    ): PAktivitetskrav

    fun createAktivitetskravVurdering(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ): PAktivitetskravVurdering

    fun updateAktivitetskravStatus(aktivitetskrav: Aktivitetskrav): PAktivitetskrav

    fun updateAktivitetskravPersonIdent(
        nyPersonIdent: PersonIdent,
        inactiveIdenter: List<PersonIdent>,
    ): Int
}
