package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.aktivitetskrav.kafka.*
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.createAktivitetskrav
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.createAktivitetskravNy
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.Future

class PublishExpiredVarslerCronJobSpek : Spek({
    val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

        val expiredVarselProducerMock = mockk<KafkaProducer<String, ExpiredVarsel>>()
        val expiredVarselProducer = ExpiredVarselProducer(producer = expiredVarselProducerMock)

        val aktivitetskravVarselService = AktivitetskravVarselService(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            aktivitetskravVurderingProducer = mockk(),
            arbeidstakervarselProducer = mockk(),
            aktivitetskravVarselProducer = mockk(),
            expiredVarselProducer = expiredVarselProducer,
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
            krrClient = externalMockEnvironment.krrClient,
        )

        val publishExpiredVarslerCronJob = PublishExpiredVarslerCronJob(
            aktivitetskravVarselService = aktivitetskravVarselService,
            externalMockEnvironment.environment.publishExpiresVarselCronjobIntervalDelayMinutes,
        )

        beforeEachTest {
            clearMocks(expiredVarselProducerMock)
            coEvery {
                expiredVarselProducerMock.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }
        describe(PublishExpiredVarslerCronJob::class.java.simpleName) {
            it("Should publish expired varsler to kafka") {
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
                val varsel =
                    AktivitetskravVarsel.create(forhandsvarselDTO.document, svarfrist = LocalDate.now().minusWeeks(1))
                aktivitetskravVarselRepository.create(
                    aktivitetskrav = updatedAktivitetskrav,
                    varsel = varsel,
                    pdf = pdf,
                )

                runBlocking {
                    val result = publishExpiredVarslerCronJob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 1
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
})
