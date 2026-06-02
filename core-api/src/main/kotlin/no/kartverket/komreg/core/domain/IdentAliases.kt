package no.kartverket.komreg.core.domain

import no.kartverket.komreg.integration.spi.Ident1
import no.kartverket.komreg.integration.spi.Ident2

typealias FylkeIdent = Ident1<Fylkesnummer>
typealias KommuneIdent = Ident2<Fylkesnummer, Kommunenummer.Lopenummer>