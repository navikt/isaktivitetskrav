package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed class VurderingArsak(val value: String) {

    sealed class Avvent(value: String) : VurderingArsak(value) {
        data object OppfolgingsplanArbeidsgiver : Avvent("OPPFOLGINGSPLAN_ARBEIDSGIVER")
        data object InformasjonBehandler : Avvent("INFORMASJON_BEHANDLER")
        data object InformasjonSykmeldt : Avvent("INFORMASJON_SYKMELDT")
        data object DroftesMedROL : Avvent("DROFTES_MED_ROL")
        data object DroftesInternt : Avvent("DROFTES_INTERNT")
        data object Annet : Avvent("ANNET")
    }

    sealed class Unntak(value: String) : VurderingArsak(value) {
        data object MedisinskeGrunner : Unntak("MEDISINSKE_GRUNNER")
        data object TilretteleggingIkkeMulig : Unntak("TILRETTELEGGING_IKKE_MULIG")
        data object SjomennUtenriks : Unntak("SJOMENN_UTENRIKS")
    }

    sealed class Oppfylt(value: String) : VurderingArsak(value) {
        data object Friskmeldt : Oppfylt("FRISKMELDT")
        data object Gradert : Oppfylt("GRADERT")
        data object Tiltak : Oppfylt("TILTAK")
    }

    sealed class IkkeAktuell(value: String) : VurderingArsak(value) {
        data object InnvilgetVTA : Oppfylt("INNVILGET_VTA")
        data object MottarAAP : Oppfylt("MOTTAR_AAP")
        data object ErDod : Oppfylt("ER_DOD")
        data object Annet : Oppfylt("ANNET")
    }
}

