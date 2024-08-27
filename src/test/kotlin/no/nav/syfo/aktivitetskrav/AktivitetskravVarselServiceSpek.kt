package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.VarselType
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.domain.AktivitetskravVurderingRecord
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarselPdfDTO
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class AktivitetskravVarselServiceSpek : Spek({

    describe(AktivitetskravVarselService::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val vurderingProducerMock = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
            val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
            val aktivitetskravRepository = AktivitetskravRepository(database = database)

            val aktivitetskravVarselService = AktivitetskravVarselService(
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                aktivitetskravVarselProducer = mockk(),
                varselPdfService = VarselPdfService(
                    pdfGenClient = externalMockEnvironment.pdfgenClient,
                    pdlClient = externalMockEnvironment.pdlClient,
                ),
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
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

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
                        varsel.type shouldBeEqualTo VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
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
                    aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                    val aktivitetskravVarselServiceWithMockedPdfGenClient = AktivitetskravVarselService(
                        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                        aktivitetskravVarselProducer = mockk(),
                        varselPdfService = VarselPdfService(
                            pdfGenClient = mockedPdfGenClient,
                            pdlClient = externalMockEnvironment.pdlClient,
                        ),
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
            }
        }
    }
})
