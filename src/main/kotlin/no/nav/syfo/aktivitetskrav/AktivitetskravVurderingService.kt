package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.database.createAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.database.updateAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.sql.Connection

class AktivitetskravVurderingService(
    private val aktivitetskravVurderingProducer: AktivitetskravVurderingProducer,
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
        val status = AktivitetskravVurdering.status(oppfolgingstilfelle)
        val stoppunktDato = AktivitetskravVurdering.stoppunktDato(oppfolgingstilfelle.tilfelleStart)
        val updatedAktivitetskravVurdering = aktivitetskravVurdering.copy(
            status = status,
            stoppunktAt = stoppunktDato,
        )

        updateAktivitetskravVurdering(
            connection = connection,
            aktivitetskravVurdering = updatedAktivitetskravVurdering,
        )
    }

    fun updateAktivitetskravVurdering(
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
}
