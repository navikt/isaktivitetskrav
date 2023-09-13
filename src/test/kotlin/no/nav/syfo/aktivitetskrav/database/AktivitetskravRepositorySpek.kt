package no.nav.syfo.aktivitetskrav.database

import io.ktor.server.testing.*
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class AktivitetskravRepositorySpek : Spek({

    describe(AktivitetskravRepositorySpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

            afterEachTest {
                database.dropData()
            }

            describe("Forhåndsvarsel") {
                val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                val aktivitetskrav = createAktivitetskravNy(
                    tilfelleStart = LocalDate.now(),
                    personIdent = personIdent,
                )
                val fritekst = "Et forhåndsvarsel"
                val document = generateDocumentComponentDTO(fritekst = fritekst)
                val forhandsvarselDTO = ForhandsvarselDTO(
                    fritekst = fritekst,
                    document = document,
                )

                beforeEachTest { database.createAktivitetskrav(aktivitetskrav) }

                it("Should update existing aktivitetskrav") {
                    val vurdering: AktivitetskravVurdering =
                        forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                    val forhandsvarsel = AktivitetskravVarsel.create(forhandsvarselDTO.document)
                    val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                    val pdf = byteArrayOf(0x2E, 100)

                    val newVarsel = aktivitetskravVarselRepository.create(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = forhandsvarsel,
                        pdf = pdf,
                    )

                    val retrievedAktivitetskrav = database.getAktivitetskrav(updatedAktivitetskrav.uuid)
                    val vurderinger = database.getAktivitetskravVurderinger(retrievedAktivitetskrav!!.id)
                    val newVurdering = vurderinger.first()
                    val newVarselPdf = aktivitetskravVarselRepository.getVarselPdf(newVarsel.id)

                    retrievedAktivitetskrav.status shouldBeEqualTo updatedAktivitetskrav.status.name
                    retrievedAktivitetskrav.updatedAt shouldBeGreaterThan retrievedAktivitetskrav.createdAt
                    vurderinger.size shouldBeEqualTo 1
                    newVurdering.aktivitetskravId shouldBeEqualTo retrievedAktivitetskrav.id
                    newVurdering.createdBy shouldBeEqualTo vurdering.createdBy
                    newVurdering.status shouldBeEqualTo vurdering.status.name
                    newVarsel.aktivitetskravVurderingId shouldBeEqualTo newVurdering.id
                    newVarsel.journalpostId shouldBeEqualTo null

                    newVarselPdf?.pdf?.get(0) shouldBeEqualTo pdf[0]
                    newVarselPdf?.pdf?.get(1) shouldBeEqualTo pdf[1]
                    newVarselPdf?.aktivitetskravVarselId shouldBeEqualTo newVarsel.id
                }
            }
        }
    }
})
