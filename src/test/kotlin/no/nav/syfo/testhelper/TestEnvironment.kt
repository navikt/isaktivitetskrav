package no.nav.syfo.testhelper

import no.nav.syfo.api.cache.ValkeyConfig
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.infrastructure.client.ClientEnvironment
import no.nav.syfo.infrastructure.client.ClientsEnvironment
import no.nav.syfo.infrastructure.client.OpenClientEnvironment
import no.nav.syfo.infrastructure.client.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import java.net.URI
import java.time.LocalDate

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isaktivitetskrav_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isaktivitetskrav-client-id",
        appClientSecret = "isaktivitetskrav-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    kafka = KafkaEnvironment(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
    ),
    valkeyConfig = ValkeyConfig(
        valkeyUri = URI("http://localhost:6379"),
        valkeyDB = 0,
        valkeyUsername = "valkeyUser",
        valkeyPassword = "valkeyPassword",
        ssl = false,
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "isTilgangskontrollUrl",
            clientId = "dev-fss.teamsykefravr.istilgangskontroll",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "pdlClientId",
        ),
        dokarkiv = ClientEnvironment(
            baseUrl = "dokarkivUrl",
            clientId = "dokarkivClientId",
        ),
        ispdfgen = OpenClientEnvironment(
            baseUrl = "ispdfgenUrl",
        ),
    ),
    arenaCutoff = LocalDate.now().minusDays(365),
    electorPath = "electorPath",
    isJournalforingRetryEnabled = true,
    outdatedCutoffMonths = 6,
    outdatedCronJobEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
