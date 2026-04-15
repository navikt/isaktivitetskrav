package no.nav.syfo.infrastructure.client.veiledertilgang

data class Tilgang(
    val erGodkjent: Boolean,
    val erAvslatt: Boolean = false,
    val fullTilgang: Boolean = false,
)
