package no.kartverket.komreg.repositories

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

class ConfigRepoTest {
    private inline fun withDatabase(block: (DataSource) -> Unit) {
        PostgreSQLContainer("postgres:16").use { database ->
            database.withDatabaseName("komreg-db")
                .withUsername("komreg-db")
                .withPassword("komreg-db")
                .start()
            flyway(database)

            val hikariConfig = HikariConfig()
            hikariConfig.poolName = "komreg-db-connection"
            hikariConfig.jdbcUrl = database.getJdbcUrl()
            hikariConfig.username = database.username
            hikariConfig.password = database.password
            hikariConfig.minimumIdle = 1

            HikariDataSource(hikariConfig).use { pool ->
                block(pool)
            }
        }
    }

    @Test
    fun testRepo() {
        withDatabase { datasource ->
            val repo = RunConfigRepo(datasource)

            repo.createConfigForKjoring(28, emptyList())
        }
    }
}
