package no.nav.syfo.application.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.registerPodApi
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.testhelper.TestDatabase
import no.nav.syfo.testhelper.TestDatabaseNotResponding
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PodApiTest {

    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                registerPodApi(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Nested
    @DisplayName("Successful liveness and readiness checks")
    inner class SuccessfulChecks {
        private val database = TestDatabase()

        @Test
        fun `returns ok on is_alive`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `returns ok on is_ready`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulChecks {
        private val database = TestDatabase()

        @Test
        fun `returns internal server error when liveness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = false, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `returns internal server error when readiness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = false),
                )
                val response = client.get("/internal/is_ready")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }
    }

    @Nested
    @DisplayName("Database not responding")
    inner class DatabaseNotResponding {
        private val database = TestDatabaseNotResponding()

        @Test
        fun `returns ok on is_alive`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_alive")
                assertTrue(response.status.isSuccess())
                assertNotNull(response.bodyAsText())
            }
        }

        @Test
        fun `returns internal server error when readiness check fails`() {
            testApplication {
                setupPodApi(
                    database = database,
                    applicationState = ApplicationState(alive = true, ready = true),
                )
                val response = client.get("/internal/is_ready")
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                assertNotNull(response.bodyAsText())
            }
        }
    }
}
