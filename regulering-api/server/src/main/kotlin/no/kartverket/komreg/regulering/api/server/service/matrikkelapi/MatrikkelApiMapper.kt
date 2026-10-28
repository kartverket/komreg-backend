package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.api.server.domain.type.*
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelnummer

class MatrikkelApiMapper(val municipalityNumbers: MatrikkelApiMunicipalityNumbers) {

    fun KommuneId.toDomain(): MunicipalityId = MunicipalityId(value)

    fun Matrikkelnummer.toDomain(): CadastralNumber {
        require(bruksnummer in Short.MIN_VALUE..Short.MAX_VALUE)
        require(festenummer in Short.MIN_VALUE..Short.MAX_VALUE)
        return CadastralNumber(
            municipalityNumbers[kommuneId.toDomain()],
            LandNumber(gardsnummer),
            TitleNumber(bruksnummer.toShort()),
            if (festenummer != 0) LeaseNumber(festenummer.toShort()) else null,
            if (seksjonsnummer != 0) UnitNumber(seksjonsnummer.toShort()) else null
        )
    }

}
