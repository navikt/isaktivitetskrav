package no.nav.syfo.testhelper

import no.nav.syfo.application.*
import no.nav.syfo.application.database.DatabaseEnvironment
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.azuread.AzureEnvironment

fun testEnvironment(
    azureOpenIdTokenEndpoint: String,
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
    ),
    kafkaOppfolgingstilfellePersonProcessingEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
