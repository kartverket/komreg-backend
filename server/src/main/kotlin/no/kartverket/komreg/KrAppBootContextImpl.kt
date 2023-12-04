package no.kartverket.komreg

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import no.kartverket.komreg.core.KrAppBootContext

object KrAppBootContextImpl : KrAppBootContext {
    override val config: Config =
        ConfigFactory
            .load("properties.conf")
            .withFallback(ConfigFactory.defaultApplication())
}
