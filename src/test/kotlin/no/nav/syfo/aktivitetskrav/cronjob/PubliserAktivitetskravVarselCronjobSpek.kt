package no.nav.syfo.aktivitetskrav.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.database.AktivitetskravVarselRepository
import no.nav.syfo.aktivitetskrav.domain.Aktivitetskrav
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVarsel
import no.nav.syfo.aktivitetskrav.domain.vurder
import no.nav.syfo.aktivitetskrav.kafka.ArbeidstakervarselProducer
import no.nav.syfo.aktivitetskrav.kafka.EsyfovarselHendelse
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.generateForhandsvarsel
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.concurrent.Future

class PubliserAktivitetskravVarselCronjobSpek : Spek({
    val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
    val aktivitetskrav = Aktivitetskrav.ny(
        personIdent = personIdent,
        tilfelleStart = LocalDate.now()
    )
    val forhandsvarselDTO = generateForhandsvarsel("Et forhåndsvarsel")
    val pdf = byteArrayOf(23)
    val defaultJournalpostId = "9"

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val aktivitetskravVarselRepository = AktivitetskravVarselRepository(database = database)

        val kafkaProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val arbeidstakerVarselProducer = ArbeidstakervarselProducer(
            kafkaArbeidstakervarselProducer = kafkaProducer,
        )

        val publiserAktivitetskravVarselCronjob = PubliserAktivitetskravVarselCronjob(
            aktivitetskravVarselRepository = aktivitetskravVarselRepository,
            arbeidstakervarselProducer = arbeidstakerVarselProducer,
        )

        fun createForhandsvarsel(
            aktivitetskrav: Aktivitetskrav,
            pdf: ByteArray,
            journalpostId: String? = defaultJournalpostId,
        ): AktivitetskravVarsel {
            database.createAktivitetskrav(aktivitetskrav)

            val vurdering = forhandsvarselDTO.toAktivitetskravVurdering(UserConstants.VEILEDER_IDENT)
            val updatedAktivitetskrav = aktivitetskrav.vurder(vurdering)
            val forhandsvarsel = AktivitetskravVarsel.create(forhandsvarselDTO.document)
            aktivitetskravVarselRepository.create(
                aktivitetskrav = updatedAktivitetskrav,
                varsel = forhandsvarsel,
                pdf = pdf,
            )
            if (journalpostId != null) {
                aktivitetskravVarselRepository.updateJournalpostId(forhandsvarsel, journalpostId)
            }
            return forhandsvarsel
        }

        beforeEachTest {
            clearMocks(kafkaProducer)
            coEvery {
                kafkaProducer.send(any())
            } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }
        afterEachTest {
            database.dropData()
        }

        describe("${PubliserAktivitetskravVarselCronjob::class.java.simpleName} runJob") {
            it("Publiserer journalfort forhandsvarsel") {
                createForhandsvarsel(
                    aktivitetskrav = aktivitetskrav,
                    pdf = pdf,
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
            }
            it("Publiserer ikke forhandsvarsel som ikke er journalfort") {
                createForhandsvarsel(
                    aktivitetskrav = aktivitetskrav,
                    pdf = pdf,
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
                first.updatedAt shouldBeEqualTo first.createdAt
                first.publishedAt shouldBe null
            }
            it("Publiserer ikke forhandsvarsel som allerede er publisert") {
                createForhandsvarsel(
                    aktivitetskrav = aktivitetskrav,
                    pdf = pdf,
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
        }
    }
})
