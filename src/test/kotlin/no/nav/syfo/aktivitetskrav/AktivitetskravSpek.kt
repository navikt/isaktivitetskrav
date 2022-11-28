package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.gjelder
import no.nav.syfo.oppfolgingstilfelle.kafka.toLatestOppfolgingstilfelle
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.generator.createKafkaOppfolgingstilfellePerson
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

private val sevenWeeksAgo = LocalDate.now().minusWeeks(7)
private val nineWeeksAgo = LocalDate.now().minusWeeks(9)

class AktivitetskravSpek : Spek({
    val oppfolgingstilfelle = createKafkaOppfolgingstilfellePerson(
        personIdent = ARBEIDSTAKER_PERSONIDENT,
        tilfelleStart = nineWeeksAgo,
        tilfelleEnd = LocalDate.now(),
        false,
    ).toLatestOppfolgingstilfelle()

    describe("gjelder Oppfolgingstilfelle") {
        it("returns false when different arbeidstakere") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = OTHER_ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle!! shouldBeEqualTo false
        }
        it("returns true when equal arbeidstaker and stoppunkt between tilfelle start and end") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = nineWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle!! shouldBeEqualTo true
        }
        it("returns false when equal arbeidstaker and stoppunkt after tilfelle end") {
            val aktivitetskrav =
                Aktivitetskrav.ny(
                    personIdent = ARBEIDSTAKER_PERSONIDENT,
                    tilfelleStart = sevenWeeksAgo
                )

            aktivitetskrav gjelder oppfolgingstilfelle!! shouldBeEqualTo false
        }
    }
})