package no.kartverket.komreg.core.impl

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.scan
import no.kartverket.komreg.core.KrAppBootContext

suspend fun main() {
    val bootContext = object : KrAppBootContext {
        val fallback = ConfigFactory.empty()
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }
    val enititySources = EntitySourceManager(bootContext)

    val z = enititySources
        .buildEntityFlow()
        .scan(1) { accumulator, value ->
            println(value)
            if (accumulator and 10240 == 0) {
                println("Lastet ned $accumulator")
            }
            accumulator + 1
        }.lastOrNull()

    println("Fant masse gøy: $z")
}