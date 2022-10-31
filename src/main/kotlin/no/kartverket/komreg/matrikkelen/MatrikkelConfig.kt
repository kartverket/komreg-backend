package no.kartverket.komreg.matrikkelen

data class MatrikkelConfig(
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String
    )
