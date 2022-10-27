package no.nav.syfo.testhelper

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Virksomhetsnummer

object UserConstants {

    private const val ARBEIDSTAKER_FNR = "12345678912"
    private const val VIRKSOMHETSNUMMER = "123456789"

    val ARBEIDSTAKER_PERSONIDENT = PersonIdent(ARBEIDSTAKER_FNR)
    val VIRKSOMHETSNUMMER_DEFAULT = Virksomhetsnummer(VIRKSOMHETSNUMMER)
}
