package no.nav.syfo.testhelper

class ExternalMockEnvironment private constructor() {
    val database = TestDatabase()

    companion object {
        val instance: ExternalMockEnvironment by lazy {
            ExternalMockEnvironment().also {
                it.startExternalMocks()
            }
        }
    }
}

fun ExternalMockEnvironment.startExternalMocks() {
    // TODO: Implement later
}
