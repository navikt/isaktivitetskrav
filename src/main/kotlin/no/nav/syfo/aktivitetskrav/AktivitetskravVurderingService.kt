package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.database.createAktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import java.sql.Connection

class AktivitetskravVurderingService {

    fun createAktivitetskravVurdering(connection: Connection, aktivitetskravVurdering: AktivitetskravVurdering) {
        connection.createAktivitetskravVurdering(aktivitetskravVurdering = aktivitetskravVurdering)
        // TODO: Publisere på kafka
    }
}
