package no.nav.syfo.util

const val NAV_CALL_ID_HEADER = "Nav-Call-Id"
const val NAV_PERSONIDENT_HEADER = "nav-personident"

fun bearerHeader(token: String) = "Bearer $token"
