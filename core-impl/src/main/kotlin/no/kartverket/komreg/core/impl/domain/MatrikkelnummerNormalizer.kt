package no.kartverket.komreg.core.impl.domain

import no.kartverket.komreg.core.And
import no.kartverket.komreg.core.and
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.core.domain.Matrikkelnummer
import no.kartverket.komreg.core.product
import no.kartverket.komreg.core.spi.Normalizer
import kotlin.reflect.KClass

class MatrikkelnummerNormalizer : Normalizer<Matrikkelnummer> {
    override val type: KClass<Matrikkelnummer>
        get() = Matrikkelnummer::class

    override fun normalize(a: Matrikkelnummer?): And<*, *> =
        if (a != null) {
            a.fylkesnummer.product and
                a.kommunenummer.lopenummer and
                a.gardsnummer and
                a.bruksnummer and
                a.festenummer and
                a.seksjonsnummer
        } else {
            (null as Fylkesnummer?).product and
                null as Kommunenummer.Lopenummer? and
                null as Matrikkelnummer.Gardsnummer? and
                null as Matrikkelnummer.Bruksnummer? and
                null as Matrikkelnummer.Festenummer? and
                null as Matrikkelnummer.Seksjonsnummer?
        }
}
