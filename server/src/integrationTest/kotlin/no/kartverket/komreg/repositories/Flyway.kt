package no.kartverket.komreg.repositories

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

fun flyway(database: PostgreSQLContainer<*>) {
    val flyway = Flyway.configure()
        .schemas("komreg")
        .dataSource(
            database.getJdbcUrl(),
            database.username,
            database.password,
        )
        .load()

    flyway.migrate()
}
