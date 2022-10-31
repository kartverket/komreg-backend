package no.kartverket.komreg.domain

sealed interface Matrikkelenhet : EntityData {
    val kommunenr: Int
    val gardsnummer: Int
    val bruksnummer: Int
}

sealed interface Seksjonert : Matrikkelenhet {
    val seksjoner: Set<Seksjon>
}
sealed interface KanHaFestegrunn : Matrikkelenhet {
    val festegrunner: Set<Festegrunn>
}

data class Grunneiendom(
    override val kommunenr: Int,
    override val gardsnummer: Int,
    override val bruksnummer: Int,
    override val festegrunner: Set<Festegrunn>,
    override val seksjoner: Set<Seksjon>
) : KanHaFestegrunn, Seksjonert

data class Festegrunn(
    val grunneiendom: KanHaFestegrunn,
    val festenummer : Int,
    override val seksjoner: Set<Seksjon>
) : KanHaFestegrunn by grunneiendom, Seksjonert

data class Seksjon(val seksjonert: Seksjonert, val seksjonsnummer : Int) : Seksjonert by seksjonert
