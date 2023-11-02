package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.cache.RedisConfig
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.OpenClientEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
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
    redisConfig = RedisConfig(
        redisUri = URI("http://localhost:6379"),
        redisUsername = "redisUser",
        redisPassword = "redisPassword",
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
        isaktivitetskravpdfgen = OpenClientEnvironment(
            baseUrl = "isaktivitetskravpdfgenUrl",
        ),
        krr = ClientEnvironment(
            baseUrl = "krrUrl",
            clientId = "kkrClientId",
        )
    ),
    arenaCutoff = LocalDate.now().minusDays(365),
    electorPath = "electorPath",
    automatiskOppfyltCronJobEnabled = true,
    nyCronjobEnabled = true,
    publishExpiredVarselCronjobIntervalDelayMinutes = 10,
    outdatedCutoff = LocalDate.now().minusMonths(6),
    outdatedCronJobEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
