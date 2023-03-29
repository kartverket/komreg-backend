package no.kartverket.komreg.transformation.parameters

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Ident

class GardsnummerParameter(
    private val kommunenummer: Kommunenummer,
    private val gardsnumre: IntRange,
    private val nyKommune: Kommunenummer,
    private val forskyving: Int,
) : Parameter.TransformingParameter {
    override fun matches(ident: Ident): Int {
        if (ident.get<Fylkesnummer>() == kommunenummer.fylkesnummer) {
            if (ident.get<Kommunenummer.Lopenummer>() == kommunenummer.lopenummer) {
                val gardsnummer = ident.get<Matrikkelnummer.Gardsnummer>() as Matrikkelnummer.Gardsnummer?
                if (gardsnummer != null && gardsnumre.contains(gardsnummer.value)) {
                    return 3
                }
            }
        }
        return 0
    }

    override fun transform(ident: Ident): Ident {
        return Ident(ident.map.values.map { value ->
            when (value) {
                is Fylkesnummer -> nyKommune.fylkesnummer
                is Kommunenummer.Lopenummer -> nyKommune.lopenummer
                is Matrikkelnummer.Gardsnummer -> {
                    Matrikkelnummer.Gardsnummer(value.value + forskyving)
                }
                else -> value
            }
        })
    }
}
