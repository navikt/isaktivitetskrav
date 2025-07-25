package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.infrastructure.kafka.oppfolgingstilfelle.durationInWeeks
import no.nav.syfo.testhelper.generator.generateOppfolgingstilfelle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class OppfolgingstilfelleTest {

    @Nested
    @DisplayName("Varighet uker")
    inner class VarighetUker {

        @Test
        fun `Calculates varighet based on start and end when antallSykedager missing - 19 days`() {
            val oppfolgingstilfelle = generateOppfolgingstilfelle(
                start = LocalDate.now().minusDays(19),
                end = LocalDate.now(),
                antallSykedager = null,
            )
            assertEquals(2, oppfolgingstilfelle.durationInWeeks())
        }

        @Test
        fun `Calculates varighet based on start and end when antallSykedager missing - 20 days`() {
            val oppfolgingstilfelle = generateOppfolgingstilfelle(
                start = LocalDate.now().minusDays(20),
                end = LocalDate.now(),
                antallSykedager = null,
            )
            assertEquals(3, oppfolgingstilfelle.durationInWeeks())
        }

        @Test
        fun `Calculates varighet based on start and end when end is in the future`() {
            val oppfolgingstilfelle = generateOppfolgingstilfelle(
                start = LocalDate.now().minusDays(19),
                end = LocalDate.now().plusDays(1),
                antallSykedager = null,
            )
            assertEquals(3, oppfolgingstilfelle.durationInWeeks())
        }

        @Test
        fun `Calculates varighet based on antallSykedager - 13 days`() {
            val oppfolgingstilfelle = generateOppfolgingstilfelle(
                start = LocalDate.now().minusDays(19),
                end = LocalDate.now(),
                antallSykedager = 13,
            )
            assertEquals(1, oppfolgingstilfelle.durationInWeeks())
        }

        @Test
        fun `Calculates varighet based on antallSykedager - 14 days`() {
            val oppfolgingstilfelle = generateOppfolgingstilfelle(
                start = LocalDate.now().minusDays(19),
                end = LocalDate.now(),
                antallSykedager = 14,
            )
            assertEquals(2, oppfolgingstilfelle.durationInWeeks())
        }
    }
}
