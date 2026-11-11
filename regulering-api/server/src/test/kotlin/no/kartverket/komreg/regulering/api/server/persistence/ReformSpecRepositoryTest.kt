package no.kartverket.komreg.regulering.api.server.persistence

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import no.kartverket.komreg.regulering.reform.format.ReformSpecFixtures
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.Test

class ReformSpecRepositoryTest {
    private val database = PostgreSQLContainer("postgres:14.4-alpine").also { it.start() }
    private val json = Json

    private val repository: ReformSpecRepository = run {
        Flyway.configure()
            .dataSource(database.jdbcUrl, database.username, database.password)
            .load()
            .migrate()
        val factory = ConnectionFactories.get(
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, database.host)
                .option(ConnectionFactoryOptions.PORT, database.firstMappedPort)
                .option(ConnectionFactoryOptions.USER, database.username)
                .option(ConnectionFactoryOptions.PASSWORD, database.password)
                .option(ConnectionFactoryOptions.DATABASE, database.databaseName)
                .build(),
        )
        JooqReformSpecRepository(DSL.using(factory, SQLDialect.POSTGRES), json)
    }

    @AfterTest
    fun stop() = database.stop()

    private fun spec(name: String = "Flytting av veg og teig") =
        json.decodeFromString(ReformSpec.serializer(), ReformSpecFixtures.valid("move")).copy(navn = name)

    @Test
    fun `lagrer, henter, oppdaterer, lister og sletter`() = runBlocking {
        val id = repository.insert(spec())
        assertThat(repository.find(id)).isEqualTo(spec())

        assertThat(repository.update(id, spec("Nytt navn"))).isTrue()
        assertThat(repository.find(id)).isEqualTo(spec("Nytt navn"))
        assertThat(repository.list()).containsExactly(ReformSammendrag(id, "Nytt navn", LocalDate(2027, 1, 1)))

        assertThat(repository.delete(id)).isTrue()
        assertThat(repository.find(id)).isNull()
        assertThat(repository.delete(id)).isFalse()
        assertThat(repository.update(id, spec())).isFalse()
    }
}
