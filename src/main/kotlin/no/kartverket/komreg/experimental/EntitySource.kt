package no.kartverket.komreg.experimental

import kotlinx.coroutines.flow.Flow
import no.kartverket.komreg.domain.EntityData

interface EntitySource<out T : EntityData> {
    fun download(context: EntitySourceDownloadContext): Flow<Entity<out T>>
}
