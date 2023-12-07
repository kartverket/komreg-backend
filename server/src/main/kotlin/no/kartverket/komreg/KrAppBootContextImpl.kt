package no.kartverket.komreg

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.integration.SchemaManager
import java.util.Properties

object KrAppBootContextImpl : KrAppBootContext {
    val schemaManager = SchemaManager()
    override val config: Config =
        ConfigFactory
            .parseProperties(
                Properties().apply {
                    setProperty("integration.matrikkel.mottaker.jdbcUrl", env["DB_MATRIKKEL_JDBC_URL"])
                    setProperty("integration.matrikkel.mottaker.user", env[schemaManager.getMottakerUsername()])
                    setProperty("integration.matrikkel.mottaker.password", env[schemaManager.getMottakerPassword()])
                    setProperty("integration.matrikkel.backing.jdbcUrl", env["DB_MATRIKKEL_JDBC_URL"])
                    setProperty("integration.matrikkel.backing.user", env["DB_MATRIKKEL_BACKING_USERNAME"])
                    setProperty("integration.matrikkel.backing.password", env["DB_MATRIKKEL_BACKING_PASSWORD"])
                    setProperty("integration.matrikkel.system.jdbcUrl", env["DB_MATRIKKEL_JDBC_URL"])
                    setProperty("integration.matrikkel.system.user", env["DB_MATRIKKEL_SYSTEM_USERNAME"])
                    setProperty("integration.matrikkel.system.password", env["DB_MATRIKKEL_SYSTEM_PASSWORD"])
                },
            )
            .withFallback(ConfigFactory.defaultApplication())
}
