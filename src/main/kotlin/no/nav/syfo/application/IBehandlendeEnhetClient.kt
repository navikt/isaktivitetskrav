package no.nav.syfo.application

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.clients.behandlendeenhet.BehandlendeEnhetResponseDTO

interface IBehandlendeEnhetClient {
    suspend fun getEnhet(
        personident: PersonIdent,
    ): BehandlendeEnhetResponseDTO?
}
