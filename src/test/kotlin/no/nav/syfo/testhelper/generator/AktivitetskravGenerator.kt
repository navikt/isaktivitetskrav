package no.nav.syfo.testhelper.generator

import no.nav.syfo.api.dto.Arsak
import no.nav.syfo.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.UserConstants
import java.time.LocalDate

fun createAktivitetskravNy(
    tilfelleStart: LocalDate,
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
): Aktivitetskrav = Aktivitetskrav.create(
    personIdent = personIdent,
    oppfolgingstilfelleStart = tilfelleStart,
)

fun createAktivitetskravAutomatiskOppfylt(
    tilfelleStart: LocalDate,
    personIdent: PersonIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
): Aktivitetskrav = Aktivitetskrav.create(
    personIdent = personIdent,
    oppfolgingstilfelleStart = tilfelleStart,
    isAutomatiskOppfylt = true,
)

fun createAktivitetskravOppfylt(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val oppfyltVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.OPPFYLT,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Oppfylt",
        arsaker = listOf(Arsak.FRISKMELDT),
    )
    return nyAktivitetskrav.vurder(oppfyltVurdering)
}

fun createAktivitetskravAvvent(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val avventVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.AVVENT,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Avvent",
        arsaker = listOf(
            Arsak.INFORMASJON_BEHANDLER,
            Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER,
            Arsak.DROFTES_MED_ROL,
            Arsak.DROFTES_INTERNT,
        ),
    )
    return nyAktivitetskrav.vurder(avventVurdering)
}

fun createAktivitetskravUnntak(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val unntakVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.UNNTAK,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Unntak",
        arsaker = listOf(Arsak.SJOMENN_UTENRIKS),
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
        arsaker = listOf(Arsak.INNVILGET_VTA),
    )
    return nyAktivitetskrav.vurder(aktivitetskravVurdering = ikkeAktuellVurdering)
}

fun createAktivitetskravInnstillingOmStans(nyAktivitetskrav: Aktivitetskrav): Aktivitetskrav {
    val innstillingOmStansVurdering = AktivitetskravVurdering.create(
        status = AktivitetskravStatus.INNSTILLING_OM_STANS,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "Beskrivelse",
        stansFom = LocalDate.now(),
    )
    return nyAktivitetskrav.vurder(aktivitetskravVurdering = innstillingOmStansVurdering)
}

fun createVurdering(
    status: AktivitetskravStatus,
    arsaker: List<Arsak> = emptyList(),
    frist: LocalDate? = null,
) =
    AktivitetskravVurdering.create(
        status = status,
        createdBy = UserConstants.VEILEDER_IDENT,
        beskrivelse = "En test vurdering",
        arsaker = arsaker,
        frist = frist,
    )
