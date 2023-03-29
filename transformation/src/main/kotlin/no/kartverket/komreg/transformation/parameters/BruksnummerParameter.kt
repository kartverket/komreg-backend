package no.kartverket.komreg.transformation.parameters

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.integration.spi.Ident

class BruksnummerParameter(
    private val kommunenummer: Kommunenummer,
    private val gardsnummer: Matrikkelnummer.Gardsnummer,
    private val bruksnumre: IntRange,
    private val nyKommune: Kommunenummer,
    private val nyttGardsnummer: Matrikkelnummer.Gardsnummer,
    private val forskyving: Short,
) : Parameter.TransformingParameter {
    override fun matches(ident: Ident): Int {
        if (ident.get<Fylkesnummer>() == kommunenummer.fylkesnummer) {
            if (ident.get<Kommunenummer.Lopenummer>() == kommunenummer.lopenummer) {
                if (ident.get<Matrikkelnummer.Gardsnummer>() == gardsnummer) {
                    val bruksnummer = ident.get<Matrikkelnummer.Bruksnummer>()
                    if (bruksnummer != null && bruksnumre.contains(bruksnummer.value)) {
                        return 4
                    }
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
                is Matrikkelnummer.Gardsnummer -> nyttGardsnummer
                is Matrikkelnummer.Bruksnummer -> {
                    Matrikkelnummer.Bruksnummer((value.value + forskyving).toShort())
                }
                else -> value
            }
        })
    }
}