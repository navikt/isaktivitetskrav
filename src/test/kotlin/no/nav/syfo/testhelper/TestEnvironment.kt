package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.ClientEnvironment
import no.nav.syfo.client.ClientsEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment
import java.time.LocalDate

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
    syfoTilgangskontrollUrl: String,
    pdlUrl: String,
) = Environment(
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
        openidConfigTokenEndpoint = azureOpenIdTokenEndpoint,
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
    clients = ClientsEnvironment(
        syfotilgangskontroll = ClientEnvironment(
            baseUrl = syfoTilgangskontrollUrl,
            clientId = "dev-fss.teamsykefravr.syfotilgangskontroll",
        ),
        pdl = ClientEnvironment(
            baseUrl = pdlUrl,
            clientId = "pdlClientId",
        ),
    ),
    kafkaOppfolgingstilfellePersonProcessingEnabled = true,
    toggleKafkaConsumerIdenthendelseEnabled = true,
    arenaCutoff = LocalDate.now().minusDays(365),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
