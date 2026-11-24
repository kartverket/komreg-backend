package no.kartverket.komreg.repositories

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.Transformasjonsmotor
import no.kartverket.komreg.core.domain.Fylkesdata
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.jsonSerializer
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.ReformRegulering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer

class ReformReguleringRepoTest {
    @Test
    fun `lagrer, erstatter og kjører reform-regulering`() {
        PostgreSQLContainer("postgres:16").use { database ->
            database.withDatabaseName("komreg-db").withUsername("komreg-db").withPassword("komreg-db").start()
            flyway(database)

            val hikariConfig = HikariConfig().apply {
                jdbcUrl = database.jdbcUrl
                username = database.username
                password = database.password
                minimumIdle = 1
            }
            HikariDataSource(hikariConfig).use { dataSource ->
                val repo = ReformReguleringRepo(dataSource, jsonSerializer())
                val regler = runBlocking {
                    val fylke = identTypeOf1<Fylkesnummer>()
                    listOf(
                        ReformOp.RetireTo(fylke(Fylkesnummer(30)), fylke(Fylkesnummer(31))),
                        ReformOp.Derive(fylke(Fylkesnummer(31)), fylke(Fylkesnummer(30)), Fylkesdata("Østfold")),
                    )
                }
                val regulering = ReformRegulering("Fylkesreform", LocalDate(2024, 1, 1), regler)

                assertNull(repo.get("r1"))
                assertTrue(repo.upsert("r1", regulering))
                assertEquals(regulering, repo.get("r1"))

                val endret = regulering.copy(navn = "Endret")
                assertFalse(repo.upsert("r1", endret))
                assertEquals(endret, repo.get("r1"))

                val kjoringRepo = KjoringRepo(dataSource)
                kjoringRepo.settMottakerSkjema(Mottaker.DB_MATRIKKEL_MOTTAKER1)
                val kjoringId = kjoringRepo.insertAndRetrieveKjoringId("r1", Transformasjonsmotor.REFORM)!!

                val kjoring = kjoringRepo.getKjoring(kjoringId)!!
                assertNull(kjoring.regulering)
                assertEquals("r1", kjoring.reformRegulering)
                assertEquals(listOf(kjoringId), kjoringRepo.getStatusForKjoringMedReguleringsId("r1", Transformasjonsmotor.REFORM).map { it.id })
                assertEquals(emptyList<Kjoring>(), kjoringRepo.getStatusForKjoringMedReguleringsId("r1"))
            }
        }
    }
}