sealed class AktivitetskravVurdering(
    open val uuid: UUID,
    open val createdAt: OffsetDateTime,
    open val createdBy: String,
    val status: AktivitetskravStatus,
    val isFinal: Boolean,
) {

    //TODO: Kan dette være en vurdering?
    data class NyVurdering(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.NY_VURDERING, isFinal = false)

    data class Avvent(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val arsaker: List<Arsak>,
        val beskrivelse: String,
        val frist: LocalDate?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.AVVENT, isFinal = false) {
        enum class Arsak(val value: String) {
            OppfolgingsplanArbeidsgiver("OPPFOLGINGSPLAN_ARBEIDSGIVER"),
            InformasjonBehandler("INFORMASJON_BEHANDLER"),
            InformasjonSykmeldt("INFORMASJON_SYKMELDT"),
            DroftesMedROL("DROFTES_MED_ROL"),
            DroftesInternt("DROFTES_INTERNT"),
            Annet("ANNET"),
        }
    }

    data class Unntak(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val arsaker: List<Arsak>,
        val beskrivelse: String?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.UNNTAK, isFinal = true) {
        enum class Arsak(val value: String) {
            MedisinskeGrunner("MEDISINSKE_GRUNNER"),
            TilretteleggingIkkeMulig("TILRETTELEGGING_IKKE_MULIG"),
            SjomennUtenriks("SJOMENN_UTENRIKS"),
        }
    }

    // TODO: beskrivelse ikke påkrevd?
    data class IkkeOppfylt(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val beskrivelse: String?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.IKKE_OPPFYLT, isFinal = true)

    data class Oppfylt(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val arsaker: List<Arsak>,
        val beskrivelse: String,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.OPPFYLT, isFinal = true) {
        enum class Arsak(val value: String) {
            Friskmeldt("FRISKMELDT"),
            Gradert("GRADERT"),
            Tiltak("TILTAK"),
        }
    }

    // TODO: Er ikke beskrivelse obligatorisk?
    data class IkkeAktuell(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val arsaker: List<Arsak>,
        val beskrivelse: String?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.IKKE_AKTUELL, isFinal = true) {
        enum class Arsak(val value: String) {
            InnvilgetVTA("INNVILGET_VTA"),
            MottarAAP("MOTTAR_AAP"),
            ErDod("ER_DOD"),
            Annet("ANNET"),
        }
    }

    // TODO: Er frist en del av forhandsvarsel?
    // TODO: Forhandsvarsel skal alltid ha et varsel
    data class Forhandsvarsel(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val beskrivelse: String,
        val varsel: AktivitetskravVarsel?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.FORHANDSVARSEL, isFinal = false)

    data class InnstillingOmStans(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        val stansFom: LocalDate,
        val beskrivelse: String,
        val varsel: AktivitetskravVarsel?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, status = AktivitetskravStatus.INNSTILLING_OM_STANS, isFinal = true)

    fun beskrivelse() =
        when (this) {
            is Avvent -> beskrivelse
            is Unntak -> beskrivelse
            is IkkeOppfylt -> beskrivelse
            is Oppfylt -> beskrivelse
            is IkkeAktuell -> beskrivelse
            is Forhandsvarsel -> beskrivelse
            is InnstillingOmStans -> beskrivelse
            else -> null
        }

    fun arsaker(): List<String> =
        when (this) {
            is Avvent -> arsaker.map { it.value }
            is Unntak -> arsaker.map { it.value }
            is Oppfylt -> arsaker.map { it.value }
            is IkkeAktuell -> arsaker.map { it.value }
            else -> emptyList()
        }

    fun varsel(): AktivitetskravVarsel? =
        when (this) {
            is Forhandsvarsel -> this.varsel
            is InnstillingOmStans -> this.varsel
            else -> null
        }

    fun requiresPdfDocument(): Boolean =
        this is Forhandsvarsel || this is Unntak || this is Oppfylt || this is IkkeAktuell || this is InnstillingOmStans

    fun toVarselType(): VarselType =
        when (this) {
            is Forhandsvarsel -> VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER
            is Unntak -> VarselType.UNNTAK
            is Oppfylt -> VarselType.OPPFYLT
            is IkkeAktuell -> VarselType.IKKE_AKTUELL
            is InnstillingOmStans -> VarselType.INNSTILLING_OM_STANS
            else -> throw IllegalArgumentException("Vurdering $this does not require varsel")
        }

    companion object {
        fun create(
            status: AktivitetskravStatus,
            createdBy: String,
            beskrivelse: String?,
            arsaker: List<VurderingArsak> = emptyList(),
            stansFom: LocalDate? = null,
            frist: LocalDate? = null,
            varsel: AktivitetskravVarsel? = null,
        ): AktivitetskravVurdering =
            when (status) {
                AktivitetskravStatus.NY_VURDERING -> NyVurdering(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                )
                AktivitetskravStatus.AVVENT -> Avvent(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    arsaker = arsaker.map { Avvent.Arsak.valueOf(it.toString()) },
                    beskrivelse = beskrivelse!!,
                    frist = frist,
                )
                AktivitetskravStatus.UNNTAK -> Unntak(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    arsaker = arsaker.map { Unntak.Arsak.valueOf(it.toString()) },
                    beskrivelse = beskrivelse,
                )
                AktivitetskravStatus.OPPFYLT -> Oppfylt(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    arsaker = arsaker.map { Oppfylt.Arsak.valueOf(it.toString()) },
                    beskrivelse = beskrivelse!!,
                )
                AktivitetskravStatus.IKKE_AKTUELL -> IkkeAktuell(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    arsaker = arsaker.map { IkkeAktuell.Arsak.valueOf(it.toString()) },
                    beskrivelse = beskrivelse,
                )
                AktivitetskravStatus.IKKE_OPPFYLT -> IkkeOppfylt(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    beskrivelse = beskrivelse,
                )
                AktivitetskravStatus.FORHANDSVARSEL -> Forhandsvarsel(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    beskrivelse = beskrivelse!!,
                    varsel = varsel,
                )
                AktivitetskravStatus.INNSTILLING_OM_STANS -> InnstillingOmStans(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    beskrivelse = beskrivelse!!,
                    stansFom = stansFom!!,
                    varsel = varsel,
                )
                //TODO: Denne skal ikke være nødvendig. Domenetypen bør kunne gjenspeile at bare veileders vurderinger skal være gyldige her
                else -> throw IllegalArgumentException("Status $status not supported as vurdering")
            }
    }
}
