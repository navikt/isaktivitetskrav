package no.nav.syfo.application.api.auth

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.api.auth")

fun Application.installJwtAuthentication(
    jwtIssuerList: List<JwtIssuer>,
) {
    install(Authentication) {
        jwtIssuerList.forEach { jwtIssuer ->
            configureJwt(
                jwtIssuer = jwtIssuer,
            )
        }
    }
}

private fun AuthenticationConfig.configureJwt(
    jwtIssuer: JwtIssuer,
) {
    val jwkProvider = JwkProviderBuilder(URL(jwtIssuer.wellKnown.jwksUri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    jwt(name = jwtIssuer.jwtIssuerType.name) {
        verifier(
            jwkProvider = jwkProvider,
            issuer = jwtIssuer.wellKnown.issuer,
        )
        validate { credential ->
            val credentialsHasExpectedAudience = credential.inExpectedAudience(
                expectedAudience = jwtIssuer.acceptedAudienceList
            )
            if (credentialsHasExpectedAudience) {
                JWTPrincipal(credential.payload)
            } else {
                log.warn(
                    "Auth: Unexpected audience for jwt {}, {}",
                    StructuredArguments.keyValue("issuer", credential.payload.issuer),
                    StructuredArguments.keyValue("audience", credential.payload.audience),
                )
                null
            }
        }
    }
}

private fun JWTCredential.inExpectedAudience(expectedAudience: List<String>) = expectedAudience.any {
    this.payload.audience.contains(it)
}
