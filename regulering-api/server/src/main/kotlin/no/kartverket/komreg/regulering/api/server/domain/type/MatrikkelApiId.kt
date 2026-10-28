package no.kartverket.komreg.regulering.api.server.domain

import kotlinx.serialization.Serializable

@Serializable
sealed interface MatrikkelApiId<T : Any> {
    val value: Long
}

@Serializable
@JvmInline
value class CadestralUnitId(override val value: Long) : MatrikkelApiId<CadestralUnit>

@Serializable
@JvmInline
value class MunicipalityId(override val value: Long) : MatrikkelApiId<Municipality>

@Serializable
@JvmInline
value class ParcelId(override val value: Long) : MatrikkelApiId<Parcel>
