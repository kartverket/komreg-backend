package no.kartverket.komreg

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import no.kartverket.komreg.core.KrAppBootContext
import java.util.Properties

object KrAppBootContextImpl : KrAppBootContext {

    override val config: Config =
        ConfigFactory
            .parseProperties(
                Properties().apply {
                    setProperty("integration.matrikkel.mottaker.jdbcUrl", env("DB_MATRIKKEL_JDBC_URL"))
                    setProperty("integration.matrikkel.mottaker.user", env(mottakerUsername))
                    setProperty("integration.matrikkel.mottaker.password", env(mottakerPassword))
                    setProperty("integration.matrikkel.backing.jdbcUrl", env("DB_MATRIKKEL_JDBC_URL"))
                    setProperty("integration.matrikkel.backing.user", env("DB_MATRIKKEL_BACKING_USERNAME"))
                    setProperty("integration.matrikkel.backing.password", env("DB_MATRIKKEL_BACKING_PASSWORD"))
                    setProperty("integration.matrikkel.system.jdbcUrl", env("DB_MATRIKKEL_JDBC_URL"))
                    setProperty("integration.matrikkel.system.user", env("DB_MATRIKKEL_SYSTEM_USERNAME"))
                    setProperty("integration.matrikkel.system.password", env("DB_MATRIKKEL_SYSTEM_PASSWORD"))
                },
            )
            .withFallback(ConfigFactory.defaultApplication())
}

private fun env(v: String?): String {
    if (v == null || env[v] == null) {
        logger.warn("Mangler miljøvariabel-verdi for $v")
        return "ENV_MISSING"
    }
    return env[v]
}
