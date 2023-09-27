package no.nav.syfo.aktivitetskrav

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.api.ForhandsvarselDTO
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.aktivitetskrav.kafka.KafkaAktivitetskravVurdering
import no.nav.syfo.client.pdfgen.PdfGenClient
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
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

class AktivitetskravServiceSpek : Spek({

    describe(AktivitetskravServiceSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val kafkaProducer = mockk<KafkaProducer<String, KafkaAktivitetskravVurdering>>()
            val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

            val aktivitetskravService = AktivitetskravService(
                aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(kafkaProducer),
                database = database,
                arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
                krrClient = externalMockEnvironment.krrClient,
            )

            beforeEachTest {
                clearMocks(kafkaProducer)
                coEvery {
                    kafkaProducer.send(any())
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
                        val varsel = aktivitetskravService.sendForhandsvarsel(
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
                        kafkaProducer.send(capture(producerRecordSlot))
                    }

                    val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
                    kafkaAktivitetskravVurdering.personIdent shouldBeEqualTo personIdent.value
                    kafkaAktivitetskravVurdering.status shouldBeEqualTo AktivitetskravStatus.FORHANDSVARSEL.name
                }
                it("Sends expected requestBody to pdfgenclient") {
                    val mockedPdfGenClient = mockk<PdfGenClient>()
                    val expectedForhandsvarselPdfRequestBody = generateForhandsvarselPdfDTO(forhandsvarselDTO)
                    database.createAktivitetskrav(aktivitetskrav)
                    val aktivitetskravServiceWithMockedPdfGenClient = AktivitetskravService(
                        aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(kafkaProducer),
                        database = database,
                        arenaCutoff = externalMockEnvironment.environment.arenaCutoff,
                        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                        pdfGenClient = mockedPdfGenClient,
                        pdlClient = externalMockEnvironment.pdlClient,
                        krrClient = externalMockEnvironment.krrClient,
                    )

                    coEvery {
                        mockedPdfGenClient.createForhandsvarselPdf(
                            any(),
                            any()
                        )
                    } returns UserConstants.PDF_FORHANDSVARSEL

                    runBlocking {
                        aktivitetskravServiceWithMockedPdfGenClient.sendForhandsvarsel(
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
