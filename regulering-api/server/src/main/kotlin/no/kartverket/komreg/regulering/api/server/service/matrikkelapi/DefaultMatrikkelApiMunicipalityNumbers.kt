package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService

class DefaultMatrikkelApiMunicipalityNumbers(
    kommuneService: KommuneService,
    storeService: StoreService,
    municipalityNumberFactory: MunicipalityNumber.Factory,
    matrikkelContext: MatrikkelContext
) : MatrikkelApiMunicipalityNumbers {
    private val municipalityNumbers: Map<MunicipalityId, MunicipalityNumber>

    init {
        val kommuneIds = kommuneService.findAlleKommuner(matrikkelContext).item
        with(municipalityNumberFactory) {
            municipalityNumbers = storeService.getObjects(
                MatrikkelBubbleIdList().apply { this.item.addAll(kommuneIds) },
                matrikkelContext
            ).item
                .mapNotNull { (it as Kommune) }
                .associateBy(
                    { MunicipalityId(it.id.value) },
                    { MunicipalityNumber.Companion(it.kommunenummer) }
                )
        }
    }

    override fun get(id: MunicipalityId): MunicipalityNumber = municipalityNumbers.getValue(id)
}
