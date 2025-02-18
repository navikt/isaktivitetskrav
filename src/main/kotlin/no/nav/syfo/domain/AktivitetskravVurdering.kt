package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.lang.IllegalArgumentException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import kotlin.also
import kotlin.collections.isNotEmpty

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

data class AktivitetskravVurdering(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val createdBy: String,
    val status: AktivitetskravStatus,
    val arsaker: List<VurderingArsak>,
    val beskrivelse: String?,
    val stansFom: LocalDate?,
    val frist: LocalDate?,
    val varsel: AktivitetskravVarsel?,
) {

    fun isFinal() = this.status.isFinal

    fun requiresVarselPdf(): Boolean = this.status.toVarselType() != null

    companion object {
        fun create(
            status: AktivitetskravStatus,
            createdBy: String,
            beskrivelse: String?,
            arsaker: List<VurderingArsak> = emptyList(),
            stansFom: LocalDate? = null,
            frist: LocalDate? = null,
        ): AktivitetskravVurdering {
            return AktivitetskravVurdering(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                createdBy = createdBy,
                status = status,
                beskrivelse = beskrivelse,
                arsaker = arsaker,
                stansFom = stansFom,
                frist = frist,
                varsel = null,
            ).also { it.validate() }
        }
    }
}

fun AktivitetskravVurdering.validate() {
    if (!status.isAllowedChangedVurderingStatus()) {
        throw IllegalArgumentException("Can't create vurdering with status $status")
    }
    if (!status.requiresVurderingArsak() && arsaker.isNotEmpty()) {
        throw IllegalArgumentException("$status should not have arsak")
    }
    if (status.requiresVurderingArsak() && arsaker.isEmpty()) {
        throw IllegalArgumentException("Must have arsak for status $status")
    }
    if (status == AktivitetskravStatus.INNSTILLING_OM_STANS && stansFom == null) {
        throw IllegalArgumentException("Must have stansFom for status $status")
    }
}
