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

class AktivitetskravVurderingService(
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
    private val database: DatabaseInterface,
) {

    fun createAktivitetskravVurdering(
        connection: Connection,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        connection.createAktivitetskravVurdering(aktivitetskravVurdering = aktivitetskravVurdering)
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskravVurdering = aktivitetskravVurdering
        )
    }

    fun updateAktivitetskravVurdering(
        connection: Connection,
        aktivitetskravVurdering: AktivitetskravVurdering,
        oppfolgingstilfelle: Oppfolgingstilfelle,
    ) {
        val updatedAktivitetskravVurdering = aktivitetskravVurdering.updateFrom(
            oppfolgingstilfelle = oppfolgingstilfelle,
        )

        updateAktivitetskravVurdering(
            connection = connection,
            aktivitetskravVurdering = updatedAktivitetskravVurdering,
        )
    }

    private fun updateAktivitetskravVurdering(
        connection: Connection,
        aktivitetskravVurdering: AktivitetskravVurdering,
    ) {
        connection.updateAktivitetskravVurdering(
            aktivitetskravVurdering = aktivitetskravVurdering
        )
        aktivitetskravVurderingProducer.sendAktivitetskravVurdering(
            aktivitetskravVurdering = aktivitetskravVurdering
        )
    }

    internal fun getAktivitetskravVurderinger(personIdent: PersonIdent): List<AktivitetskravVurdering> =
        database.getAktivitetskravVurderinger(personIdent = personIdent).toAktivitetskravVurderinger()

    internal fun vurderAktivitetskrav(
        aktivitetskravVurdering: AktivitetskravVurdering,
        aktivitetskravVurderingRequestDTO: AktivitetskravVurderingRequestDTO,
        veilederIdent: String,
    ) {
        val updatedAktivitetskravVurdering = aktivitetskravVurdering.vurder(
            status = aktivitetskravVurderingRequestDTO.status,
            beskrivelse = aktivitetskravVurderingRequestDTO.beskrivelse,
            vurdertAv = veilederIdent,
        )

        database.connection.use { connection ->
            updateAktivitetskravVurdering(
                connection = connection,
                aktivitetskravVurdering = updatedAktivitetskravVurdering,
            )
            connection.commit()
        }
    }

    internal fun getAktivitetskravVurdering(uuid: UUID): AktivitetskravVurdering? =
        database.getAktivitetskravVurdering(uuid = uuid)?.toAktivitetskravVurdering()
}
