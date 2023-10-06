package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.cronjob.pdf
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.ExpiredVarselProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarselPdfDTO
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Future

class AktivitetskravVarselServiceSpek : Spek({

    describe(AktivitetskravVarselService::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val vurderingProducerMock = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
            val expiredVarselProducerMock = mockk<KafkaProducer<String, ExpiredVarsel>>()
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
            val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
            val expiredVarselProducer = ExpiredVarselProducer(expiredVarselProducerMock)

            val aktivitetskravVarselService = AktivitetskravVarselService(
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                arbeidstakervarselProducer = mockk(),
                aktivitetskravVarselProducer = mockk(),
                expiredVarselProducer = expiredVarselProducer,
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
                krrClient = externalMockEnvironment.krrClient,
            )

            beforeEachTest {
                clearMocks(vurderingProducerMock, expiredVarselProducerMock)
                coEvery {
                    vurderingProducerMock.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
                coEvery {
                    expiredVarselProducerMock.send(any())
                } returns mockk<Future<RecordMetadata>>(relaxed = true)
            }

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
                it("Updates aktivitetskrav, creates aktivitetskravVurdering, creates aktivitetskravVarsel, creates aktivitetskravVarselPdf and send on kafka") {
                    database.createAktivitetskrav(aktivitetskrav)

                    runBlocking {
                        val varsel = aktivitetskravVarselService.sendForhandsvarsel(
                            aktivitetskrav = aktivitetskrav,
                            veilederIdent = UserConstants.VEILEDER_IDENT,
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            forhandsvarselDTO = forhandsvarselDTO,
                            callId = "",
                        )

                        varsel.journalpostId shouldBeEqualTo null
                        varsel.document shouldBeEqualTo document
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, KafkaAktivitetskravVurdering>>()
                    verify(exactly = 1) {
                        vurderingProducerMock.send(capture(producerRecordSlot))
                    }

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.FORHANDSVARSEL.name
                }
                it("Sends expected requestBody to pdfgenclient") {
                    val mockedPdfGenClient = mockk<PdfGenClient>()
                    val expectedForhandsvarselPdfRequestBody = generateForhandsvarselPdfDTO(forhandsvarselDTO)
                    database.createAktivitetskrav(aktivitetskrav)
                    val aktivitetskravVarselServiceWithMockedPdfGenClient = AktivitetskravVarselService(
                        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                        arbeidstakervarselProducer = mockk(),
                        aktivitetskravVarselProducer = mockk(),
                        expiredVarselProducer = mockk(),
                        pdfGenClient = mockedPdfGenClient,
                        pdlClient = externalMockEnvironment.pdlClient,
                        krrClient = externalMockEnvironment.krrClient,
                        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                    )

                    coEvery {
                        mockedPdfGenClient.createForhandsvarselPdf(
                            any(),
                            any()
                        )
                    } returns UserConstants.PDF_FORHANDSVARSEL

                    runBlocking {
                        aktivitetskravVarselServiceWithMockedPdfGenClient.sendForhandsvarsel(
                            aktivitetskrav = aktivitetskrav,
                            veilederIdent = UserConstants.VEILEDER_IDENT,
                            personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            forhandsvarselDTO = forhandsvarselDTO,
                            callId = "",
                        )
                    }

                    coVerify {
                        mockedPdfGenClient.createForhandsvarselPdf(
                            callId = "",
                            forhandsvarselPdfDTO = expectedForhandsvarselPdfRequestBody
                        )
                    }
                }
                it("Retrieves expired varsel which has svarfrist today or earlier and match inserted varsler") {
                    val newAktivitetskrav = createAktivitetskravNy(LocalDate.now().minusWeeks(10))
                    val vurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.FORHANDSVARSEL,
                        createdBy = UserConstants.VEILEDER_IDENT,
                        beskrivelse = "En test vurdering",
                        arsaker = emptyList(),
                        frist = null,
                    )
                    val updatedAktivitetskrav = newAktivitetskrav.vurder(vurdering)
                    database.createAktivitetskrav(updatedAktivitetskrav)
                    val varsel = AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusWeeks(1))
                    aktivitetskravVarselRepository.create(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = varsel,
                        pdf = pdf,
                    )
                    val expiredVarslerToBePublished = runBlocking { aktivitetskravVarselService.getExpiredVarsler() }
                    expiredVarslerToBePublished.size shouldBe 1

                    expiredVarslerToBePublished.first().varselUuid shouldBeEqualTo varsel.uuid
                    expiredVarslerToBePublished.first().svarfrist shouldBeEqualTo varsel.svarfrist
                    expiredVarslerToBePublished.first().createdAt.truncatedTo(ChronoUnit.MINUTES) shouldBeEqualTo varsel.createdAt.toLocalDateTime()
                        .truncatedTo(ChronoUnit.MINUTES)
                }
                it("Publishes expired varsel to kafka") {
                    val newAktivitetskrav = createAktivitetskravNy(LocalDate.now().minusWeeks(10))
                    val vurdering = AktivitetskravVurdering.create(
                        status = AktivitetskravStatus.FORHANDSVARSEL,
                        createdBy = UserConstants.VEILEDER_IDENT,
                        beskrivelse = "En test vurdering",
                        arsaker = emptyList(),
                        frist = null,
                    )
                    val updatedAktivitetskrav = newAktivitetskrav.vurder(vurdering)
                    database.createAktivitetskrav(updatedAktivitetskrav)
                    val varsel = AktivitetskravVarsel.create(document, svarfrist = LocalDate.now().minusWeeks(1))
                    aktivitetskravVarselRepository.create(
                        aktivitetskrav = updatedAktivitetskrav,
                        varsel = varsel,
                        pdf = pdf,
                    )
                    val expiredVarslerToBePublished = runBlocking { aktivitetskravVarselService.getExpiredVarsler() }

                    runBlocking {
                        aktivitetskravVarselService.publishExpiredVarsel(expiredVarslerToBePublished.first())
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, ExpiredVarsel>>()
                    verify(exactly = 1) {
                        expiredVarselProducerMock.send(capture(producerRecordSlot))
                    }
                    val expiredVarselRecord = producerRecordSlot.captured.value()
                    expiredVarselRecord.varselUuid shouldBeEqualTo varsel.uuid
                    expiredVarselRecord.svarfrist shouldBeEqualTo varsel.svarfrist
                    expiredVarselRecord.createdAt.truncatedTo(ChronoUnit.MINUTES) shouldBeEqualTo varsel.createdAt.toLocalDateTime()
                        .truncatedTo(ChronoUnit.MINUTES)
                }
            }
        }
    }
})
