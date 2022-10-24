package no.kartverket.komreg

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

data class CLIOptions(
    val input: String,
    val output: String?,
    val threads: Int = 1,
    val debug: Boolean = false,
    val follow: Boolean = false,
)

fun main(args: Array<String>) {
    val parser = ArgParser("komreg")
    val input by parser.option(ArgType.String, shortName = "i", description = "Input file").required()
    val output by parser.option(ArgType.String, shortName = "o", description = "Output file")
    val threads by parser.option(ArgType.Int, shortName = "t", description = "Number of concurrent threads").default(1)
    val debug by parser.option(ArgType.Boolean, shortName = "d", description = "Debug logging").default(false)
    val follow by parser.option(ArgType.Boolean, shortName = "f", description = "Trail log").default(false)

    parser.parse(args)
    val cli = CLIOptions(input, output, threads, debug, follow)
}
