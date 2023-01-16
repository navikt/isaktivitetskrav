package no.nav.syfo.testhelper.generator

import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate

fun createAktivitetskravNy(tilfelleStart: LocalDate): Aktivitetskrav = Aktivitetskrav.ny(
    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    tilfelleStart = tilfelleStart,
)

fun createAktivitetskravAutomatiskOppfylt(tilfelleStart: LocalDate): Aktivitetskrav = Aktivitetskrav.automatiskOppfylt(
    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    tilfelleStart = tilfelleStart,
)

fun createAktivitetskravOppfylt(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val oppfyltVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.OPPFYLT,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Oppfylt",
        arsaker = listOf(VurderingArsak.FRISKMELDT),
    )
    return nyAktivitetskrav.vurder(oppfyltVurdering)
}

fun createAktivitetskravAvvent(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val avventVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.AVVENT,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Avvent",
        arsaker = listOf(VurderingArsak.INFORMASJON_BEHANDLER, VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER),
    )
    return nyAktivitetskrav.vurder(avventVurdering)
}

fun createAktivitetskravUnntak(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val unntakVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.UNNTAK,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Unntak",
        arsaker = listOf(VurderingArsak.SJOMENN_UTENRIKS),
    )
    return nyAktivitetskrav.vurder(unntakVurdering)
}

fun createAktivitetskravIkkeOppfylt(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val ikkeOppfyltVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.IKKE_OPPFYLT,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = null,
        arsaker = emptyList(),
    )

    return nyAktivitetskrav.vurder(aktivitetskravVurdering = ikkeOppfyltVurdering)
}
