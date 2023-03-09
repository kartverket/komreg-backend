package no.kartverket.komreg.transformation

import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import no.kartverket.komreg.core.KrAppBootContext
import no.kartverket.komreg.core.domain.Kommune
import no.kartverket.komreg.core.domain.OldToNewKommune
import no.kartverket.komreg.integration.spi.WriteService

class TransformationKommune {
    val bootContext = object : KrAppBootContext {
        override val config by lazy {
            ConfigFactory.load("reference-dev.conf")
        }
    }

    suspend fun getAllKommuner(): List<Kommune> {
        val entitySources = EntitySourceManager(bootContext)
        val rawKommuner = entitySources.buildKommuneFlow().toList()
        val kommuner = entitySources.makeKommuneListFromRawDataKommuneList(rawKommuner)

        return kommuner
    }

    suspend fun writeKommuneNummer(oldNumber: Long, newNumber: Long) {
        val kommuneWrite: WriteService<OldToNewKommune> = WriteServiceManager(bootContext).getKommuneWriteService()

        kommuneWrite.write(listOf(OldToNewKommune(oldNumber, newNumber)).asFlow())
    }
}
