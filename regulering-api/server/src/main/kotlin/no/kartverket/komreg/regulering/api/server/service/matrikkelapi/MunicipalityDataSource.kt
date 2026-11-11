package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleIdList
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelBubbleObject
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.MatrikkelContext
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.KommuneId
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommunevapen
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.kommune.KommuneService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Fylke as WsFylke
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.domain.kommune.Kommune as WsKommune

/** Blokkerende oppslag mot Matrikkel API som [MatrikkelApiMunicipalityCache] trenger. */
interface MunicipalityDataSource {
    fun fetchCounties(): List<WsFylke>
    fun fetchMunicipalities(): List<WsKommune>

    /** Objekter (for eksempel `Postnummeromrade` og `JuridiskPerson`) som finnes. Manglende ID-er hoppes over. */
    fun fetchObjects(ids: Collection<MatrikkelBubbleId>): List<MatrikkelBubbleObject>

    /** `null` hvis kommunen ikke har kommunevåpen. */
    fun fetchCoatOfArms(municipality: KommuneId): ByteArray?
}

class MatrikkelApiMunicipalityDataSource(
    private val kommuneService: KommuneService,
    private val storeService: StoreService,
    private val matrikkelContext: MatrikkelContext,
) : MunicipalityDataSource {
    override fun fetchCounties(): List<WsFylke> =
        storeService.getObjects(
            MatrikkelBubbleIdList().apply { item.addAll(kommuneService.findAlleFylker(matrikkelContext).item) },
            matrikkelContext,
        ).item.filterIsInstance<WsFylke>()

    override fun fetchMunicipalities(): List<WsKommune> =
        storeService.getObjects(
            MatrikkelBubbleIdList().apply { item.addAll(kommuneService.findAlleKommuner(matrikkelContext).item) },
            matrikkelContext,
        ).item.filterIsInstance<WsKommune>()

    override fun fetchObjects(ids: Collection<MatrikkelBubbleId>): List<MatrikkelBubbleObject> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            storeService.getObjectsIgnoreMissing(
                MatrikkelBubbleIdList().apply { item.addAll(ids) },
                matrikkelContext,
            ).item.filterNotNull()
        }

    override fun fetchCoatOfArms(municipality: KommuneId): ByteArray? {
        val id = kommuneService.findKommunevapenForKommune(municipality, matrikkelContext) ?: return null
        return fetchObjects(listOf(id)).filterIsInstance<Kommunevapen>().singleOrNull()?.kommunevapen
    }
}
