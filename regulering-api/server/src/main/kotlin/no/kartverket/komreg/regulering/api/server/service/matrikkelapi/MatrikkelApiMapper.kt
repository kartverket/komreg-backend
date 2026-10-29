package no.kartverket.komreg.regulering.api.server.domain

import no.kartverket.komreg.regulering.api.server.domain.type.*
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMunicipalityNumbers
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelnummer
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelenhet as WSMatrikkelenhet
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.MatrikkelenhetId as WSMatrikkelenhetId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId as WSTeigId

class MatrikkelApiMapper(val municipalityNumbers: MatrikkelApiMunicipalityNumbers) {

    fun WSTeigId.toDomain(): ParcelId = ParcelId(value)

    fun WSMatrikkelenhetId.toDomain(): CadestralUnitId = CadestralUnitId(value)

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

    fun WSMatrikkelenhet.toDomain(): CadestralUnit {
        val (mainParcelIdSet, otherParcelIds) = teigerForMatrikkelenhet
            .item
            .asSequence()
            .mapNotNull { it.isHovedteig to it.teigId.toDomain() }
            .partition { (hovedTeig, _) -> hovedTeig }
            .let { (mainParcels, otherParcels) ->
                val mainParcel = mainParcels.mapTo(hashSetOf()) { (_, id) -> id }
                val otherParcels = otherParcels.mapTo(hashSetOf()) { (_, id) -> id }
                mainParcel to otherParcels
            }

        return CadestralUnit(
            id = (id as WSMatrikkelenhetId).toDomain(),
            cadastralNumber = matrikkelnummer.toDomain(),
            mainParcelId = mainParcelIdSet.single { true },
            otherParcelIds = otherParcelIds
        )
    }
}
