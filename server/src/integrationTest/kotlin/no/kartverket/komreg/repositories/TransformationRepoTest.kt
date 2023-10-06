package no.kartverket.komreg.repositories

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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

class TransformationRepoTest {
    private inline fun withDatabase(block: (PostgreSQLContainer<*>) -> Unit) {
        PostgreSQLContainer("postgres:16").use { database ->
            database.withDatabaseName("komreg-db")
                .withUsername("komreg-db")
                .withPassword("komreg-db")
                .start()
            flyway(database)
            block(database)
        }
    }

    private fun initKjoring(database: PostgreSQLContainer<*>): Int {
        val reguleringRepo = ReguleringRepo(
            database.getJdbcUrl(),
            database.username,
            database.password
        )
        val ok = reguleringRepo.insertRegulering(
            Regulering(
                "abc",
                "def",
                Clock.System.todayIn(TimeZone.currentSystemDefault()),
                emptyList()
            )
        )
        Assertions.assertTrue(ok, "Lagring av regulering")

        val kjoringRepo = KjoringRepo(
            database.getJdbcUrl(),
            database.username,
            database.password
        )
        return kjoringRepo.insertAndRetrieveKjoringId("abc")!!
    }

    /**
     * Tester bare at lagringen ikke krasjer
     */
    @Test
    fun write() {
        withDatabase { database ->
            val kjoringId = initKjoring(database)

            val repo = TransformationRepo(
                database.getJdbcUrl(),
                database.username,
                database.password,
                jsonSerializer()
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
                        setOf(7L, 13L)
                    ),
                ),
                transformedIdent = kommuneIdentType(Fylkesnummer(99), Kommunenummer.Lopenummer(99)),
                transformedAssociatedIdents = null,
                resultObject = null,
            )

            repo.writeTransformationsToDatabase(
                kjoringId,
                listOf(
                    transformation
                )
            )
        }
    }
}
