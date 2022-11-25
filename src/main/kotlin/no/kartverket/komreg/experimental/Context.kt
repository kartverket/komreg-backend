package no.kartverket.komreg.experimental

import java.io.File
import java.util.UUID

sealed interface Context

interface EntitySourceContext<out C> : Context {
    fun getEntitySourceConfig(): C
}

interface DownloadContext : Context {
    val cacheDir: File
}

class DownloadContextShared(cycleUUID: UUID) : DownloadContext, AutoCloseable {
    override val cacheDir = File(
        System.getProperty("java.io.tmpdir"),
        "komreg-download-${cycleUUID}"
    )

    init {
        check(cacheDir.mkdirs())
        cacheDir.deleteOnExit()
    }

    override fun close() {
        cacheDir.deleteRecursively()
    }
}

class EntitySourceDownloadContext(
    private val entitySourceContext: EntitySourceContext<Any>,
    private val downloadContext: DownloadContext
) : EntitySourceContext<Any> by entitySourceContext, DownloadContext by downloadContext
