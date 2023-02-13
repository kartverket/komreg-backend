package no.kartverket.komreg.matrikkel.old

data class MatrikkelConfig(
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String,
)
