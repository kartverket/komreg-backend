package no.kartverket.komreg.matrikkelen

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.jdk9.asFlow
import no.kartverket.komreg.domain.Grunneiendom
import no.kartverket.komreg.domain.Matrikkelenhet
import no.kartverket.komreg.experimental.DownloadContext
import no.kartverket.komreg.experimental.EntitySource
import no.kartverket.komreg.experimental.Validation
import oracle.jdbc.OracleConnection
import oracle.jdbc.OraclePreparedStatement
import oracle.jdbc.OracleRow
import java.util.logging.Level
import java.util.logging.LogRecord

class MatrikkelenhetEntitySource(private val connection: OracleConnection) : EntitySource<Matrikkelenhet> {

    override fun download(context: DownloadContext): Flow<Validation<Matrikkelenhet>> = flow {
        connection
            .prepareStatement("SELECT m.id, m.kommuneid, m.gardsnr, m.bruksnr, m.festenr, m.seksjonsnr FROM matrikkelenhet m")
            .use { st ->
                emitAll(
                    st.unwrap(OraclePreparedStatement::class.java)
                        .executeQueryAsyncOracle()
                        .asFlow()
                        .flatMapConcat { it.publisherOracle(::matrikkelenhetFromRow).asFlow() }
                )
            }
    }

    companion object {
        private fun matrikkelenhetFromRow(row: OracleRow): Validation<Matrikkelenhet> {
            val kommuneid = try {
                Math.toIntExact(row.getOrThrow(2))
            } catch (e: ArithmeticException) {
                return Validation.invalid(LogRecord(Level.SEVERE, "Komreg støtter ikke kjempestore kommuneid-er"))
            }
            val gardsnr = row.getOrThrow<Int>(3)
            val bruksnr = row.getOrThrow<Int>(4)
            val festenr = row.getOrThrow<Int>(5).takeIf { it != 0 }
            val seksjonsr = row.getOrThrow<Int>(6).takeIf { it != 0 }
            return if (festenr == null && seksjonsr == null) {
                Validation.valid(Grunneiendom(kommuneid, gardsnr, bruksnr, emptySet(), emptySet()))
            } else {
                Validation.invalid(LogRecord(Level.WARNING, "Not implemented yet"))
            }
        }
    }
}

inline fun <reified T> OracleRow.get(col: Int): T? = this.getObject(col, T::class.java)

inline fun <reified T> OracleRow.getOrThrow(col: Int): T = this.getObject(col, T::class.java)
