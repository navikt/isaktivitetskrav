package no.nav.syfo.aktivitetskrav.database

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.cronjob.pdf
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

class AktivitetskravRepositorySpek : Spek({

    describe(AktivitetskravRepositorySpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val aktivitetskravRepository = AktivitetskravRepository(database = database)
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

            afterEachTest {
                database.dropData()
            }

            describe("Successfully creates an aktivitetskrav with previous aktivitetskrav") {
                val newAktivitetskrav = Aktivitetskrav.create(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val previousAktivitetskravUuid = UUID.randomUUID()
                aktivitetskravRepository.createAktivitetskrav(
                    newAktivitetskrav,
                    previousAktivitetskravUuid,
                )
                val storedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(newAktivitetskrav.uuid)

                storedAktivitetskrav?.personIdent shouldBeEqualTo newAktivitetskrav.personIdent
                storedAktivitetskrav?.previousAktivitetskravUuid shouldBeEqualTo previousAktivitetskravUuid
            }

            describe("Forhåndsvarsel") {
                val tenWeeksAgo = LocalDate.now().minusWeeks(10)
                val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
                val newAktivitetskrav = createAktivitetskravNy(
                    tilfelleStart = LocalDate.now(),
                    personIdent = personIdent,
                )
                val fritekst = "Et forhåndsvarsel"
                val document = generateDocumentComponentDTO(fritekst = fritekst)
                val forhandsvarselDTO = ForhandsvarselDTO(
                    fritekst = fritekst,
                    document = document,
                )

                beforeEachTest {
                    aktivitetskravRepository.createAktivitetskrav(newAktivitetskrav)
                }

                it("Should create forhåndsvarsel in db") {
                    val vurdering: AktivitetskravVurdering =
                        forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                    val forhandsvarsel = AktivitetskravVarsel.create(forhandsvarselDTO.document)
                    val updatedAktivitetskrav = newAktivitetskrav.vurder(vurdering)
                    val pdf = byteArrayOf(0x2E, 100)

                    val newVarsel = aktivitetskravVarselRepository.create(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = forhandsvarsel,
                        pdf = pdf,
                    )

                    val retrievedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(updatedAktivitetskrav.uuid)
                    val vurderinger = database.getAktivitetskravVurderinger(retrievedAktivitetskrav!!.id)
                    val newVurdering = vurderinger.first()
                    val newVarselPdf = database.getAktivitetskravVarselPdf(newVarsel.id)

                    retrievedAktivitetskrav.status shouldBeEqualTo updatedAktivitetskrav.status.name
                    retrievedAktivitetskrav.updatedAt shouldBeGreaterThan retrievedAktivitetskrav.createdAt
                    vurderinger.size shouldBeEqualTo 1
                    newVurdering.aktivitetskravId shouldBeEqualTo retrievedAktivitetskrav.id
                    newVurdering.createdBy shouldBeEqualTo vurdering.createdBy
                    newVurdering.status shouldBeEqualTo vurdering.status.name
                    newVarsel.aktivitetskravVurderingId shouldBeEqualTo newVurdering.id
                    newVarsel.journalpostId shouldBeEqualTo null

                    newVarselPdf?.pdf?.size shouldBeEqualTo pdf.size
                    newVarselPdf?.pdf?.get(0) shouldBeEqualTo pdf[0]
                    newVarselPdf?.pdf?.get(1) shouldBeEqualTo pdf[1]
                    newVarselPdf?.aktivitetskravVarselId shouldBeEqualTo newVarsel.id
                }

                it("Should retrieve expired varsler when svarfrist is one week ago or more") {
                    val aktivitetskravList =
                        createNAktivitetskrav(5)
                            .map {
                                val vurdering = AktivitetskravVurdering.create(
                                    status = AktivitetskravStatus.FORHANDSVARSEL,
                                    createdBy = UserConstants.VEILEDER_IDENT,
                                    beskrivelse = "En test vurdering",
                                    arsaker = emptyList(),
                                    frist = null,
                                )
                                val updatedAktivitetskrav = it.vurder(vurdering)
                                aktivitetskravRepository.createAktivitetskrav(updatedAktivitetskrav)
                                updatedAktivitetskrav
                            }
                    val varsler = createVarsler()
                    for ((aktivitetkrav, varsel) in aktivitetskravList.zip(varsler)) {
                        aktivitetskravVarselRepository.create(
                            aktivitetskrav = aktivitetkrav,
                            varsel = varsel,
                            pdf = pdf,
                        )
                    }

                    val expiredVarsler = runBlocking { aktivitetskravVarselRepository.getExpiredVarsler() }
                        .map { (_, _, varsel) -> varsel }

                    expiredVarsler.size shouldBeEqualTo 3
                    expiredVarsler.any {
                        it.svarfrist == LocalDate.now()
                    } shouldBe true
                    expiredVarsler.any {
                        it.svarfrist == LocalDate.now().minusDays(1)
                    } shouldBe true
                    expiredVarsler.any {
                        it.svarfrist == LocalDate.now().minusWeeks(1)
                    } shouldBe true
                }

                it("Is not retrieving expired varsler which has OPPFYLT, UNNTAK or IKKE_AKTUELL status after they are created") {
                    val createdAktivitetskravList =
                        createNAktivitetskrav(5)
                            .map {
                                val vurdering = AktivitetskravVurdering.create(
                                    status = AktivitetskravStatus.FORHANDSVARSEL,
                                    createdBy = UserConstants.VEILEDER_IDENT,
                                    beskrivelse = "En test vurdering",
                                    arsaker = emptyList(),
                                    frist = null,
                                )
                                val updatedAktivitetskrav = it.vurder(vurdering)
                                aktivitetskravRepository.createAktivitetskrav(updatedAktivitetskrav)
                                updatedAktivitetskrav
                            }
                    val varsler =
                        List(5) { AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusWeeks(1)) }
                    for ((aktivitetkrav, varsel) in createdAktivitetskravList.zip(varsler)) {
                        aktivitetskravVarselRepository.create(
                            aktivitetskrav = aktivitetkrav,
                            varsel = varsel,
                            pdf = pdf,
                        )
                    }
                    val aktivitetskravOppfylt =
                        createAktivitetskravOppfylt(createdAktivitetskravList[0])
                    val aktivitetskravUnntak =
                        createAktivitetskravUnntak(createdAktivitetskravList[1])
                    val aktivitetskravIkkeAktuell =
                        createAktivitetskravUnntak(createdAktivitetskravList[2])
                    val aktivitetskravAvvent =
                        createAktivitetskravAvvent(createdAktivitetskravList[3])

                    database.connection.use { connection ->
                        val oppfyltId = connection.updateAktivitetskrav(aktivitetskravOppfylt)
                        val unntakId = connection.updateAktivitetskrav(aktivitetskravUnntak)
                        val ikkeAktuellId = connection.updateAktivitetskrav(aktivitetskravIkkeAktuell)
                        val avventId = connection.updateAktivitetskrav(aktivitetskravAvvent)
                        connection.createAktivitetskravVurdering(oppfyltId, aktivitetskravOppfylt.vurderinger.first())
                        connection.createAktivitetskravVurdering(unntakId, aktivitetskravUnntak.vurderinger.first())
                        connection.createAktivitetskravVurdering(
                            ikkeAktuellId,
                            aktivitetskravIkkeAktuell.vurderinger.first()
                        )
                        connection.createAktivitetskravVurdering(avventId, aktivitetskravAvvent.vurderinger.first())
                        connection.commit()
                    }

                    val expiredVarsler = runBlocking { aktivitetskravVarselRepository.getExpiredVarsler() }
                        .map { (_, _, varsel) -> varsel }

                    expiredVarsler.size shouldBeEqualTo 2
                }

                it("Should update varsel") {
                    val aktivitetskrav = createAktivitetskravNy(tenWeeksAgo)
                    val vurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.FORHANDSVARSEL,
                        createdBy = UserConstants.VEILEDER_IDENT,
                        beskrivelse = "En test vurdering",
                        arsaker = emptyList(),
                        frist = null,
                    )
                    val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                    aktivitetskravRepository.createAktivitetskrav(updatedAktivitetskrav)
                    val varsel = AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusWeeks(1))
                    aktivitetskravVarselRepository.create(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = varsel,
                        pdf = pdf,
                    )
                    val expiredVarsler =
                        runBlocking { aktivitetskravVarselRepository.getExpiredVarsler() }.map { (personIdent, aktivitetskravUuid, varsel) ->
                            varsel.toExpiredVarsel(personIdent, aktivitetskravUuid)
                        }
                    val rowsUpdated =
                        runBlocking { aktivitetskravVarselRepository.updateExpiredVarselPublishedAt(expiredVarsler.first()) }

                    rowsUpdated shouldBe 1
                    runBlocking { aktivitetskravVarselRepository.getExpiredVarsler() } shouldBeEqualTo emptyList()
                }
            }
        }
    }
})
