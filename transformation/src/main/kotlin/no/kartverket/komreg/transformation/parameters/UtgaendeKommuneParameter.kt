package no.kartverket.komreg.transformation.parameters

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Ident

class UtgaendeKommuneParameter(
    private val kommunenummer: Kommunenummer,
    private val nyKommune: Kommunenummer,
) : Parameter.TransformingParameter {
    override fun matches(ident: Ident): Int {
        if (ident.get<Fylkesnummer>() == kommunenummer.fylkesnummer) {
            if (ident.get<Kommunenummer.Lopenummer>() == kommunenummer.lopenummer) {
                return 2
            }
        }
        return 0
    }

    override fun transform(ident: Ident): Ident {
        // TODO: Kommune skal transformeres på annen måte enn ved å transformere ident
        return Ident(ident.map.values.map { value ->
            when (value) {
                is Fylkesnummer -> nyKommune.fylkesnummer
                is Kommunenummer.Lopenummer -> nyKommune.lopenummer
                else -> value
            }
        })
    }
}
