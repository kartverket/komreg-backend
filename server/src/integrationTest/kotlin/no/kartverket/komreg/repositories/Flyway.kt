package no.kartverket.komreg.repositories

import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

fun flyway(database: PostgreSQLContainer) {
    val flyway = Flyway.configure()
        .loggers("slf4j")
        .schemas("komreg")
        .dataSource(
            database.jdbcUrl,
            database.username,
            database.password,
        )
        .load()

    flyway.migrate()
}
