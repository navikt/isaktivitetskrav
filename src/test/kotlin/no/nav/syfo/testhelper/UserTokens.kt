package no.nav.syfo.testhelper

import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testhelper.UserConstants.VEILEDER_IDENT_WITH_LESETILGANG

private val externalMockEnvironment = ExternalMockEnvironment.instance

val tokenForVeilederWithFullTilgang: String = generateJWT(
    audience = externalMockEnvironment.environment.azure.appClientId,
    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    navIdent = VEILEDER_IDENT,
)

val tokenForVeilederWithLeseTilgang: String = generateJWT(
    audience = externalMockEnvironment.environment.azure.appClientId,
    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    navIdent = VEILEDER_IDENT_WITH_LESETILGANG,
)
