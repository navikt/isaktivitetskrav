package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.testhelper.TestDatabase
import no.nav.syfo.testhelper.TestDatabaseNotResponding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDatabase()
            application.routing {
                registerPodApi(
                    applicationState = ApplicationState(
                        alive = true,
                        ready = true
                    ),
                    database = database,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDatabase()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = false,
                        ready = false
                    ),
                    database,
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
    describe("Successful liveness and unsuccessful readiness checks when database not working") {
        with(TestApplicationEngine()) {
            start()
            val database = TestDatabaseNotResponding()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = true,
                        ready = true
                    ),
                    database,
                )
            }

            it("Returns ok on is_alive") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, "/internal/is_ready")) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
