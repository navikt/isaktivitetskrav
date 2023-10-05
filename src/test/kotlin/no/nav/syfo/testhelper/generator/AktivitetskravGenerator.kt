package no.nav.syfo.testhelper.generator

import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate

fun createAktivitetskravNy(
    tilfelleStart: LocalDate,
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
): Aktivitetskrav = Aktivitetskrav.ny(
    personIdent = personIdent,
    tilfelleStart = tilfelleStart,
)

fun createAktivitetskravAutomatiskOppfylt(
    tilfelleStart: LocalDate,
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
): Aktivitetskrav = Aktivitetskrav.automatiskOppfylt(
    personIdent = personIdent,
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

fun createAktivitetskravIkkeAktuell(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val ikkeAktuellVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.IKKE_AKTUELL,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = null,
        arsaker = emptyList(),
    )

    return nyAktivitetskrav.vurder(aktivitetskravVurdering = ikkeAktuellVurdering)
}

fun createNAktivitetskrav(
    n: Int,
    tilfelleStart: LocalDate,
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
): List<Aktivitetskrav> {
    val allAktivitetskrav = mutableListOf<Aktivitetskrav>()
    for (i in 1..n) {
        val newAktivitetskrav = Aktivitetskrav.ny(
            personIdent,
            tilfelleStart
        )
        allAktivitetskrav.add(newAktivitetskrav)
    }
    return allAktivitetskrav.toList()
}

fun createVarsler(): List<AktivitetskravVarsel> {
    val document = generateDocumentComponentDTO(fritekst = "Et test varsel")
    return listOf(
        AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusWeeks(1)),
        AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusDays(1)),
        AktivitetskravVarsel.create(document, svarfrist = LocalDate.now()),
        AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().plusDays(1)),
        AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().plusWeeks(1)),
    )
}
