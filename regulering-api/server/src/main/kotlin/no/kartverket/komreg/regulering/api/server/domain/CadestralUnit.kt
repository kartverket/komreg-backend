package no.kartverket.komreg.regulering.api.server.domain

import kotlinx.serialization.Serializable
import no.kartverket.komreg.regulering.api.server.domain.type.CadastralNumber

@Serializable
@ConsistentCopyVisibility
data class CadestralUnit private constructor(
    val id: CadestralUnitId,
    val cadastralNumber: CadastralNumber,
    val mainParcelId: ParcelId,
    val otherParcelIds: Set<ParcelId>
) {

    companion object {
        operator fun invoke(
            id: CadestralUnitId,
            cadastralNumber: CadastralNumber,
            mainParcelId: ParcelId,
            otherParcelIds: Collection<ParcelId>
        ): CadestralUnit {
            val otherParcelIdsSet = HashSet(otherParcelIds)
            otherParcelIdsSet.remove(mainParcelId)
            return CadestralUnit(id, cadastralNumber, mainParcelId, otherParcelIdsSet)
        }
    }
}
