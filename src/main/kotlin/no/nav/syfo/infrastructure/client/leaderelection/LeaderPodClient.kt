package no.nav.syfo.infrastructure.client.leaderelection

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.util.configuredJacksonMapper
import org.slf4j.LoggerFactory
import java.net.InetAddress

class LeaderPodClient(
    private val electorPath: String,
) {
    private val httpClient = HttpClient(Apache) {
        expectSuccess = true
    }

    private val objectMapper: ObjectMapper = configuredJacksonMapper()

    fun isLeader(): Boolean {
        try {
            val electorUrl = getElectorUrl(electorPath)
            log.debug("Looking for leader at url=$electorUrl")
            return runBlocking {
                val response: HttpResponse = httpClient.get(electorUrl) {
                    accept(ContentType.Text.Plain)
                }
                val leaderPodDTO: LeaderPodDTO = objectMapper.readValue(
                    response.bodyAsText()
                )
                val hostname: String = InetAddress.getLocalHost().hostName

                if (hostname == leaderPodDTO.name) {
                    log.debug("Pod with $hostname is the leader")
                    true
                } else {
                    log.debug("Pod with $hostname is not leader, leader is:${leaderPodDTO.name}")
                    false
                }
            }
        } catch (ex: Exception) {
            log.error("Exception caught while looking for leader.", ex)
            return false
        }
    }

    private fun getElectorUrl(electorPath: String) = "http://$electorPath"

    companion object {
        private val log = LoggerFactory.getLogger(LeaderPodClient::class.java)
    }
}
