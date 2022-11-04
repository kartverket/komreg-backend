package no.kartverket.komreg

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import no.kartverket.komreg.experimental.DownloadContext
import no.kartverket.komreg.experimental.EntitySourceContext
import no.kartverket.komreg.matrikkelen.MatrikkelConfig
import no.kartverket.komreg.matrikkelen.MatrikkelenEntitySourceFactory

data class CLIOptions(
    val output: String?,
    val threads: Int = 1,
    val entries: Int = 10,
    val debug: Boolean = false,
    val follow: Boolean = false,
)

suspend fun main(args: Array<String>) {
    val parser = ArgParser("komreg")
    val output by parser.option(ArgType.String, shortName = "o", description = "Output file")
    val entries by parser.option(ArgType.Int, shortName = "n", description = "Number of entries").default(10)
    val threads by parser.option(ArgType.Int, shortName = "t", description = "Number of concurrent threads").default(1)
    val debug by parser.option(ArgType.Boolean, shortName = "d", description = "Debug logging").default(false)
    val follow by parser.option(ArgType.Boolean, shortName = "f", description = "Trail log").default(false)

    parser.parse(args)
    val cli = CLIOptions(output, threads, entries, debug, follow)

    val factory = MatrikkelenEntitySourceFactory()
    val context: EntitySourceContext<MatrikkelConfig> = object : EntitySourceContext<MatrikkelConfig> {
        override fun getEntitySourceConfig(): MatrikkelConfig {
            return MatrikkelConfig(
                jdbcUrl = "jdbc:oracle:thin:@//localhost:1521/devmatr1",
                jdbcUser = "MATRIKKEL_DEV",
                jdbcPassword = "MATRIKKEL_DEV"
            )
        }
    }
    val source = factory.create(context)

    val result = source.download(DownloadContext(context)).take(cli.entries).onEach {
        println(it)
    }.collect()
}
