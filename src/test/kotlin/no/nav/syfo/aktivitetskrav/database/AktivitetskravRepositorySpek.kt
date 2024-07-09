package no.nav.syfo.aktivitetskrav.database

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.database.repository.createAktivitetskravVurdering
import no.nav.syfo.infrastructure.database.repository.updateAktivitetskrav
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

class AktivitetskravRepositorySpek : Spek({

    fun createVurdering(status: AktivitetskravStatus, arsaker: List<VurderingArsak> = emptyList()) =
        AktivitetskravVurdering.create(
            status = status,
            createdBy = UserConstants.VEILEDER_IDENT,
            beskrivelse = "En test vurdering",
            arsaker = arsaker,
            frist = null,
        )

    describe(AktivitetskravRepositorySpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val aktivitetskravRepository = AktivitetskravRepository(database = database)
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
            val pdf = byteArrayOf(0x2E, 100)

            afterEachTest { database.dropData() }

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
                    val forhandsvarsel = AktivitetskravVarsel.create(
                        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                        forhandsvarselDTO.document
                    )
                    val updatedAktivitetskrav = newAktivitetskrav.vurder(vurdering)

                    val newVarsel = aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = forhandsvarsel,
                        newVurdering = vurdering,
                        pdf = pdf,
                    )

                    val retrievedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(updatedAktivitetskrav.uuid)
                    val vurderinger = retrievedAktivitetskrav!!.vurderinger
                    val newVurdering = vurderinger.first()
                    val newVarselPdf = database.getAktivitetskravVarselPdf(newVarsel.id)

                    retrievedAktivitetskrav.status shouldBeEqualTo updatedAktivitetskrav.status
                    retrievedAktivitetskrav.updatedAt shouldBeGreaterThan retrievedAktivitetskrav.createdAt
                    vurderinger.size shouldBeEqualTo 1
                    newVurdering.aktivitetskravId shouldBeEqualTo retrievedAktivitetskrav.id
                    newVurdering.createdBy shouldBeEqualTo vurdering.createdBy
                    newVurdering.status shouldBeEqualTo vurdering.status
                    newVarsel.aktivitetskravVurderingId shouldBeEqualTo newVurdering.id
                    newVarsel.journalpostId shouldBeEqualTo null
                    newVarsel.type shouldBeEqualTo VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER.name

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
                        aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                            aktivitetskrav = aktivitetkrav,
                            varsel = varsel,
                            newVurdering = aktivitetkrav.vurderinger.first(),
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
                        List(5) {
                            createExpiredForhandsvarsel(document)
                        }
                    for ((aktivitetkrav, varsel) in createdAktivitetskravList.zip(varsler)) {
                        aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                            aktivitetskrav = aktivitetkrav,
                            varsel = varsel,
                            newVurdering = aktivitetkrav.vurderinger.first(),
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
                    val varsel = createExpiredForhandsvarsel(document)
                    aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = varsel,
                        newVurdering = updatedAktivitetskrav.vurderinger.first(),
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

            describe("Successfully retrieves a list of aktivitetskrav belonging to a list of persons") {

                val fritekst = "Et forhåndsvarsel"
                val document = generateDocumentComponentDTO(fritekst = fritekst)
                val forhandsvarselDTO = ForhandsvarselDTO(
                    fritekst = fritekst,
                    document = document,
                )

                it("Should retrieve aktivitetskrav without vurderinger for persons") {
                    val firstAktivitetskrav = Aktivitetskrav.create(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    val secondAktivitetskrav = Aktivitetskrav.create(UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
                    val personsWithAktivitetskrav =
                        listOf(UserConstants.ARBEIDSTAKER_PERSONIDENT, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT)

                    val storedAktivitetskrav = aktivitetskravRepository.getAktivitetskravForPersons(personsWithAktivitetskrav)

                    storedAktivitetskrav.size shouldBeEqualTo 2
                    val firstStoredAktivitetskrav = storedAktivitetskrav.find { it.personIdent == firstAktivitetskrav.personIdent }
                    val secondStoredAktivitetskrav = storedAktivitetskrav.find { it.personIdent == secondAktivitetskrav.personIdent }
                    firstStoredAktivitetskrav?.personIdent shouldBeEqualTo firstAktivitetskrav.personIdent
                    secondStoredAktivitetskrav?.personIdent shouldBeEqualTo secondAktivitetskrav.personIdent
                }

                it("Should retrieve aktivitetskrav with vurderinger for persons") {
                    val firstAktivitetskrav = Aktivitetskrav.create(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    createVurdering(AktivitetskravStatus.AVVENT, listOf(VurderingArsak.Avvent.InformasjonSykmeldt))
                        .also {
                            firstAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                        }
                    createVurdering(AktivitetskravStatus.FORHANDSVARSEL)
                        .also {
                            firstAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                        }
                    createVurdering(AktivitetskravStatus.IKKE_OPPFYLT)
                        .also {
                            firstAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                        }

                    val secondAktivitetskrav = Aktivitetskrav.create(UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(secondAktivitetskrav, UUID.randomUUID())
                    createVurdering(AktivitetskravStatus.FORHANDSVARSEL)
                        .also {
                            secondAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                        }
                    createVurdering(AktivitetskravStatus.OPPFYLT, listOf(VurderingArsak.Oppfylt.Friskmeldt))
                        .also {
                            secondAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(secondAktivitetskrav, it)
                        }

                    val personsWithAktivitetskrav =
                        listOf(UserConstants.ARBEIDSTAKER_PERSONIDENT, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT)
                    val storedAktivitetskrav = aktivitetskravRepository.getAktivitetskravForPersons(personsWithAktivitetskrav)

                    storedAktivitetskrav.size shouldBeEqualTo 2
                    val firstStoredAktivitetskrav = storedAktivitetskrav.find { it.personIdent == firstAktivitetskrav.personIdent }
                    firstStoredAktivitetskrav?.personIdent shouldBeEqualTo firstAktivitetskrav.personIdent
                    firstStoredAktivitetskrav?.vurderinger?.size shouldBeEqualTo 3

                    val secondStoredAktivitetskrav = storedAktivitetskrav.find { it.personIdent == secondAktivitetskrav.personIdent }
                    secondStoredAktivitetskrav?.personIdent shouldBeEqualTo secondAktivitetskrav.personIdent
                    secondStoredAktivitetskrav?.vurderinger?.size shouldBeEqualTo 2
                }

                it("Should retrieve aktivitetskrav with vurderinger and varsel for persons") {
                    val firstAktivitetskrav = Aktivitetskrav.create(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
                    createVurdering(AktivitetskravStatus.AVVENT, listOf(VurderingArsak.Avvent.InformasjonSykmeldt))
                        .also {
                            firstAktivitetskrav.vurder(it)
                            aktivitetskravRepository.createAktivitetskravVurdering(firstAktivitetskrav, it)
                        }
                    val newVurdering = createVurdering(AktivitetskravStatus.FORHANDSVARSEL).also { firstAktivitetskrav.vurder(it) }
                    val newVarsel = AktivitetskravVarsel.create(
                        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                        forhandsvarselDTO.document
                    )
                    aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                        firstAktivitetskrav,
                        newVurdering,
                        newVarsel,
                        pdf
                    )
                    val personsWithAktivitetskrav =
                        listOf(UserConstants.ARBEIDSTAKER_PERSONIDENT, UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT)
                    val storedAktivitetskrav = aktivitetskravRepository.getAktivitetskravForPersons(personsWithAktivitetskrav)

                    storedAktivitetskrav.size shouldBeEqualTo 1
                    val firstStoredAktivitetskrav = storedAktivitetskrav.find { it.personIdent == firstAktivitetskrav.personIdent }
                    firstStoredAktivitetskrav?.personIdent shouldBeEqualTo firstAktivitetskrav.personIdent
                    firstStoredAktivitetskrav?.vurderinger?.size shouldBeEqualTo 2
                    firstStoredAktivitetskrav?.vurderinger?.find { it.varsel !== null } shouldNotBe null
                }
            }
        }
    }
})
