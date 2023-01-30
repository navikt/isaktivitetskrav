package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.*
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
import java.util.EnumSet

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)

class AktivitetskravSpek : Spek({
    val oppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
        personIdent = ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        false,
    ).toLatestOppfolgingstilfelle()!!

    describe("gjelder Oppfolgingstilfelle") {
        it("returns false when different arbeidstakere") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo false
        }
        it("returns true when equal arbeidstaker and stoppunkt between tilfelle start and end") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = nineWeeksAgo)

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo true
        }
        it("returns false when equal arbeidstaker and stoppunkt after tilfelle end") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = sevenWeeksAgo)

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo false
        }
    }

    describe("updateStoppunkt") {
        it("updates stoppunktAt") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle)

            updatedAktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)
        }
    }

    describe("vurder aktivitetskrav") {
        it("updates vurderinger and status") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val avventVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.AVVENT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Avvent",
                arsaker = listOf(VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER),
            )
            val oppfyltVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Oppfylt",
                arsaker = listOf(VurderingArsak.FRISKMELDT),
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
        it("kan ikke vurdere IKKE_OPPFYLT med arsak") {
            assertFailsWith(IllegalArgumentException::class) {
                AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.IKKE_OPPFYLT,
                    createdBy = UserConstants.VEILEDER_IDENT,
                    beskrivelse = null,
                    arsaker = listOf(VurderingArsak.ANNET),
                )
            }
        }
        EnumSet.of(AktivitetskravStatus.NY, AktivitetskravStatus.AUTOMATISK_OPPFYLT, AktivitetskravStatus.STANS)
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
    }

    describe("toKafkaAktivitetskravVurdering") {
        it("sets updatedBy, sistVurdert, beskrivelse and arsaker from latest vurdering") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val avventVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.AVVENT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Avvent",
                arsaker = listOf(VurderingArsak.OPPFOLGINGSPLAN_ARBEIDSGIVER),
            )
            val oppfyltVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                createdBy = UserConstants.OTHER_VEILEDER_IDENT,
                beskrivelse = "Oppfylt",
                arsaker = listOf(VurderingArsak.FRISKMELDT),
            )

            var updatedAktivitetskrav = aktivitetskrav.vurder(aktivitetskravVurdering = avventVurdering)
            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            val kafkaAktivitetskravVurdering = updatedAktivitetskrav.toKafkaAktivitetskravVurdering()

            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.OTHER_VEILEDER_IDENT
            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Oppfylt"
            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
            kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo oppfyltVurdering.createdAt
        }
        it("updatedBy, sistVurdert and beskrivelse is null when not vurdert") {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val kafkaAktivitetskravVurdering = aktivitetskrav.toKafkaAktivitetskravVurdering()

            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
            kafkaAktivitetskravVurdering.sistVurdert shouldBeEqualTo null
        }
    }

    describe("created from vurdering") {
        it("has vurdering, status and stoppunkt today") {
            val oppfyltVurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.OPPFYLT,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "Oppfylt",
                arsaker = listOf(VurderingArsak.FRISKMELDT),
            )

            val aktivitetskrav = Aktivitetskrav.fromVurdering(
                personIdent = ARBEIDSTAKER_PERSONIDENT,
                vurdering = oppfyltVurdering,
            )

            aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
            aktivitetskrav.vurderinger shouldBeEqualTo listOf(oppfyltVurdering)
            aktivitetskrav.stoppunktAt shouldBeEqualTo LocalDate.now()
        }
    }
})
