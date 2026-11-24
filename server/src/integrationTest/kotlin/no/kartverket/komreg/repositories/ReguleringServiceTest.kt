package no.kartverket.komreg.repositories

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.kartverket.komreg.Transformasjonsmotor
import no.kartverket.komreg.core.domain.Fylkesnummer
import no.kartverket.komreg.exceptions.ReguleringAlreadyRunningException
import no.kartverket.komreg.integration.spi.identTypeOf1
import no.kartverket.komreg.integration.spi.invoke
import no.kartverket.komreg.jsonSerializer
import no.kartverket.komreg.services.LagreReformResultat
import no.kartverket.komreg.services.ReguleringService
import no.kartverket.komreg.transformation.reform.ReformOp
import no.kartverket.komreg.transformation.reform.ReformRegulering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ReguleringServiceTest {
    @Test
    fun `reform-regler kan ikke endres mens eller etter at en kjøring starter`() {
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
                val reformReguleringRepo = ReformReguleringRepo(dataSource, jsonSerializer())
                val kjoringRepo = KjoringRepo(dataSource)
                val service = ReguleringService(dataSource, ReguleringRepo(dataSource), kjoringRepo, reformReguleringRepo)
                kjoringRepo.settMottakerSkjema(Mottaker.DB_MATRIKKEL_MOTTAKER1)

                val regler = runBlocking {
                    val fylke = identTypeOf1<Fylkesnummer>()
                    listOf(ReformOp.RetireTo(fylke(Fylkesnummer(30)), fylke(Fylkesnummer(31))))
                }
                val regulering = ReformRegulering("Fylkesreform", LocalDate(2024, 1, 1), regler)
                val endret = regulering.copy(navn = "Endret")

                assertEquals(LagreReformResultat.Lagret(opprettet = true), service.lagreReformRegulering("r1", regulering))
                val kjoring = service.startKjoring("r1", Transformasjonsmotor.REFORM)
                assertFalse(kjoring.gjenopptatt)
                assertEquals("r1", kjoringRepo.getKjoring(kjoring.kjoringId)!!.reformRegulering)
                assertEquals(LagreReformResultat.IBruk, service.lagreReformRegulering("r1", endret))
                assertEquals(regulering, reformReguleringRepo.get("r1"))
                assertThrows(ReguleringAlreadyRunningException::class.java) {
                    service.startKjoring("r1", Transformasjonsmotor.REFORM)
                }

                // Gjenopptakelse kreves atomisk, så en ny forespørsel kan ikke gjenoppta den samme kjøringen.
                kjoringRepo.setStatusForKjøring(kjoring.kjoringId, Kjoringstatus.STOPPET)
                val gjenopptatt = service.startKjoring("r1", Transformasjonsmotor.REFORM)
                assertEquals(kjoring.kjoringId to true, gjenopptatt.kjoringId to gjenopptatt.gjenopptatt)
                assertEquals(Kjoringstatus.KJØRER, kjoringRepo.getKjoring(kjoring.kjoringId)!!.status)
                assertThrows(ReguleringAlreadyRunningException::class.java) {
                    service.startKjoring("r1", Transformasjonsmotor.REFORM)
                }

                // En kjøring som har låst reglene, men ikke committet kjøringen ennå, må blokkere lagring.
                assertEquals(LagreReformResultat.Lagret(opprettet = true), service.lagreReformRegulering("r2", regulering))
                val lagring = dataSource.iTransaksjon { connection ->
                    assertNotNull(reformReguleringRepo.getForUpdate(connection, "r2"))
                    val lagring = CompletableFuture.supplyAsync { service.lagreReformRegulering("r2", endret) }
                    Thread.sleep(500)
                    assertFalse(lagring.isDone)
                    assertNotNull(kjoringRepo.insertAndRetrieveKjoringId(connection, "r2", Transformasjonsmotor.REFORM))
                    lagring
                }
                assertEquals(LagreReformResultat.IBruk, lagring.get(10, TimeUnit.SECONDS))
                assertEquals(regulering, reformReguleringRepo.get("r2"))
            }
        }
    }
}
