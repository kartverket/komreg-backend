@file:UseContextualSerialization(MunicipalityNumber::class)

package no.kartverket.komreg.regulering.api.server.domain.type

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber

@Serializable
data class CadastralNumber(
    val municipalityNumber: MunicipalityNumber,
    val landNumber: LandNumber,
    val titleNumber: TitleNumber,
    val leaseNumber: LeaseNumber? = null,
    val unitNumber: UnitNumber? = null
) {
    override fun toString(): String {
        return "${municipalityNumber.value}-${landNumber.value}/${titleNumber.value}/${leaseNumber?.value ?: 0}/${unitNumber?.value ?: 0}"
    }
}
