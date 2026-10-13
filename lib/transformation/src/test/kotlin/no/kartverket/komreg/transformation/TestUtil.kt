package no.kartverket.komreg.transformation

import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Ident2

operator fun FylkeIdent.div(kommuneIdent: Kommunenummer.Lopenummer): Ident2<Fylkesnummer, Kommunenummer.Lopenummer> =
    appendWith(Kommune.KommuneIdent, kommuneIdent)