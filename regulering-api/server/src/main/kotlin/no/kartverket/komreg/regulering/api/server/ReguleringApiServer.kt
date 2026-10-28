package no.kartverket.komreg.regulering.api.server

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.util.ContextInitializer
import dev.dirs.ProjectDirectories
import dev.hayden.KHealth
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.loadCommonConfiguration
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import no.kartverket.komreg.regulering.api.server.domain.MunicipalityNumber
import no.kartverket.komreg.regulering.api.server.persistence.ReformRepository
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import java.io.File
import java.net.URI
import java.time.Duration
import io.ktor.serialization.kotlinx.json.json as kotlinxJson

lateinit var logger: Logger

fun main(args: Array<String>) {
    val cl = Thread.currentThread().contextClassLoader ?: ::main.javaClass.classLoader

    // Sett logback konfigurasjonsfil hvis den ikke er satt som system property
    // Dumme avhengiheter som inkluderer logback.xml i classpathen som gir
    // warnings, som trigger en stygg liste med meldinger fra logback
    val logbackContainingJars = if (System.getProperty("logback.configurationFile") == null) {
        System.setProperty(
            "logback.configurationFile",
            "regulering-api-logback.xml"
        )
        cl.getResources("logback.xml").toList()
    } else {
        null
    }

    // Sett opp konfigurasjon for Ktor serveren, med default application.conf
    // og secrets.conf
    val modifiedArgs = arrayListOf<String>()
    val configDir = File(ProjectDirectories.from("no", "kartverket.komreg", "regulering-api").configDir)
    val secretsFile = File(configDir, "secrets.conf")
    val secretsFileFound = secretsFile.isFile
            && secretsFile.canRead()
            && modifiedArgs.add("-config=$secretsFile")
    if (args.none { it.startsWith("-config=") }) {
        check(cl.getResource("application.conf") != null || File("application.conf").isFile) {
            "application.conf not found"
        }
        modifiedArgs.add("-config=application.conf")
    }
    val commandLineConfig = CommandLineConfig(modifiedArgs.toTypedArray())
    val deploymentConfig = commandLineConfig.rootConfig.environment.config

    // Sett logging format
    if (System.getProperty("APP_LOG_ENCODE").isNullOrEmpty()) {
        deploymentConfig.propertyOrNull("app.log.encode")?.getString()?.let { System.setProperty("APP_LOG_ENCODE", it) }
    }

    // Initialiser SLF4J:
    // - for å unngå https://www.slf4j.org/codes.html#replay
    // - kunne sette APP_ENV i application.conf
    when (val ctx = LoggerFactory.getILoggerFactory()) {
        is LoggerContext -> {
            ctx.reset()
            ContextInitializer(ctx).autoConfig()
        }
    }

    // Redirect java.util.logging (JUL) to SLF4J
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()

    // Log uncaught via logger, ikke stderr
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        LoggerFactory.getLogger("UncaughtExceptionHandler").error(
            "Uncaught exception in thread ${thread.name}",
            throwable
        )
    }

    logger = LoggerFactory.getLogger(::main.javaClass.enclosingClass)

    // Dumme avhengigheter som inkluderer logback.xml i classpathen som gir
    // warnings, som trigger en stygg liste med meldinger fra logback
    // Logg workaround, for å eventuelt fjerne hvis det blir fikset
    if (logbackContainingJars != null) {
        if (logbackContainingJars.isEmpty()) {
            logger.warn("Ingen avhengigheter med logback.xml funnet i classpathen: Work around kan fjernes")
        } else {
            logger.info(
                "Logback konfigurasjonsfiler som ikke burde være der funnet på classpathen: ${
                    logbackContainingJars.joinToString(
                        "\n    - ",
                        "\n    - "
                    )
                }"
            )
        }
    }

    if (!secretsFileFound) {
        logger.info("Secrets file not found(secrets can still be provided via environment variables): $secretsFile")
    } else {
        logger.info("Secrets file found: $secretsFile")
    }

    // JOOQ spammer loggen med tips og logo
    System.setProperty("org.jooq.no-logo", "true")
    System.setProperty("org.jooq.no-tips", "true")

    val server = embeddedServer(
        factory = Netty,
        configure = {
            takeFrom(commandLineConfig.engineConfig)
            loadCommonConfiguration(deploymentConfig)
        },
        module = rootModuleFactory(deploymentConfig)
    )

    server.start(wait = true)
}

internal fun rootModuleFactory(
    deploymentConfig: ApplicationConfig
): (Application.() -> Unit) {


    return {
        val openTelemetrySdk = openTelemetrySdk(deploymentConfig)
        install(KtorServerTelemetry) {
            setOpenTelemetry(openTelemetrySdk)
        }

        val appJson = Json {
            serializersModule = SerializersModule {
                // TODO: Støtt test-kommuner med lange fylkesnummer
                contextual(MunicipalityNumber::class, MunicipalityNumber.DefaultSerializer)
            }
        }
        install(ContentNegotiation) {
            kotlinxJson(appJson)
        }

        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
        }

        val dsl = databaseDsl(deploymentConfig)

        healthModule(dsl)

        routes(deploymentConfig, appJson, ReformRepository(dsl))
    }

}

