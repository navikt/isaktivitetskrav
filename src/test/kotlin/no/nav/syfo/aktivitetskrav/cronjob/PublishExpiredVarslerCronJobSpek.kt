package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.AktivitetskravVarselService
import no.nav.syfo.aktivitetskrav.VarselPdfService
import no.nav.syfo.infrastructure.database.AktivitetskravRepository
import no.nav.syfo.infrastructure.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.*
import no.nav.syfo.infrastructure.kafka.domain.ExpiredVarsel
import no.nav.syfo.infrastructure.kafka.ExpiredVarselProducer
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
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
        val aktivitetskravRepository = AktivitetskravRepository(database = database)

        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

        val expiredVarselProducerMock = mockk<KafkaProducer<String, ExpiredVarsel>>()
        val expiredVarselProducer = ExpiredVarselProducer(producer = expiredVarselProducerMock)

        val aktivitetskravVarselService = AktivitetskravVarselService(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            aktivitetskravVurderingProducer = mockk(),
            aktivitetskravVarselProducer = mockk(),
            expiredVarselProducer = expiredVarselProducer,
            varselPdfService = VarselPdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            ),
        )

        val publishExpiredVarslerCronJob = PublishExpiredVarslerCronJob(
            aktivitetskravVarselService = aktivitetskravVarselService,
            externalMockEnvironment.environment.publishExpiredVarselCronjobIntervalDelayMinutes,
        )

        fun createAktivitetskravWithVurdering(
            personIdent: PersonIdent
        ): Aktivitetskrav {
            val tenWeeksAgo = LocalDate.now().minusWeeks(10)
            val aktivitetskrav = createAktivitetskravNy(tenWeeksAgo, personIdent = personIdent)
            val vurdering = AktivitetskravVurdering.create(
                status = AktivitetskravStatus.FORHANDSVARSEL,
                createdBy = UserConstants.VEILEDER_IDENT,
                beskrivelse = "En test vurdering",
                arsaker = emptyList(),
                frist = tenWeeksAgo,
            )
            aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
            return aktivitetskrav.vurder(vurdering)
        }

        fun createNAktivitetskravWithForhandsvarsel(n: Int): Pair<List<Aktivitetskrav>, List<AktivitetskravVarsel>> {
            val allAktivitetskrav = mutableListOf<Aktivitetskrav>()
            val allVarsler = mutableListOf<AktivitetskravVarsel>()
            for (i in 1..n) {
                val aktivitetskrav = createAktivitetskravWithVurdering(
                    if (n >= 10) UserConstants.ARBEIDSTAKER_PERSONIDENT
                    else PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT.value.dropLast(1).plus("$i"))
                )
                val varsel =
                    createExpiredForhandsvarsel(forhandsvarselDTO.document)
                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    aktivitetskrav = aktivitetskrav,
                    varsel = varsel,
                    newVurdering = aktivitetskrav.vurderinger.first(),
                    pdf = pdf,
                )
            }
            return Pair(allAktivitetskrav, allVarsler)
        }

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
            it("Publishes expired varsel to kafka when svarfrist is today or earlier") {
                val newAktivitetskrav = createAktivitetskravWithVurdering(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val varsel =
                    createExpiredForhandsvarsel(forhandsvarselDTO.document)
                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    aktivitetskrav = newAktivitetskrav,
                    varsel = varsel,
                    newVurdering = newAktivitetskrav.vurderinger.first(),
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
            it("Does not publish anything to kafka when there is no expired varsler") {
                val newAktivitetskrav = createAktivitetskravWithVurdering(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                val varsel =
                    AktivitetskravVarsel.create(
                        VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER,
                        forhandsvarselDTO.document
                    ).copy(
                        svarfrist = LocalDate.now().plusWeeks(1)
                    )

                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    aktivitetskrav = newAktivitetskrav,
                    varsel = varsel,
                    newVurdering = newAktivitetskrav.vurderinger.first(),
                    pdf = pdf,
                )

                runBlocking {
                    val result = publishExpiredVarslerCronJob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    expiredVarselProducerMock.send(any())
                }
            }
            it("Does not publish varsel without svarfrist to kafka") {
                var aktivitetskrav = createAktivitetskravNy(tilfelleStart = LocalDate.now().minusWeeks(10))
                aktivitetskravRepository.createAktivitetskrav(aktivitetskrav)
                aktivitetskrav = createAktivitetskravUnntak(nyAktivitetskrav = aktivitetskrav)
                val varsel = AktivitetskravVarsel.create(
                    document = generateDocumentComponentDTO("Fritekst"),
                    type = VarselType.UNNTAK,
                )
                aktivitetskravVarselRepository.createAktivitetskravVurderingWithVarselPdf(
                    aktivitetskrav = aktivitetskrav,
                    newVurdering = aktivitetskrav.vurderinger.first(),
                    varsel = varsel,
                    pdf = pdf,
                )

                aktivitetskravVarselRepository.getVarselForVurdering(vurderingUuid = aktivitetskrav.vurderinger.first().uuid)
                    .shouldNotBeNull()

                runBlocking {
                    val result = publishExpiredVarslerCronJob.runJob()
                    result.failed shouldBeEqualTo 0
                    result.updated shouldBeEqualTo 0
                }

                verify(exactly = 0) {
                    expiredVarselProducerMock.send(any())
                }
            }
            it("Fails publishing to kafka for one expired varsel and succeed for two others") {
                createNAktivitetskravWithForhandsvarsel(3)
                coEvery {
                    expiredVarselProducerMock.send(
                        match<ProducerRecord<String, ExpiredVarsel>> { record ->
                            record.value().personIdent.value == "12345678911"
                        }
                    )
                } coAnswers {
                    throw Exception("Publishing on kafka failed")
                }
                runBlocking {
                    val result = publishExpiredVarslerCronJob.runJob()
                    result.failed shouldBeEqualTo 1
                    result.updated shouldBeEqualTo 2
                }
            }
        }
    }
})
