package no.kartverket.komreg.regulering.api.server.domain

import no.kartverket.komreg.regulering.api.server.domain.type.*
import no.kartverket.komreg.regulering.api.server.service.matrikkelapi.MatrikkelApiMunicipalityNumbers
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleObjectList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelnummer
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigIdTilMatrikkelenhetIdsMap
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleId as WSMatrikkelApiId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.geometri.Point as WSPoint
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.Matrikkelenhet as WSMatrikkelenhet
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.MatrikkelenhetId as WSMatrikkelenhetId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigId as WSTeigId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.matrikkelenhet.TeigIdList as WSTeigIdList

interface MatrikkelApiMapper {
    fun ParcelId.toMatrikkelApi(): WSTeigId
    fun WSTeigId.toDomain(): ParcelId

    fun CadestralUnitId.toMatrikkelApi(): WSMatrikkelenhetId
    fun WSMatrikkelenhetId.toDomain(): CadestralUnitId

    fun KommuneId.toDomain(): MunicipalityId
    fun MunicipalityId.toMatrikkelApi(): KommuneId

    context(municipalityNumbers: MatrikkelApiMunicipalityNumbers)
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

    context(municipalityNumbers: MatrikkelApiMunicipalityNumbers)
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

    fun MatrikkelApiId<*>.toMatrikkelApi(): WSMatrikkelApiId = when (val id = this) {
        is ParcelId -> id.toMatrikkelApi()
        is CadestralUnitId -> id.toMatrikkelApi()
        is MunicipalityId -> id.toMatrikkelApi()
    }

    fun Iterable<MatrikkelApiId<*>?>.toMatrikkelApi() = MatrikkelBubbleIdList().apply {
        item.addAll(this@toMatrikkelApi.map { it?.toMatrikkelApi() })
    }

    fun Iterable<ParcelId?>.toMatrikkelApi() = WSTeigIdList().apply {
        item.addAll(this@toMatrikkelApi.map { it?.toMatrikkelApi() })
    }

    fun WSTeigIdList.toDomain(): List<ParcelId?> = item.map { it?.toDomain() }

    fun wsPoint(x: Double, y: Double): WSPoint = WSPoint().apply {
        this.x = x
        this.y = y
    }

    fun TeigIdTilMatrikkelenhetIdsMap.toDomain(): Map<ParcelId, Set<CadestralUnitId>> {
        return this.entry.associateTo(HashMap()) { it.key.toDomain() to it.value.item.mapTo(HashSet()) { wsTeigId -> wsTeigId.toDomain() } }
    }


    context(municipalities: MatrikkelApiMunicipalityNumbers)
    fun MatrikkelBubbleObjectList.toDomainOrThrow(): List<Any?> = item.map {
        when (it) {
            is WSMatrikkelenhet -> it.toDomain()
            else -> throw IllegalArgumentException("Unknown MatrikkelBubbleObject type: ${it::class.qualifiedName}")
        }
    }

    companion object : MatrikkelApiMapper {
        override fun ParcelId.toMatrikkelApi(): WSTeigId = WSTeigId().apply { value = this@toMatrikkelApi.value }

        override fun CadestralUnitId.toMatrikkelApi(): WSMatrikkelenhetId = WSMatrikkelenhetId().apply { value = this@toMatrikkelApi.value }

        override fun MunicipalityId.toMatrikkelApi(): KommuneId = KommuneId().apply { value = this@toMatrikkelApi.value }

        override fun WSTeigId.toDomain(): ParcelId = ParcelId(value)

        override fun WSMatrikkelenhetId.toDomain(): CadestralUnitId = CadestralUnitId(value)

        override fun KommuneId.toDomain(): MunicipalityId = MunicipalityId(value)
    }
}
