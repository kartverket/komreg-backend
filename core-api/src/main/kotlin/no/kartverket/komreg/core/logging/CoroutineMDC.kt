package no.kartverket.komreg.core.logging

import kotlinx.coroutines.*
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

/**
 * CoroutineContext element håndterer SLF4J's MDC
 * ([Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html)).
 *
 */
@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
data class CoroutineMDC (
    private var contextMap: Map<String, String?> = mutableMapOf()
) : CopyableThreadContextElement<Map<String, String?>> {
    companion object Key : CoroutineContext.Key<CoroutineMDC>

    override val key: Key get() = Key

    operator fun get(k: String): String? {
        return contextMap[k]
    }

    override fun updateThreadContext(context: CoroutineContext): Map<String, String?> {
        val currentThreadContext: MutableMap<String, String>? = MDC.getCopyOfContextMap()
        if (contextMap.isNotEmpty()) {
            MDC.setContextMap(contextMap)
        } else {
            MDC.setContextMap(null)
        }
        return currentThreadContext ?: emptyMap()
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String?>) {
        contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
        if (oldState.isNotEmpty()) {
            MDC.setContextMap(oldState)
        } else {
            MDC.setContextMap(null)
        }
    }

    override fun copyForChild(): CoroutineMDC {
        return copy(contextMap = MDC.getCopyOfContextMap() ?: emptyMap())
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineMDC {
        return when (overwritingElement) {
            is CoroutineMDC -> CoroutineMDC(mergeContextMaps(contextMap, overwritingElement.contextMap))
            else -> copyForChild()
        }
    }

    private fun mergeContextMaps(
        contextMap: Map<String, String?>?,
        overwritingContextMap: Map<String, String?>?) : MutableMap<String, String?> {
        return when {
            overwritingContextMap.isNullOrEmpty() -> contextMap?.toMutableMap() ?: mutableMapOf()
            contextMap.isNullOrEmpty() -> overwritingContextMap.toMutableMap()
            else -> contextMap.toMutableMap().apply { putAll(overwritingContextMap) }
        }
    }

}
