package no.nav.syfo.domain

import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class AktivitetskravVarselSpek : Spek({
    val forhandsvarsel = AktivitetskravVarsel.create(
        type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
        document = generateDocumentComponentDTO(fritekst = "Dette er et forhåndsvarsel")
    )

    describe("isExpired") {
        it("returns true when svarfrist is today") {
            val isExpired = forhandsvarsel.copy(
                svarfrist = LocalDate.now()
            ).toVarselResponseDTO().isExpired

            isExpired shouldBeEqualTo true
        }
        it("returns true when svarfrist is before today") {
            val isExpired = forhandsvarsel.copy(
                svarfrist = LocalDate.now().minusDays(1)
            ).toVarselResponseDTO().isExpired

            isExpired shouldBeEqualTo true
        }
        it("returns false when svarfrist is after today") {
            val isExpired = forhandsvarsel.copy(
                svarfrist = LocalDate.now().plusDays(1)
            ).toVarselResponseDTO().isExpired

            isExpired shouldBeEqualTo false
        }
        it("returns false when svarfrist is null") {
            val unntakVarsel = AktivitetskravVarsel.create(
                type = VarselType.UNNTAK,
                document = generateDocumentComponentDTO(fritekst = "Dette er et unntak")
            )
            val isExpired = unntakVarsel.toVarselResponseDTO().isExpired

            isExpired shouldBeEqualTo false
        }
    }
})
