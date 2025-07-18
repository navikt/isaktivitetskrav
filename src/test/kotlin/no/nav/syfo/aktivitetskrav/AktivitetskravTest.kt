package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.api.Arsak
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.oppfolgingstilfelle.kafka.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.generator.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)
private val tenWeeksAgo = LocalDate.now().minusWeeks(10)
private val twoWeeksFromNow = LocalDate.now().plusWeeks(2)

class AktivitetskravTest {

    private val oppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
        personIdent = ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        false,
    ).toLatestOppfolgingstilfelle()!!

    @Nested
    @DisplayName("Gjelder oppfolgingstilfelle")
    inner class GjelderOppfolgingstilfelle {

        @Test
        fun `returns false when different arbeidstakere`() {
            val aktivitetskrav = createAktivitetskravNy(
                personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
                tilfelleStart = nineWeeksAgo,
            )

            assertFalse(aktivitetskrav gjelder oppfolgingstilfelle)
        }

        @Test
        fun `returns true when equal arbeidstaker and stoppunkt after tilfelle start and before tilfelle end`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = nineWeeksAgo)

            assertTrue(aktivitetskrav gjelder oppfolgingstilfelle)
        }

        @Test
        fun `returns false when equal arbeidstaker and stoppunkt after tilfelle end`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = sevenWeeksAgo)

            assertFalse(aktivitetskrav gjelder oppfolgingstilfelle)
        }

        @Test
        fun `returns true when equal arbeidstaker and stoppunkt after tilfelle start and equal to tilfelle end`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(8))

            assertTrue(aktivitetskrav gjelder oppfolgingstilfelle)
        }
    }

    @Nested
    @DisplayName("shouldUpdateStoppunkt")
    inner class ShouldUpdateStoppunkt {

        @Test
        fun `true if stoppunkt changed and after arenaCutoff`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate =
                aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle, arenaCutoff = LocalDate.now().minusWeeks(4))

            assertTrue(shouldUpdate)
        }

        @Test
        fun `false if stoppunkt is unchanged`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate = aktivitetskrav.shouldUpdateStoppunkt(
                oppfolgingstilfelle = oppfolgingstilfelle.copy(tilfelleStart = tenWeeksAgo),
                arenaCutoff = LocalDate.now().minusWeeks(4)
            )

            assertFalse(shouldUpdate)
        }

        @Test
        fun `false if stoppunkt before arenaCutoff`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val shouldUpdate =
                aktivitetskrav.shouldUpdateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle, arenaCutoff = LocalDate.now())

            assertFalse(shouldUpdate)
        }
    }

    @Nested
    @DisplayName("Update stoppunkt")
    inner class UpdateStoppunkt {

        @Test
        fun `updates stoppunktAt`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val updatedAktivitetskrav = aktivitetskrav.updateStoppunkt(oppfolgingstilfelle = oppfolgingstilfelle)

            assertEquals(nineWeeksAgo.plusWeeks(8).minusDays(1), updatedAktivitetskrav.stoppunktAt)
        }
    }

    @Nested
    @DisplayName("Vurder aktivitetskrav")
    inner class VurderAktivitetskrav {

        @Test
        fun `updates vurderinger and status`() {
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

            assertEquals(AktivitetskravStatus.AVVENT, updatedAktivitetskrav.status)
            assertEquals(listOf(avventVurdering), updatedAktivitetskrav.vurderinger)

            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            assertEquals(AktivitetskravStatus.OPPFYLT, updatedAktivitetskrav.status)
            assertEquals(listOf(oppfyltVurdering, avventVurdering), updatedAktivitetskrav.vurderinger)
        }

        @Test
        fun `kan vurdere IKKE_OPPFYLT uten arsak`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravIkkeOppfylt(nyAktivitetskrav = aktivitetskrav)

            assertEquals(AktivitetskravStatus.IKKE_OPPFYLT, ikkeOppfyltAktivitetskrav.status)
        }

        @Test
        fun `kan vurdere IKKE_AKTUELL uten arsak`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravIkkeAktuell(nyAktivitetskrav = aktivitetskrav)

            assertEquals(AktivitetskravStatus.IKKE_AKTUELL, ikkeOppfyltAktivitetskrav.status)
        }

        @Test
        fun `kan ikke lage vurdering med status NY`() {
            assertThrows<IllegalArgumentException> {
                AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.NY,
                    createdBy = UserConstants.VEILEDER_IDENT,
                    beskrivelse = null,
                    arsaker = emptyList(),
                )
            }
        }

        @Test
        fun `kan ikke lage vurdering med status AUTOMATISK_OPPFYLT`() {
            assertThrows<IllegalArgumentException> {
                AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.AUTOMATISK_OPPFYLT,
                    createdBy = UserConstants.VEILEDER_IDENT,
                    beskrivelse = null,
                    arsaker = emptyList(),
                )
            }
        }

        @Test
        fun `kan vurdere INNSTILLING_OM_STANS uten arsak og frist`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)
            val ikkeOppfyltAktivitetskrav = createAktivitetskravInnstillingOmStans(nyAktivitetskrav = aktivitetskrav)

            assertEquals(AktivitetskravStatus.INNSTILLING_OM_STANS, ikkeOppfyltAktivitetskrav.status)
        }
    }

    @Nested
    @DisplayName("toKafkaAktivitetskravVurdering")
    inner class ToKafkaAktivitetskravVurdering {

        @Test
        fun `sets updatedBy, sisteVurderingUuid, sistVurdert, beskrivelse, frist and arsaker from latest vurdering`() {
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
            assertEquals(twoWeeksFromNow, aktivitetskravVurderingRecord.frist)

            updatedAktivitetskrav = updatedAktivitetskrav.vurder(
                aktivitetskravVurdering = oppfyltVurdering
            )

            aktivitetskravVurderingRecord = AktivitetskravVurderingRecord.from(updatedAktivitetskrav)

            assertEquals(UserConstants.OTHER_VEILEDER_IDENT, aktivitetskravVurderingRecord.updatedBy)
            assertEquals("Oppfylt", aktivitetskravVurderingRecord.beskrivelse)
            assertEquals(listOf(Arsak.FRISKMELDT.toString()), aktivitetskravVurderingRecord.arsaker)
            assertEquals(oppfyltVurdering.createdAt, aktivitetskravVurderingRecord.sistVurdert)
            assertEquals(oppfyltVurdering.uuid, aktivitetskravVurderingRecord.sisteVurderingUuid)
            assertNull(aktivitetskravVurderingRecord.frist)
        }

        @Test
        fun `updatedBy, sisteVurderingUuid, sistVurdert, frist and beskrivelse is null when not vurdert`() {
            val aktivitetskrav = createAktivitetskravNy(tilfelleStart = tenWeeksAgo)

            val aktivitetskravVurderingRecord = AktivitetskravVurderingRecord.from(aktivitetskrav)

            assertNull(aktivitetskravVurderingRecord.updatedBy)
            assertNull(aktivitetskravVurderingRecord.beskrivelse)
            assertNull(aktivitetskravVurderingRecord.sistVurdert)
            assertNull(aktivitetskravVurderingRecord.frist)
            assertNull(aktivitetskravVurderingRecord.sisteVurderingUuid)
        }
    }
}
