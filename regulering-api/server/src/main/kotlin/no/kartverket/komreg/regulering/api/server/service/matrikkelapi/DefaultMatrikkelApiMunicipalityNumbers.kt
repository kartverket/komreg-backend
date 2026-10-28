package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.kartverket.komreg.regulering.api.server.domain.MunicipalityId
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber
import no.kartverket.matrikkelapi.v1.Koordinatsystem
import no.kartverket.matrikkelapi.v1.MatrikkelContextBuilder
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService

class DefaultMatrikkelApiMunicipalityNumbers(
    kommuneService: KommuneService,
    storeService: StoreService,
    municipalityNumberFactory: MunicipalityNumber.Factory
) : MatrikkelApiMunicipalityNumbers {
    private val municipalityNumbers: Map<MunicipalityId, MunicipalityNumber>

    init {
        val ctx = MatrikkelContextBuilder(SYSTEM_IDENTIFIER).locale("nb_NO")
            .koordinatsystem(Koordinatsystem.Euref89UTM33, false)
            .build()
        val kommuneIds = kommuneService.findAlleKommuner(ctx).item
        with(municipalityNumberFactory) {
            municipalityNumbers = storeService.getObjects(
                MatrikkelBubbleIdList().apply { this.item.addAll(kommuneIds) },
                ctx
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
