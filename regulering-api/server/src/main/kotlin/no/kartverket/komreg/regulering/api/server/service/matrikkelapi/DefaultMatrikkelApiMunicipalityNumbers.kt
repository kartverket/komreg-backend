package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.reform.domain.type.MunicipalityNumber
import no.kartverket.komreg.regulering.reform.domain.type.NumberingScheme
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService

class DefaultMatrikkelApiMunicipalityNumbers(
    kommuneService: KommuneService,
    storeService: StoreService,
    numberingScheme: NumberingScheme,
    matrikkelContext: MatrikkelContext
) : MatrikkelApiMunicipalityNumbers {
    private val municipalityNumbers: Map<MunicipalityId, MunicipalityNumber>

    init {
        val kommuneIds = kommuneService.findAlleKommuner(matrikkelContext).item
        with(numberingScheme) {
            municipalityNumbers = storeService.getObjects(
                MatrikkelBubbleIdList().apply { this.item.addAll(kommuneIds) },
                matrikkelContext
            ).item
                .mapNotNull { (it as Kommune) }
                .associateBy(
                    { MunicipalityId(it.id.value) },
                    { MunicipalityNumber(it.kommunenummer) }
                )
        }
    }

    override fun get(id: MunicipalityId): MunicipalityNumber = municipalityNumbers.getValue(id)
}
