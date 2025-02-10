package no.nav.syfo.aktivitetskrav.database

import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID

class AktivitetskravRepositorySpek : Spek({

    describe(AktivitetskravRepositorySpek::class.java.simpleName) {
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
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
            val newAktivitetskrav = createAktivitetskravNy(
                tilfelleStart = LocalDate.now(),
                personIdent = personIdent,
            )
            val fritekst = "Et forhåndsvarsel"
            val document = generateDocumentComponentDTO(fritekst = fritekst)
            val frist = LocalDate.now().plusDays(30)
            val forhandsvarselDTO = ForhandsvarselDTO(
                fritekst = fritekst,
                document = document,
                frist = frist,
            )

            beforeEachTest {
                aktivitetskravRepository.createAktivitetskrav(newAktivitetskrav)
            }

            it("Should create forhåndsvarsel in db") {
                val vurdering: AktivitetskravVurdering =
                    forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                val forhandsvarsel = AktivitetskravVarsel.create(
                    type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    frist = LocalDate.now().plusDays(30),
                    document = forhandsvarselDTO.document,
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
                newVarsel.svarfrist shouldBeEqualTo frist

                newVarselPdf?.pdf?.size shouldBeEqualTo pdf.size
                newVarselPdf?.pdf?.get(0) shouldBeEqualTo pdf[0]
                newVarselPdf?.pdf?.get(1) shouldBeEqualTo pdf[1]
                newVarselPdf?.aktivitetskravVarselId shouldBeEqualTo newVarsel.id
            }
        }

        describe("Successfully retrieves a list of aktivitetskrav belonging to a list of persons") {

            val fritekst = "Et forhåndsvarsel"
            val document = generateDocumentComponentDTO(fritekst = fritekst)
            val forhandsvarselDTO = ForhandsvarselDTO(
                fritekst = fritekst,
                document = document,
                frist = LocalDate.now().plusDays(30),
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
                val svarfrist = LocalDate.now().plusDays(30)
                val newVurdering = createVurdering(
                    status = AktivitetskravStatus.FORHANDSVARSEL,
                    frist = svarfrist,
                ).also { firstAktivitetskrav.vurder(it) }
                val newVarsel = AktivitetskravVarsel.create(
                    type = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    document = forhandsvarselDTO.document,
                    frist = svarfrist,
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
})
