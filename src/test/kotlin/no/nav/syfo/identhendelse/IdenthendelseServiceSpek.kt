package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivitetskrav.database.getAktivitetskrav
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.testhelper.*
import no.nav.syfo.testhelper.generator.*
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
private val aktivIdentIkkeOppdatert = UserConstants.THIRD_ARBEIDSTAKER_PERSONIDENT
private val inaktivIdent = UserConstants.OTHER_ARBEIDSTAKER_PERSONIDENT

class IdenthendelseServiceSpek : Spek({
    describe("${IdenthendelseService::class.java.simpleName}: handle") {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val azureAdClient = AzureAdClient(
                azureEnvironment = externalMockEnvironment.environment.azure
            )
            val pdlClient = PdlClient(
                azureAdClient = azureAdClient,
                pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
            )

            val identhendelseService = IdenthendelseService(
                database = database,
                pdlClient = pdlClient,
            )

            afterEachTest {
                database.dropData()
            }

            describe("Aktivitetskrav eksisterer for person") {
                val aktivitetskravNy = createAktivitetskravNy(
                    personIdent = inaktivIdent,
                    tilfelleStart = LocalDate.now().minusDays(50),
                )
                val aktivitetskravAutomatiskOppfylt =
                    createAktivitetskravAutomatiskOppfylt(
                        personIdent = inaktivIdent,
                        tilfelleStart = LocalDate.now().minusDays(400),
                    )
                beforeEachTest {
                    database.createAktivitetskrav(aktivitetskravNy, aktivitetskravAutomatiskOppfylt)
                }

                it("Oppdaterer aktivitetskrav når person har fått ny ident") {
                    val previousUpdatedAt = database.getAktivitetskrav(personIdent = inaktivIdent).first().updatedAt
                    val kafkaIdenthendelseDTO =
                        generateKafkaIdenthendelseDTO(
                            aktivIdent = aktivIdent,
                            inaktiveIdenter = listOf(inaktivIdent)
                        )
                    runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

                    val aktivitetskravMedInaktivIdent = database.getAktivitetskrav(personIdent = inaktivIdent)
                    aktivitetskravMedInaktivIdent.size shouldBeEqualTo 0

                    val aktivitetskravMedAktivIdent = database.getAktivitetskrav(personIdent = aktivIdent)
                    aktivitetskravMedAktivIdent.size shouldBeEqualTo 2
                    val updatedAktivitetskravNy = aktivitetskravMedAktivIdent.first()
                    updatedAktivitetskravNy.updatedAt shouldBeGreaterThan previousUpdatedAt
                }
                it("Oppdaterer ingenting når person har fått ny ident uten gamle identer") {
                    val kafkaIdenthendelseDTO =
                        generateKafkaIdenthendelseDTO(
                            aktivIdent = aktivIdent,
                            inaktiveIdenter = emptyList()
                        )
                    runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

                    val aktivitetskravMedInaktivIdent = database.getAktivitetskrav(personIdent = inaktivIdent)
                    aktivitetskravMedInaktivIdent.size shouldBeEqualTo 2

                    val aktivitetskravMedAktivIdent = database.getAktivitetskrav(personIdent = aktivIdent)
                    aktivitetskravMedAktivIdent.size shouldBeEqualTo 0
                }
                it("Oppdaterer ingenting når person mangler aktiv ident") {
                    val kafkaIdenthendelseDTO =
                        generateKafkaIdenthendelseDTO(
                            aktivIdent = null,
                            inaktiveIdenter = listOf(inaktivIdent)
                        )
                    runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

                    val aktivitetskravMedInaktivIdent = database.getAktivitetskrav(personIdent = inaktivIdent)
                    aktivitetskravMedInaktivIdent.size shouldBeEqualTo 2

                    val aktivitetskravMedAktivIdent = database.getAktivitetskrav(personIdent = aktivIdent)
                    aktivitetskravMedAktivIdent.size shouldBeEqualTo 0
                }
                it("Kaster feil og oppdaterer ingenting når person har fått ny ident, men ident er ikke oppdatert i PDL") {
                    val kafkaIdenthendelseDTO =
                        generateKafkaIdenthendelseDTO(
                            aktivIdent = aktivIdentIkkeOppdatert,
                            inaktiveIdenter = listOf(inaktivIdent)
                        )
                    runBlocking {
                        assertFailsWith(IllegalStateException::class) {
                            identhendelseService.handle(
                                kafkaIdenthendelseDTO
                            )
                        }
                    }

                    val aktivitetskravMedInaktivIdent = database.getAktivitetskrav(personIdent = inaktivIdent)
                    aktivitetskravMedInaktivIdent.size shouldBeEqualTo 2

                    val aktivitetskravMedAktivIdent = database.getAktivitetskrav(personIdent = aktivIdentIkkeOppdatert)
                    aktivitetskravMedAktivIdent.size shouldBeEqualTo 0
                }
            }
            describe("Aktivitetskrav eksisterer ikke for person") {
                it("Oppdaterer ingenting når person har fått ny ident") {
                    val kafkaIdenthendelseDTO =
                        generateKafkaIdenthendelseDTO(
                            aktivIdent = aktivIdent,
                            inaktiveIdenter = listOf(inaktivIdent)
                        )
                    runBlocking { identhendelseService.handle(kafkaIdenthendelseDTO) }

                    val aktivitetskravMedInaktivIdent = database.getAktivitetskrav(personIdent = inaktivIdent)
                    aktivitetskravMedInaktivIdent.size shouldBeEqualTo 0

                    val aktivitetskravMedAktivIdent = database.getAktivitetskrav(personIdent = aktivIdent)
                    aktivitetskravMedAktivIdent.size shouldBeEqualTo 0
                }
            }
        }
    }
})
