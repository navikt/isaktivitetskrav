package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.Arsak
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.oppfolgingstilfelle.kafka.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)
private val twoWeeksFromNow = LocalDate.now().plusWeeks(2)

class AktivitetskravSpek : Spek({
    val oppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
        personIdent = ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        false,
    ).toLatestOppfolgingstilfelle()!!

    describe("gjelder Oppfolgingstilfelle") {
        it("returns false when different arbeidstakere") {
            val aktivitetskrav = createAktivitetskravNy(
                personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = nineWeeksAgo,
            )

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo false
        }
        it("returns true when equal arbeidstaker and stoppunkt after tilfelle start and before tilfelle end") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = nineWeeksAgo)

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo true
        }
        it("returns false when equal arbeidstaker and stoppunkt after tilfelle end") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = sevenWeeksAgo)

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo false
        }
        it("returns true when equal arbeidstaker and stoppunkt after tilfelle start and equal to tilfelle end") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(8))

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo true
        }
    }

    describe("shouldUpdateStoppunkt") {
        it("true if stoppunkt changed and after arenaCutoff") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate =
                aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle, arenaCutoff = LocalDate.now().minusWeeks(4))

            shouldUpdate shouldBeEqualTo true
        }
        it("false if stoppunkt is unchanged") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate = aktivitetskrav.shouldUpdateStoppunkt(
                oppfolgingstilfelle = oppfolgingstilfelle.copy(tilfelleStart = tenWeeksAgo),
                arenaCutoff = LocalDate.now().minusWeeks(4)
            )

            shouldUpdate shouldBeEqualTo false
        }
        it("false if stoppunkt before arenaCutoff") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate =
                aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle, arenaCutoff = LocalDate.now())

            shouldUpdate shouldBeEqualTo false
        }
    }

    describe("updateStoppunkt") {
        it("updates stoppunktAt") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle)

            updatedAktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8).minusDays(1)
        }
    }

    describe("vurder aktivitetskrav") {
        it("updates vurderinger and status") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val avventVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.AVVENT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Avvent",
                arsaker = listOf(Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER),
            )
            val oppfyltVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Oppfylt",
                arsaker = listOf(Arsak.FRISKMELDT),
            )

            var updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = avventVurdering)

            updatedAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT
            updatedAktivitetskrav.vurderinger shouldBeEqualTo listOf(avventVurdering)

            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            updatedAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
            updatedAktivitetskrav.vurderinger shouldBeEqualTo listOf(oppfyltVurdering, avventVurdering)
        }
        it("kan vurdere IKKE_OPPFYLT uten arsak") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravIkkeOppfylt(nyAktivitetskrav = aktivitetskrav)

            ikkeOppfyltAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.IKKE_OPPFYLT
        }
        it("kan vurdere IKKE_AKTUELL uten arsak") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravIkkeAktuell(nyAktivitetskrav = aktivitetskrav)

            ikkeOppfyltAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.IKKE_AKTUELL
        }
        EnumSet.of(AktivitetskravStatus.NY, AktivitetskravStatus.AUTOMATISK_OPPFYLT)
            .forEach {
                it("kan ikke lage vurdering med status $it") {
                    assertFailsWith(IllegalArgumentException::class) {
                        AktivitetskravVurdering.create(
                            status = it,
                            createdBy = UserConstants.VEILEDER_IDENT,
                            beskrivelse = null,
                            arsaker = emptyList(),
                        )
                    }
                }
            }

        it("kan vurdere INNSTILLING_OM_STANS uten arsak og frist") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravInnstillingOmStans(nyAktivitetskrav = aktivitetskrav)

            ikkeOppfyltAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.INNSTILLING_OM_STANS
        }
    }

    describe("toKafkaAktivitetskravVurdering") {
        it("sets updatedBy, sisteVurderingUuid, sistVurdert, beskrivelse, frist and arsaker from latest vurdering") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val avventVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.AVVENT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Avvent",
                arsaker = listOf(Arsak.OPPFOLGINGSPLAN_ARBEIDSGIVER),
                frist = twoWeeksFromNow,
            )
            val oppfyltVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                createdBy = UserConstants.OTHER_VEILEDER_IDENT,
                beskrivelse = "Oppfylt",
                arsaker = listOf(Arsak.FRISKMELDT),
            )

            var updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = avventVurdering)
            var aktivitetskravVurderingRecord = AktivitetskravVurderingRecord.from(updatedAktivitetskrav)
            aktivitetskravVurderingRecord.frist shouldBeEqualTo twoWeeksFromNow

            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            aktivitetskravVurderingRecord = AktivitetskravVurderingRecord.from(updatedAktivitetskrav)

            aktivitetskravVurderingRecord.updatedBy shouldBeEqualTo UserConstants.OTHER_VEILEDER_IDENT
            aktivitetskravVurderingRecord.beskrivelse shouldBeEqualTo "Oppfylt"
            aktivitetskravVurderingRecord.arsaker shouldBeEqualTo listOf(Arsak.FRISKMELDT.toString())
            aktivitetskravVurderingRecord.sistVurdert shouldBeEqualTo oppfyltVurdering.createdAt
            aktivitetskravVurderingRecord.sisteVurderingUuid shouldBeEqualTo oppfyltVurdering.uuid
            aktivitetskravVurderingRecord.frist shouldBeEqualTo null
        }
        it("updatedBy, sisteVurderingUuid, sistVurdert, frist and beskrivelse is null when not vurdert") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val aktivitetskravVurderingRecord = AktivitetskravVurderingRecord.from(aktivitetskrav)

            aktivitetskravVurderingRecord.updatedBy shouldBeEqualTo null
            aktivitetskravVurderingRecord.beskrivelse shouldBeEqualTo null
            aktivitetskravVurderingRecord.sistVurdert shouldBeEqualTo null
            aktivitetskravVurderingRecord.frist shouldBeEqualTo null
            aktivitetskravVurderingRecord.sisteVurderingUuid shouldBeEqualTo null
        }
    }
})
