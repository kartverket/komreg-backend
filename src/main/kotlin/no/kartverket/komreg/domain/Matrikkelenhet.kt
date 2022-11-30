package no.kartverket.komreg.domain

import no.kartverket.komreg.experimental.Referencable
import no.kartverket.komreg.experimental.Id

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
    private val festegrunnData: Set<FestegrunnData.Detached>,
    private val seksjonsData: Set<SeksjonData.Detached>,
) : KanHaFestegrunn, Seksjonert {
    override val festegrunner by lazy { festegrunnData.map { festegrunnData -> Festegrunn(this, festegrunnData) }.toSet() }
    override val seksjoner by lazy { seksjonsData.map { seksjonsData -> Seksjon(this, seksjonsData) }.toSet() }
}

interface FestegrunnData : Referencable<Matrikkelenhet> {
    val festenummer: Int
    val seksjonsData: Set<SeksjonData.Detached>
    data class Detached(
        override val id: Id<Matrikkelenhet>,
        override val festenummer: Int,
        override val seksjonsData: Set<SeksjonData.Detached>
    ) : FestegrunnData
}

interface SeksjonData : Referencable<Matrikkelenhet> {
    val seksjonsnummer: Int

    data class Detached(
        override val id: Id<Matrikkelenhet>, override val seksjonsnummer: Int
    ) : SeksjonData
}

data class Festegrunn(
    val grunneiendom: KanHaFestegrunn,
    private val data: FestegrunnData,
) : FestegrunnData by data, Matrikkelenhet by grunneiendom, Seksjonert {
    override val seksjoner: Set<Seksjon> by lazy { seksjonsData.map { Seksjon(this, it) }.toSet() }
}

data class Seksjon(val seksjonert: Seksjonert, private val data: SeksjonData) : SeksjonData by data, Matrikkelenhet by seksjonert
