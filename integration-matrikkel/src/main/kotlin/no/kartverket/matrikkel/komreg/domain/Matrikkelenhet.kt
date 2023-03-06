package no.kartverket.matrikkel.komreg.domain

data class Matrikkelenhet(
    val id: Long,
    val kommunenummer: Long,
    val gardsnummer: Int,
    val bruksnummer: Int,
    val festenummer: Int,
    val seksjonernummer: Int,
)
