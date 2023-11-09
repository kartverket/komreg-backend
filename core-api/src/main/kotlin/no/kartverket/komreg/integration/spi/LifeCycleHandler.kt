package no.kartverket.komreg.integration.spi

import no.kartverket.komreg.core.KrAppBootContext

interface LifeCycleHandler {
    fun beforeRun()

    fun afterRun(successful: Boolean)
}

interface LifeCycleHandlerFactory {
    fun KrAppBootContext.create(): LifeCycleHandler
}
