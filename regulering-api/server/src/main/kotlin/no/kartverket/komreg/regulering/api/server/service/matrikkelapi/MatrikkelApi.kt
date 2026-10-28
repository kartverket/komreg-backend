package no.kartverket.komreg.regulering.api.server.service.matrikkelapi

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import no.kartverket.komreg.regulering.api.server.domain.MatrikkelApiId
import no.kartverket.komreg.regulering.api.server.domain.MatrikkelApiMapper
import no.kartverket.komreg.regulering.api.server.domain.CadestralUnitId
import no.kartverket.komreg.regulering.api.server.domain.ParcelId
import no.kartverket.matrikkelapi.v1.MatrikkelContextBuilder
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.matrikkelenhet.MatrikkelenhetService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.store.StoreService
import no.statkart.matrikkel.matrikkelapi.wsapi.v1.service.teig.TeigService
import java.util.concurrent.Executors
import kotlin.collections.firstOrNull
import kotlin.collections.joinToString
import no.kartverket.matrikkelapi.v1.Koordinatsystem as WSKoordinatsystem

internal const val SYSTEM_IDENTIFIER = "komreg-regulering-api"

class MatrikkelAPI(
    private val storeService: StoreService,
    private val meService: MatrikkelenhetService,
    private val teigService: TeigService,
    private val municipalities: MatrikkelApiMunicipalityNumbers
) {
    private val ctx = MatrikkelContextBuilder(SYSTEM_IDENTIFIER).locale("nb_NO")
        .koordinatsystem(WSKoordinatsystem.Euref89UTM33, false)
        .build()

    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val soapDispatcher = executor.asCoroutineDispatcher()

    suspend fun findParcelIdCoveringPoint(
        x: Double,
        y: Double
    ) = with(MatrikkelApiMapper) {
        withContext(soapDispatcher) {
            teigService.findTeigerForPunkt(
                wsPoint(x, y),
                ctx
            ).toDomain().apply {
                check(size <= 1) {
                    "Matrikkelen returned more than one parcel id from findTeigerForPunkt($x, $y): ${joinToString()}"
                }
            }.firstOrNull()
        }
    }

    suspend fun findCadestralUnitIdsForParcels(
        parcelIds: Set<ParcelId>
    ): Map<ParcelId, Set<CadestralUnitId>> = with(MatrikkelApiMapper) {
        withContext(soapDispatcher) {
            meService.findMatrikkelenheterForTeiger(
                parcelIds.toMatrikkelApi(),
                ctx
            ).toDomain()
        }
    }

    suspend fun <T : Any> get(ids: Iterable<MatrikkelApiId<out T>>): List<T> = with(MatrikkelApiMapper) {
        withContext(soapDispatcher) {
            with(municipalities) {
                storeService.getObjects(ids.toMatrikkelApi(), ctx).toDomainOrThrow()
            }.mapNotNull {
                @Suppress("UNCHECKED_CAST")
                it as T
            }
        }
    }

}
