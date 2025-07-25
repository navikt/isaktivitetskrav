package no.nav.syfo.domain

import no.nav.syfo.api.dto.Arsak
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed class AktivitetskravVurdering(
    open val uuid: UUID,
    open val createdAt: OffsetDateTime,
    open val createdBy: String,
    open val beskrivelse: String?,
    val status: AktivitetskravStatus,
    val isFinal: Boolean,
) {

    data class Avvent(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String,
        val arsaker: List<Arsak>,
        val frist: LocalDate?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.AVVENT, isFinal = false) {
        enum class Arsak {
            OPPFOLGINGSPLAN_ARBEIDSGIVER,
            INFORMASJON_BEHANDLER,
            INFORMASJON_SYKMELDT,
            DROFTES_MED_ROL,
            DROFTES_INTERNT,
            ANNET,
        }
    }

    data class Unntak(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String?,
        val arsaker: List<Arsak>,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.UNNTAK, isFinal = true) {
        enum class Arsak {
            MEDISINSKE_GRUNNER,
            TILRETTELEGGING_IKKE_MULIG,
            SJOMENN_UTENRIKS,
        }
    }

    data class IkkeOppfylt(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.IKKE_OPPFYLT, isFinal = true)

    data class Oppfylt(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String?,
        val arsaker: List<Arsak>,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.OPPFYLT, isFinal = true) {
        enum class Arsak {
            FRISKMELDT,
            GRADERT,
            TILTAK,
        }
    }

    data class IkkeAktuell(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String?,
        val arsaker: List<Arsak>,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.IKKE_AKTUELL, isFinal = true) {
        enum class Arsak {
            INNVILGET_VTA,
            MOTTAR_AAP,
            ER_DOD,
            ANNET,
        }
    }

    data class Forhandsvarsel(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String,
        val frist: LocalDate?,
        val varsel: AktivitetskravVarsel?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.FORHANDSVARSEL, isFinal = false)

    data class InnstillingOmStans(
        override val uuid: UUID,
        override val createdAt: OffsetDateTime,
        override val createdBy: String,
        override val beskrivelse: String,
        val stansFom: LocalDate,
        val varsel: AktivitetskravVarsel?,
    ) : AktivitetskravVurdering(uuid, createdAt, createdBy, beskrivelse, status = AktivitetskravStatus.INNSTILLING_OM_STANS, isFinal = true)

    fun frist() =
        when (this) {
            is Avvent -> frist
            is Forhandsvarsel -> frist
            else -> null
        }

    fun arsaker(): List<String> =
        when (this) {
            is Avvent -> arsaker.map { it.toString() }
            is Unntak -> arsaker.map { it.toString() }
            is Oppfylt -> arsaker.map { it.toString() }
            is IkkeAktuell -> arsaker.map { it.toString() }
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
            arsaker: List<Arsak> = emptyList(),
            stansFom: LocalDate? = null,
            frist: LocalDate? = null,
            varsel: AktivitetskravVarsel? = null,
        ): AktivitetskravVurdering =
            when (status) {
                AktivitetskravStatus.AVVENT -> Avvent(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    arsaker = arsaker.map { Avvent.Arsak.valueOf(it.toString()) },
                    beskrivelse = requireNotNull(beskrivelse),
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
                    beskrivelse = requireNotNull(beskrivelse),
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
                    beskrivelse = requireNotNull(beskrivelse),
                    frist = requireNotNull(frist),
                    varsel = varsel,
                )
                AktivitetskravStatus.INNSTILLING_OM_STANS -> InnstillingOmStans(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    createdBy = createdBy,
                    beskrivelse = requireNotNull(beskrivelse),
                    stansFom = requireNotNull(stansFom),
                    varsel = varsel,
                )
                else -> throw IllegalArgumentException("Status $status not supported as vurdering")
            }
    }
}
