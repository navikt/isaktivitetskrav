package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.aktivitetskrav.domain.VurderingArsak
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.application.exception.ConflictException
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENT
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.createAktivitetskravOppfylt
import no.nav.syfo.testhelper.generator.createVurdering
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.getAktivitetskravVarselPdf
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Future

class AktivitetskravServiceSpek : Spek({

    describe(AktivitetskravService::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val vurderingProducerMock = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
            val aktivitetskravRepository = AktivitetskravRepository(database = database)
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
            val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
            val aktivitetskravService = AktivitetskravService(
                aktivitetskravRepository = aktivitetskravRepository,
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
                varselPdfService = VarselPdfService(
                    pdfGenClient = externalMockEnvironment.pdfgenClient,
                    pdlClient = externalMockEnvironment.pdlClient,
                )
            )

            beforeEachTest {
                clearMocks(vurderingProducerMock)
                coEvery {
                    vurderingProducerMock.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }
            afterEachTest {
                database.dropData()
            }

            describe("create aktivitetskrav") {
                it("creates aktivitetskrav with previous aktivitetskrav from service") {
                    val previousAktivitetskrav = createAktivitetskravOppfylt(
                        createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    )
                    val createdAktivitetskrav =
                        aktivitetskravService.createAktivitetskrav(
                            UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            previousAktivitetskrav = previousAktivitetskrav
                        )
                    val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
                    val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                    verify(exactly = 1) {
                        vurderingProducerMock.send(capture(producerRecordSlot))
                    }

                    val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
                    createdAktivitetskrav.personIdent.value shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    savedAktivitetskrav?.previousAktivitetskravUuid shouldBeEqualTo previousAktivitetskrav.uuid
                    aktivitetskravVurderingRecord.previousAktivitetskravUuid shouldBeEqualTo previousAktivitetskrav.uuid
                    aktivitetskravVurderingRecord.uuid shouldBeEqualTo createdAktivitetskrav.uuid
                }
                it("create aktivitetskrav with previous aktivitetskrav not final throws exception") {
                    val previousAktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    assertFailsWith(ConflictException::class) {
                        aktivitetskravService.createAktivitetskrav(
                            UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            previousAktivitetskrav = previousAktivitetskrav
                        )
                    }
                }
                it("creates aktivitetskrav without previous aktivitetskrav from service") {
                    val createdAktivitetskrav =
                        aktivitetskravService.createAktivitetskrav(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                    val savedAktivitetskrav = aktivitetskravRepository.getAktivitetskrav(createdAktivitetskrav.uuid)
                    val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()

                    verify(exactly = 1) {
                        vurderingProducerMock.send(capture(producerRecordSlot))
                    }

                    val aktivitetskravVurderingRecord = producerRecordSlot.captured.value()
                    createdAktivitetskrav.personIdent.value shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                    savedAktivitetskrav?.previousAktivitetskravUuid shouldBeEqualTo null
                    aktivitetskravVurderingRecord.previousAktivitetskravUuid shouldBeEqualTo null
                    aktivitetskravVurderingRecord.uuid shouldBeEqualTo createdAktivitetskrav.uuid
                }
            }

            describe("vurder aktivitetskrav") {
                it("creates vurdering, varsel and pdf for unntak") {
                    var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                    val fritekst = "En beskrivelse"
                    val vurdering = AktivitetskravVurdering.create(
                        AktivitetskravStatus.UNNTAK,
                        UserConstants.VEILEDER_IDENT,
                        fritekst,
                        listOf(VurderingArsak.Unntak.MedisinskeGrunner),
                    )

                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = aktivitetskrav,
                            aktivitetskravVurdering = vurdering,
                            document = generateDocumentComponentDTO(fritekst),
                            callId = "",
                        )
                    }

                    aktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.UNNTAK
                    val latestVurdering = aktivitetskrav.vurderinger.first()
                    val varsel =
                        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
                    varsel.shouldNotBeNull()
                    varsel.type shouldBeEqualTo VarselType.UNNTAK.name
                    varsel.document.shouldNotBeEmpty()
                    varsel.svarfrist.shouldBeNull()
                    val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
                    varselPdf.shouldNotBeNull()
                }

                it("creates vurdering, varsel and pdf for oppfylt") {
                    var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                    val fritekst = "En beskrivelse"
                    val vurdering = AktivitetskravVurdering.create(
                        AktivitetskravStatus.OPPFYLT,
                        UserConstants.VEILEDER_IDENT,
                        fritekst,
                        listOf(VurderingArsak.Oppfylt.Gradert),
                    )

                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = aktivitetskrav,
                            aktivitetskravVurdering = vurdering,
                            document = generateDocumentComponentDTO(fritekst),
                            callId = "",
                        )
                    }

                    aktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.OPPFYLT
                    val latestVurdering = aktivitetskrav.vurderinger.first()
                    val varsel =
                        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
                    varsel.shouldNotBeNull()
                    varsel.type shouldBeEqualTo VarselType.OPPFYLT.name
                    varsel.document.shouldNotBeEmpty()
                    varsel.svarfrist.shouldBeNull()
                    val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
                    varselPdf.shouldNotBeNull()
                }

                it("creates vurdering, varsel and pdf for ikke-aktuell") {
                    var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                    val fritekst = "En beskrivelse"
                    val vurdering = AktivitetskravVurdering.create(
                        AktivitetskravStatus.IKKE_AKTUELL,
                        UserConstants.VEILEDER_IDENT,
                        fritekst,
                        listOf(VurderingArsak.IkkeAktuell.InnvilgetVTA),
                    )

                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = aktivitetskrav,
                            aktivitetskravVurdering = vurdering,
                            document = generateDocumentComponentDTO(fritekst),
                            callId = "",
                        )
                    }

                    aktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.IKKE_AKTUELL
                    val latestVurdering = aktivitetskrav.vurderinger.first()
                    val varsel =
                        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
                    varsel.shouldNotBeNull()
                    varsel.type shouldBeEqualTo VarselType.IKKE_AKTUELL.name
                    varsel.document.shouldNotBeEmpty()
                    varsel.svarfrist.shouldBeNull()
                    val varselPdf = database.getAktivitetskravVarselPdf(aktivitetskravVarselId = varsel.id)
                    varselPdf.shouldNotBeNull()
                }

                it("creates vurdering and no varsel for avvent") {
                    var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                    val fritekst = "En beskrivelse"
                    val vurdering = AktivitetskravVurdering.create(
                        AktivitetskravStatus.AVVENT,
                        UserConstants.VEILEDER_IDENT,
                        fritekst,
                        listOf(VurderingArsak.Avvent.DroftesMedROL),
                    )

                    runBlocking {
                        aktivitetskravService.vurderAktivitetskrav(
                            aktivitetskrav = aktivitetskrav,
                            aktivitetskravVurdering = vurdering,
                            document = generateDocumentComponentDTO(fritekst),
                            callId = "",
                        )
                    }

                    aktivitetskrav =
                        aktivitetskravRepository.getAktivitetskrav(uuid = aktivitetskrav.uuid)?.toAktivitetskrav()!!
                    aktivitetskrav.status shouldBeEqualTo AktivitetskravStatus.AVVENT
                    val latestVurdering = aktivitetskrav.vurderinger.first()
                    val varsel =
                        aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = latestVurdering.uuid)
                    varsel.shouldBeNull()
                }
            }

            describe("getAktivitetskravForPersons") {
                it("gets aktivitetskrav with only the most recent vurdering for persons") {
                    val firstAktivitetskrav = Aktivitetskrav.create(ARBEIDSTAKER_PERSONIDENT)
                    aktivitetskravRepository.createAktivitetskrav(firstAktivitetskrav, UUID.randomUUID())
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

                    val aktivitetskravForPersons =
                        aktivitetskravService.getAktivitetskravForPersons(listOf(ARBEIDSTAKER_PERSONIDENT))
                    aktivitetskravForPersons[ARBEIDSTAKER_PERSONIDENT]?.vurderinger?.size shouldBe 1
                    aktivitetskravForPersons[ARBEIDSTAKER_PERSONIDENT]?.vurderinger?.first()?.status shouldBe AktivitetskravStatus.IKKE_OPPFYLT
                }
            }
        }
    }
})