private fun Application.openTelemetrySdk(envConfig: ApplicationConfig): OpenTelemetrySdk {
    val openTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
        .addPropertiesCustomizer { oldProps ->
            val props: MutableMap<String, String?> = hashMapOf()

            fun setProp(parameter: String) {
                if (oldProps.getString(parameter) == null) {
                    props[parameter] = envConfig.property("app.$parameter").getString()
                } else {
                    props[parameter] = oldProps.getString(parameter)
                }
            }

            val endpointKey = "otel.exporter.otlp.endpoint"
            if (oldProps.getString(endpointKey) == null) {
                val endpoint = envConfig.propertyOrNull("app.$endpointKey")?.getString()
                if (endpoint != null) {
                    props[endpointKey] = endpoint
                }
            } else {
                props[endpointKey] = oldProps.getString(endpointKey)
            }

            setProp("otel.logs.exporter")
            setProp("otel.metrics.exporter")
            setProp("otel.traces.exporter")
            setProp("otel.exporter.otlp.service.name")
            setProp("otel.exporter.otlp.protocol")
            setProp("otel.exporter.otlp.insecure")
            setProp("otel.propagators")

            val showConfig = props
                .toSortedMap()
                .map { "${it.key.padEnd(31)} : ${it.value}" }
                .joinToString("\n    - ", "\n    - ")
            logger.info("Configuring OpenTelemetry$showConfig")

            props
        }
        .addResourceCustomizer { oldResource, _ ->
            oldResource.toBuilder()
                .putAll(oldResource.attributes)
                .put(
                    ServiceAttributes.SERVICE_NAME,
                    envConfig.property("app.otel.exporter.otlp.service.name").getString()
                )
                .build()
        }
        .build()
        .openTelemetrySdk


    val runtimeTelemetry = try {
        RuntimeTelemetry.builder(openTelemetrySdk).build()
    } catch (err: Throwable) {
        try {
            openTelemetrySdk.close()
        } catch (suppressed: Throwable) {
            err.addSuppressed(suppressed)
        }
        throw err
    }
    monitor.subscribe(ApplicationStopped) {
        logger.info("Shutting down OpenTelemetry RuntimeTelemetry")
        try {
            runtimeTelemetry.close()
        } catch (err: Throwable) {
            logger.error("Error shutting down OpenTelemetry RuntimeTelemetry", err)
        }
        logger.info("Shutting down OpenTelemetry SDK")
        try {
            openTelemetrySdk.close()
        } catch (err: Throwable) {
            logger.error("Error shutting down OpenTelemetry SDK", err)
        }
    }

    return openTelemetrySdk
}

private fun Application.databaseDsl(deploymentConfig: ApplicationConfig): DSLContext {
    val dbUser = deploymentConfig.property("app.db.user").getString()
    val dbPassword = deploymentConfig.property("app.db.password").getString()
    val dbSchema = deploymentConfig.property("app.db.schema").getString()
    val dbUriSsp = URI.create(deploymentConfig.property("app.db.url").getString()).schemeSpecificPart
    val r2dbcUri = URI("r2dbc", dbUriSsp, null)
    val jdbcUri = URI("jdbc", dbUriSsp, null)

    val flyway = Flyway.configure()
        .schemas(dbSchema)
        .dataSource(
            jdbcUri.toString(),
            dbUser,
            dbPassword,
        )
        .load()

    flyway.migrate()

    val factory = ConnectionFactories.get(
        ConnectionFactoryOptions
            .parse(r2dbcUri.toString())
            .mutate()
            .option(ConnectionFactoryOptions.USER, dbUser)
            .option(ConnectionFactoryOptions.PASSWORD, dbPassword)
            .build()
    )
    val maxSize = 10
    val initialSize = 2
    val maxAcquireTime = Duration.ofSeconds(2)
    val pool = ConnectionPool(
        ConnectionPoolConfiguration
            .builder(factory)
            .maxSize(maxSize)
            .initialSize(initialSize)
            .maxAcquireTime(maxAcquireTime)
            .build()
    )
    logger.info(
        """Database connection pool created
    |    - maxSize        : $maxSize
    |    - initialSize    : $initialSize
    |    - maxAcquireTime : ${maxAcquireTime.toMillis()}ms
    """.trimMargin()
    )

    monitor.subscribe(ApplicationStopped) {
        logger.info("Disposing database connection pool")
        try {
        pool.dispose()
        } catch (err: Throwable) {
            logger.error("Error disposing database connection pool", err)
        }
    }

    return DSL.using(pool, SQLDialect.POSTGRES, Settings())
}

private fun Application.healthModule(dsl: DSLContext) {
    install(KHealth) {
        healthCheckPath = "/livez"
        readyCheckPath = "/readyz"
        readyChecks {
            check("database") {
                dsl.selectOne().awaitSingle().component1() == 1
            }
        }
    }
}
