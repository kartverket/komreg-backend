package no.kartverket.komreg.repositories

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import no.kartverket.komreg.KjoringContextImpl
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.core.domain.Id
import no.kartverket.komreg.core.domain.Kommunenummer
import no.kartverket.komreg.integration.spi.Entity
import no.kartverket.komreg.integration.spi.Transformation
import no.kartverket.komreg.integration.spi.identTypeOf2
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.jsonSerializer
import no.kartverket.komreg.routes.Regulering
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

class TransformationRepoTest {
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

    private fun initKjoring(dataSource: DataSource): Int {
        val reguleringRepo = ReguleringRepo(dataSource)
        val ok = reguleringRepo.insertRegulering(
            Regulering(
                "abc",
                "def",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                emptyList(),
            ),
        )
        Assertions.assertTrue(ok, "Lagring av regulering")


        val kjoringRepo = KjoringRepo(dataSource)
        kjoringRepo.settMottakerSkjema(Mottaker.DB_MATRIKKEL_MOTTAKER1)
        return kjoringRepo.insertAndRetrieveKjoringId("abc")!!
    }

    /**
     * Tester bare at lagringen ikke krasjer
     */
    @Test
    fun writeAndRead() {
        withDatabase { dataSource ->
            val kjoringId = initKjoring(dataSource)

            val repo = TransformationRepo(
                dataSource,
                jsonSerializer(),
            )

            val kommuneIdentType = runBlocking {
                identTypeOf2<Fylkesnummer, Kommunenummer.Lopenummer>()
            }

            val transformation = Transformation(
                id = Id(TestIdType.Foo, 1L),
                sourceEntity = Entity(
                    id = Id(TestIdType.Foo, 1L),
                    ident = kommuneIdentType(Fylkesnummer(98), Kommunenummer.Lopenummer(98)),
                    associatedIdents = null,
                    sourceObject = TestPayload(
                        true,
                        "Hallo",
                        setOf(7L, 13L),
                    ),
                ),
                transformedIdent = kommuneIdentType(Fylkesnummer(99), Kommunenummer.Lopenummer(99)),
                transformedAssociatedIdents = null,
                resultObject = null,
            )

            repo.writeTransformationsToDatabase(
                kjoringId,
                listOf(
                    transformation,
                ),
            )

            val tFlow = repo.readTransformationFromDatabase(kjoringId)
            val readTransformations = runBlocking { tFlow.toList() }
            Assertions.assertEquals(1, readTransformations.size, "Number of transformations read")

            val readTransformation = readTransformations.single()
            Assertions.assertEquals(transformation, readTransformation, "Read transformation")
        }
    }

    @Test
    fun testIdCache() {
        withDatabase { dataSource ->
            val kjoringContext = KjoringContextImpl(0, dataSource)
            runBlocking {
                val firstId = kjoringContext.idGenerators.idFor(TestIdType.Foo,  null)
                val secondId = kjoringContext.idGenerators.idFor(TestIdType.Foo,  null)
                Assertions.assertNotEquals(firstId, secondId, "Expected different ids")
            }

            runBlocking {
                val firstId = kjoringContext.idGenerators.idFor(TestIdType.Foo,  "foo")
                val secondId = kjoringContext.idGenerators.idFor(TestIdType.Foo,  "foo")
                val thirdId = kjoringContext.idGenerators.idFor(TestIdType.Foo,  "bar")

                Assertions.assertEquals(firstId, secondId, "Expected same ids")
                Assertions.assertNotEquals(secondId, thirdId, "Expected different ids")
            }
        }
    }

    @Test
    fun testBatchingIdCache() {
        withDatabase { dataSource ->
            val kjoringContext = KjoringContextImpl(0, dataSource)
            val hints = listOf("foo", "bar", null, null, "baz", null)
            val firstIds = runBlocking {
                kjoringContext.idGenerators.idsFor(TestIdType.Foo, hints)
            }
            val secondIds = runBlocking {
                kjoringContext.idGenerators.idsFor(TestIdType.Foo, hints)
            }


            Assertions.assertEquals(firstIds.map { it.first }, hints, "Expected same hints")
            Assertions.assertEquals(
                firstIds.filter { (hint, _) -> hint != null },
                secondIds.filter { (hint, _) -> hint != null },
                "Expected same ids")


            Assertions.assertEquals(secondIds.map { it.first }, hints, "Expected same hints")
            Assertions.assertNotEquals(
                firstIds.filterIndexed { n, _ -> hints[n] == null },
                secondIds.filterIndexed {n, _ -> hints[n] == null },
                "Expected different ids")
        }
    }
}
