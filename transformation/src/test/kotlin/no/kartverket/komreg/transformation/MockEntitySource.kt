package no.kartverket.komreg.transformation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.EntitySource

fun mockEntitySource(init: suspend MockEntitySourceBuilder.() -> Unit): EntitySource {
    return MockEntitySource(flow {
        MockEntitySourceBuilder(this)
            .init()
    })
}

private class MockEntitySource(override val entityFlow: Flow<Entity>) : EntitySource {
    override val id: String = "MockEntitySource"

    override val preValidation: Set<() -> Unit> = emptySet()

    override val postValidation: Set<() -> Unit> = emptySet()
}

class MockEntitySourceBuilder(private val collector: FlowCollector<Entity>) {
    suspend fun entity(
        id: String,
        ident: Map<*, *>? = null,
    ) {
        collector.emit(
            Entity(
                id,
                ident,
            )
        )
    }

    fun matrikkelnummer(
        kommunenummer: String,
        gardsnummer: Int,
        bruksnummer: Short,
        festenummer: Short = 0,
        seksjonsnummer: Short = 0,
    ): Map<*, *> {
        val fylkesnummer = kommunenummer.substring(0, kommunenummer.length - 2)
        val kommunelopenummer = kommunenummer.substring(kommunenummer.length - 2)

        return Entity.typeMap(
            Fylkesnummer(fylkesnummer.toLong()),
            Kommunenummer.Lopenummer(kommunelopenummer.toByte()),
            Matrikkelnummer.Gardsnummer(gardsnummer),
            Matrikkelnummer.Bruksnummer(bruksnummer),
            Matrikkelnummer.Festenummer(festenummer),
            Matrikkelnummer.Seksjonsnummer(seksjonsnummer)
        )
    }
}
