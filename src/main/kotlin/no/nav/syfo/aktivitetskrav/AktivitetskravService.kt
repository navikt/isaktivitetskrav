package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.AktivitetskravVurderingRequestDTO
import no.nav.syfo.aktivitetskrav.database.*
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.sql.Connection
import java.util.*

class AktivitetskravService(
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val database: DatabaseInterface,
) {

    fun createAktivitetskrav(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
    ) {
        connection.createAktivitetskrav(aktivitetskrav = aktivitetskrav)
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = aktivitetskrav
        )
    }

    fun updateAktivitetskrav(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
        oppfolgingstilfelle: Oppfolgingstilfelle,
    ) {
        val updatedAktivitetskrav = aktivitetskrav.updateFrom(
            oppfolgingstilfelle = oppfolgingstilfelle,
        )

        updateAktivitetskrav(
            connection = connection,
            aktivitetskrav = updatedAktivitetskrav,
        )
    }

    private fun updateAktivitetskrav(
        connection: Connection,
        aktivitetskrav: Aktivitetskrav,
    ) {
        connection.updateAktivitetskrav(
            aktivitetskrav = aktivitetskrav
        )
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskrav = aktivitetskrav
        )
    }

    internal fun getAktivitetskrav(personIdent: PersonIdent): List<Aktivitetskrav> =
        database.getAktivitetskrav(personIdent = personIdent).toAktivitetskravList()

    internal fun vurderAktivitetskrav(
        aktivitetskrav: Aktivitetskrav,
        aktivitetskravVurderingRequestDTO: AktivitetskravVurderingRequestDTO,
        veilederIdent: String,
    ) {
        val updatedAktivitetskrav = aktivitetskrav.vurder(
            status = aktivitetskravVurderingRequestDTO.status,
            beskrivelse = aktivitetskravVurderingRequestDTO.beskrivelse,
            vurdertAv = veilederIdent,
        )

        database.connection.use { connection ->
            updateAktivitetskrav(
                connection = connection,
                aktivitetskrav = updatedAktivitetskrav,
            )
            connection.commit()
        }
    }

    internal fun getAktivitetskrav(uuid: UUID): Aktivitetskrav? =
        database.getAktivitetskrav(uuid = uuid)?.toAktivitetskrav()
}
