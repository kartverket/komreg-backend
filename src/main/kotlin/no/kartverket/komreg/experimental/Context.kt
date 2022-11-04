package no.kartverket.komreg.experimental

sealed interface Context

interface EntitySourceContext<out C> : Context {
    fun getEntitySourceConfig(): C
}

data class DownloadContext(private val entitySourceContext: EntitySourceContext<Any>) :
    EntitySourceContext<Any> by entitySourceContext
