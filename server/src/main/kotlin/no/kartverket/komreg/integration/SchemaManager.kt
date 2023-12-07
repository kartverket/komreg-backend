package no.kartverket.komreg.integration

class SchemaManager {

    fun getMottakerUsername(): String {
        val environment = System.getenv("environment")
        return if (environment == "prod") {
            selectProdMottakerUsername()
        } else {
            "DB_MATRIKKEL_MOTTAKER2_USERNAME"
        }
    }

    fun getMottakerPassword(): String {
        val environment = System.getenv("environment")
        return if (environment == "prod") {
            selectProdMottakerPassword()
        } else {
            "DB_MATRIKKEL_MOTTAKER2_PASSWORD"
        }
    }

    fun selectProdMottakerUsername(): String {
        // TODO: logikk for å velge mottakerskjema som er "READY"
        return "DB_MATRIKKEL_MOTTAKER1_USERNAME"
    }

    fun selectProdMottakerPassword(): String {
        // TODO: logikk for å velge mottakerskjema som er "READY"
        return "DB_MATRIKKEL_MOTTAKER1_PASSWORD"
    }
}
