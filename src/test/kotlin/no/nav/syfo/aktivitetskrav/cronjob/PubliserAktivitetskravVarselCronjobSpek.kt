package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.aktivitetskrav.api.DocumentComponentDTO
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.kafka.AktivitetskravVarselProducer
import no.nav.syfo.infrastructure.kafka.domain.KafkaAktivitetskravVarsel
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import no.nav.syfo.testhelper.getVarsler
import no.nav.syfo.util.sekundOpplosning
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class PubliserAktivitetskravVarselCronjobSpek : Spek({
    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    val aktivitetskrav = Aktivitetskrav.create(
        personIdent = personIdent,
        oppfolgingstilfelleStart = LocalDate.now(),
    )
    val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")
    val pdf = byteArrayOf(23)
    val defaultJournalpostId = "9"

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
        val aktivitetskravRepository = AktivitetskravRepository(database = database)

        val aktivitetskravVarselKafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVarsel>>()
        val aktivitetskravVarselProducer = AktivitetskravVarselProducer(
            kafkaProducer = aktivitetskravVarselKafkaProducer,
        )
        val aktivitetskravVarselService = AktivitetskravVarselService(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            aktivitetskravVurderingProducer = mockk(),
            aktivitetskravVarselProducer = aktivitetskravVarselProducer,
            varselPdfService = VarselPdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            ),
        )

        val publiserAktivitetskravVarselCronjob = PubliserAktivitetskravVarselCronjob(
            aktivitetskravVarselService = aktivitetskravVarselService,
        )

        fun createVarsel(
            aktivitetskrav: Aktivitetskrav,
            pdf: ByteArray,
            journalpostId: String? = defaultJournalpostId,
            varselType: VarselType,
            document: List<DocumentComponentDTO>,
        ): AktivitetskravVarsel {
            val varsel = AktivitetskravVarsel.create(varselType, document)
            aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                aktivitetskrav = aktivitetskrav,
                varsel = varsel,
                newVurdering = aktivitetskrav.vurderinger.first(),
                pdf = pdf,
            )
            if (journalpostId != null) {
                aktivitetskravVarselRepository.updateJournalpostId(varsel, journalpostId)
            }
            return varsel
        }

        beforeEachTest {
            clearMocks(aktivitetskravVarselKafkaProducer)
            coEvery {
                aktivitetskravVarselKafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        describe("${PubliserAktivitetskravVarselCronjob::class.java.simpleName} runJob") {
            beforeEachTest {
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            }

            it("Publiserer journalfort forhandsvarsel") {
                val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    document = forhandsvarselDTO.document,
                )
                val varslerBefore = database.getVarsler(personIdent)
                varslerBefore.size shouldBeEqualTo 1
                varslerBefore.first().publishedAt shouldBe null

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varslerBefore.first().uuid
                first.updatedAt shouldBeGreaterThan first.createdAt
                first.publishedAt shouldNotBe null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVarsel>>()
                verify(exactly = 1) {
                    aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
                kafkaAktivitetskravVarsel.personIdent shouldBeEqualTo personIdent.value
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldNotBeEqualTo kafkaAktivitetskravVarsel.varselUuid
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldBeEqualTo aktivitetskrav.uuid
                kafkaAktivitetskravVarsel.varselUuid shouldBeEqualTo first.uuid
                kafkaAktivitetskravVarsel.createdAt shouldBeEqualTo first.createdAt
                kafkaAktivitetskravVarsel.journalpostId.shouldNotBeNull()
                kafkaAktivitetskravVarsel.journalpostId shouldBeEqualTo first.journalpostId
                kafkaAktivitetskravVarsel.document.shouldNotBeEmpty()
                kafkaAktivitetskravVarsel.svarfrist shouldBeEqualTo first.svarfrist
                kafkaAktivitetskravVarsel.vurderingUuid shouldBeEqualTo vurdering.uuid
                kafkaAktivitetskravVarsel.type shouldBeEqualTo VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER.name
            }
            it("Publiserer ikke forhandsvarsel som ikke er journalfort") {
                val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    document = forhandsvarselDTO.document,
                    journalpostId = null,
                )
                val varslerBefore = database.getVarsler(personIdent)
                varslerBefore.size shouldBeEqualTo 1
                varslerBefore.first().publishedAt shouldBe null

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varslerBefore.first().uuid
                first.updatedAt.sekundOpplosning() shouldBeEqualTo first.createdAt.sekundOpplosning()
                first.publishedAt shouldBe null
            }
            it("Publiserer ikke forhandsvarsel som allerede er publisert") {
                val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                    document = forhandsvarselDTO.document,
                )
                val varslerBefore = database.getVarsler(personIdent)
                varslerBefore.size shouldBeEqualTo 1
                varslerBefore.first().publishedAt shouldBe null

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }
            }
            it("Publiserer journalført varsel for UNNTAK") {
                val fritekst = "Aktivitetskravet er oppfylt"
                val vurdering = AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.UNNTAK,
                    beskrivelse = fritekst,
                    arsaker = listOf(VurderingArsak.Unntak.MedisinskeGrunner),
                    createdBy = UserConstants.VEILEDER_IDENT,
                )
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                val varsel = createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.UNNTAK,
                    document = generateDocumentComponentDTO(fritekst),
                )

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.updatedAt shouldBeGreaterThan first.createdAt
                first.publishedAt shouldNotBe null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVarsel>>()
                verify(exactly = 1) {
                    aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
                kafkaAktivitetskravVarsel.personIdent shouldBeEqualTo personIdent.value
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldNotBeEqualTo kafkaAktivitetskravVarsel.varselUuid
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldBeEqualTo aktivitetskrav.uuid
                kafkaAktivitetskravVarsel.varselUuid shouldBeEqualTo first.uuid
                kafkaAktivitetskravVarsel.createdAt shouldBeEqualTo first.createdAt
                kafkaAktivitetskravVarsel.journalpostId.shouldNotBeNull()
                kafkaAktivitetskravVarsel.journalpostId shouldBeEqualTo first.journalpostId
                kafkaAktivitetskravVarsel.document.shouldNotBeEmpty()
                kafkaAktivitetskravVarsel.svarfrist shouldBeEqualTo first.svarfrist
                kafkaAktivitetskravVarsel.vurderingUuid shouldBeEqualTo vurdering.uuid
                kafkaAktivitetskravVarsel.type shouldBeEqualTo VarselType.UNNTAK.name
            }

            it("Publiserer journalført varsel for OPPFYLT") {
                val fritekst = "Aktivitetskravet er oppfylt"
                val vurdering = AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.OPPFYLT,
                    beskrivelse = fritekst,
                    arsaker = listOf(VurderingArsak.Oppfylt.Friskmeldt),
                    createdBy = UserConstants.VEILEDER_IDENT,
                )
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                val varsel = createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.OPPFYLT,
                    document = generateDocumentComponentDTO(fritekst),
                )

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.updatedAt shouldBeGreaterThan first.createdAt
                first.publishedAt shouldNotBe null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVarsel>>()
                verify(exactly = 1) {
                    aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
                kafkaAktivitetskravVarsel.personIdent shouldBeEqualTo personIdent.value
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldNotBeEqualTo kafkaAktivitetskravVarsel.varselUuid
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldBeEqualTo aktivitetskrav.uuid
                kafkaAktivitetskravVarsel.varselUuid shouldBeEqualTo first.uuid
                kafkaAktivitetskravVarsel.createdAt shouldBeEqualTo first.createdAt
                kafkaAktivitetskravVarsel.journalpostId.shouldNotBeNull()
                kafkaAktivitetskravVarsel.journalpostId shouldBeEqualTo first.journalpostId
                kafkaAktivitetskravVarsel.document.shouldNotBeEmpty()
                kafkaAktivitetskravVarsel.svarfrist shouldBeEqualTo first.svarfrist
                kafkaAktivitetskravVarsel.vurderingUuid shouldBeEqualTo vurdering.uuid
                kafkaAktivitetskravVarsel.type shouldBeEqualTo VarselType.OPPFYLT.name
            }

            it("Publiserer journalført varsel for IKKE AKTUELL") {
                val fritekst = "Aktivitetskravet er ikke aktuelt"
                val vurdering = AktivitetskravVurdering.create(
                    status = AktivitetskravStatus.IKKE_AKTUELL,
                    beskrivelse = fritekst,
                    arsaker = listOf(VurderingArsak.IkkeAktuell.InnvilgetVTA),
                    createdBy = UserConstants.VEILEDER_IDENT,
                )
                val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
                val varsel = createVarsel(
                    aktivitetskrav = updatedAktivitetskrav,
                    pdf = pdf,
                    varselType = VarselType.IKKE_AKTUELL,
                    document = generateDocumentComponentDTO(fritekst),
                )

                runBlocking {
                    val result = publiserAktivitetskravVarselCronjob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
                }
                val varsler = database.getVarsler(personIdent)
                varsler.size shouldBeEqualTo 1
                val first = varsler.first()
                first.uuid shouldBeEqualTo varsel.uuid
                first.updatedAt shouldBeGreaterThan first.createdAt
                first.publishedAt shouldNotBe null

                val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVarsel>>()
                verify(exactly = 1) {
                    aktivitetskravVarselKafkaProducer.send(capture(producerRecordSlot))
                }

                val kafkaAktivitetskravVarsel = producerRecordSlot.captured.value()
                kafkaAktivitetskravVarsel.personIdent shouldBeEqualTo personIdent.value
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldNotBeEqualTo kafkaAktivitetskravVarsel.varselUuid
                kafkaAktivitetskravVarsel.aktivitetskravUuid shouldBeEqualTo aktivitetskrav.uuid
                kafkaAktivitetskravVarsel.varselUuid shouldBeEqualTo first.uuid
                kafkaAktivitetskravVarsel.createdAt shouldBeEqualTo first.createdAt
                kafkaAktivitetskravVarsel.journalpostId.shouldNotBeNull()
                kafkaAktivitetskravVarsel.journalpostId shouldBeEqualTo first.journalpostId
                kafkaAktivitetskravVarsel.document.shouldNotBeEmpty()
                kafkaAktivitetskravVarsel.svarfrist shouldBeEqualTo first.svarfrist
                kafkaAktivitetskravVarsel.vurderingUuid shouldBeEqualTo vurdering.uuid
                kafkaAktivitetskravVarsel.type shouldBeEqualTo VarselType.IKKE_AKTUELL.name
            }
        }
    }
})
