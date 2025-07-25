package no.nav.syfo.aktivitetskrav

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.dto.ForhandsvarselDTO
import no.nav.syfo.application.AktivitetskravVarselService
import no.nav.syfo.application.VarselPdfService
import no.nav.syfo.domain.AktivitetskravStatus
import no.nav.syfo.domain.VarselType
import no.nav.syfo.infrastructure.client.pdfgen.ForhandsvarselPdfDTO
import no.nav.syfo.infrastructure.client.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.database.repository.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.repository.AktivitetskravVarselRepository
import no.nav.syfo.infrastructure.kafka.AktivitetskravVurderingProducer
import no.nav.syfo.infrastructure.kafka.model.AktivitetskravVurderingRecord
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateDocumentComponentDTO
import no.nav.syfo.testhelper.generator.generateForhandsvarselPdfDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.time.LocalDate
import java.util.concurrent.Future

class AktivitetskravVarselServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vurderingProducerMock = mockk<KafkaProducer<String, AktivitetskravVurderingRecord>>()
    private val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)
    private val aktivitetskravVurderingProducer = AktivitetskravVurderingProducer(vurderingProducerMock)
    private val aktivitetskravRepository = AktivitetskravRepository(database = database)

    private val aktivitetskravVarselService = AktivitetskravVarselService(
        aktivitetskravVarselRepository = aktivitetskravVarselRepository,
        aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
        aktivitetskravVarselProducer = mockk(),
        varselPdfService = VarselPdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
    )

    @BeforeEach
    fun setUp() {
        clearMocks(vurderingProducerMock)
        coEvery {
            vurderingProducerMock.send(any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        database.dropData()
    }

    @Nested
    @DisplayName("Forhåndsvarsel")
    inner class Forhandsvarsel {
        private val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
        private val aktivitetskrav = createAktivitetskravNy(
            tilfelleStart = LocalDate.now(),
            personIdent = personIdent,
        )
        private val fritekst = "Et forhåndsvarsel"
        private val document = generateDocumentComponentDTO(fritekst = fritekst)
        private val forhandsvarselDTO = ForhandsvarselDTO(
            fritekst = fritekst,
            document = document,
            frist = LocalDate.now().plusDays(30),
        )

        @Test
        fun `Updates aktivitetskrav, creates aktivitetskravVurdering, creates aktivitetskravVarsel, creates aktivitetskravVarselPdf and send on kafka`() {
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

            runBlocking {
                val varsel = aktivitetskravVarselService.sendForhandsvarsel(
                    aktivitetskrav = aktivitetskrav,
                    veilederIdent = UserConstants.VEILEDER_IDENT,
                    personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                    forhandsvarselDTO = forhandsvarselDTO,
                    callId = "",
                )

                assertNull(varsel.journalpostId)
                assertEquals(document, varsel.document)
                assertEquals(VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER, varsel.type)
            }

            val producerRecordSlot = slot<ProducerRecord<String, AktivitetskravVurderingRecord>>()
            verify(exactly = 1) {
                vurderingProducerMock.send(capture(producerRecordSlot))
            }

            val kafkaAktivitetskravVurdering = producerRecordSlot.captured.value()
            assertEquals(personIdent.value, kafkaAktivitetskravVurdering.personIdent)
            assertEquals(AktivitetskravStatus.FORHANDSVARSEL.name, kafkaAktivitetskravVurdering.status)
        }

        @Test
        fun `Sends expected requestBody to pdfgenclient`() {
            val mockedPdfGenClient = mockk<PdfGenClient>()
            val expectedForhandsvarselPdfRequestBody = generateForhandsvarselPdfDTO(forhandsvarselDTO)

            val varselPdfService = VarselPdfService(
                pdfGenClient = mockedPdfGenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            )

            val aktivitetskravVarselServiceWithMockedPdfGenClient = AktivitetskravVarselService(
                aktivitetskravVarselRepository = aktivitetskravVarselRepository,
                aktivitetskravVurderingProducer = aktivitetskravVurderingProducer,
                aktivitetskravVarselProducer = mockk(),
                varselPdfService = varselPdfService,
            )

            val slot = slot<ForhandsvarselPdfDTO>()
            coEvery {
                mockedPdfGenClient.createForhandsvarselPdf(
                    any(),
                    any(),
                )
            } returns byteArrayOf(0x2E, 0x28)

            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)

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
                    forhandsvarselPdfDTO = expectedForhandsvarselPdfRequestBody,
                )
            }
        }
    }
}
