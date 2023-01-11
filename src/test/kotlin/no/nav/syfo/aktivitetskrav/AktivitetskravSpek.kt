package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.oppfolgingstilfelle.kafka.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.generator.createKafkaOppfolgingstilfellePerson
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime

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
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo true
        }
        it("returns false when equal arbeidstaker and stoppunkt after tilfelle end") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = sevenWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle shouldBeEqualTo false
        }
    }

    describe("update from Oppfolgingstilfelle") {
        it("updates stoppunktAt and sistEndret") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = tenWeeksAgo
                ).copy(
                    sistEndret = OffsetDateTime.now().minusDays(1)
                )

            val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle)

            updatedAktivitetskrav.sistEndret shouldBeGreaterThan aktivitetskrav.sistEndret
            updatedAktivitetskrav.stoppunktAt shouldBeEqualTo nineWeeksAgo.plusWeeks(8)
        }
    }

    describe("vurder aktivitetskrav") {
        it("updates vurderinger, status, sistEndret") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = tenWeeksAgo
                ).copy(
                    sistEndret = OffsetDateTime.now().minusDays(1)
                )
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

            val avventSistEndret = updatedAktivitetskrav.sistEndret
            avventSistEndret shouldBeGreaterThan aktivitetskrav.sistEndret
            updatedAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT
            updatedAktivitetskrav.vurderinger shouldBeEqualTo listOf(avventVurdering)

            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            updatedAktivitetskrav.sistEndret shouldBeGreaterThan avventSistEndret
            updatedAktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
            updatedAktivitetskrav.vurderinger shouldBeEqualTo listOf(oppfyltVurdering, avventVurdering)
        }
    }

    describe("toKafkaAktivitetskravVurdering") {
        it("sets updatedBy, beskrivelse and arsaker from latest vurdering") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = tenWeeksAgo
                )
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

            kafkaAktivitetskravVurdering.updatedAt shouldBeEqualTo updatedAktivitetskrav.sistEndret
            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo UserConstants.OTHER_VEILEDER_IDENT
            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo "Oppfylt"
            kafkaAktivitetskravVurdering.arsaker shouldBeEqualTo listOf(VurderingArsak.FRISKMELDT.name)
        }
        it("updatedBy and beskrivelse is null when not vurdert") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = tenWeeksAgo
                )

            val kafkaAktivitetskravVurdering = aktivitetskrav.toKafkaAktivitetskravVurdering()

            kafkaAktivitetskravVurdering.updatedBy shouldBeEqualTo null
            kafkaAktivitetskravVurdering.beskrivelse shouldBeEqualTo null
        }
    }
})
