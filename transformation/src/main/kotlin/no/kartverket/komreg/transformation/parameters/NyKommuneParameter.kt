package no.kartverket.komreg.transformation.parameters

import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.Transformation

class NyKommuneParameter(
    private val kommunenummer: Kommunenummer,
    private val kommunenavn: String,
) : Parameter.SpawningParameter {
    override fun order(): Int = 1

    override fun spawn(): Transformation {
        return Transformation(
            "Kommune:${kommunenummer.fylkesnummer.value * 100 + kommunenummer.lopenummer.value}",
            null,
            "new",
            Ident(
                kommunenummer.fylkesnummer,
                kommunenummer.lopenummer
            ),
            null,
            kommunenavn
        )
    }
}
