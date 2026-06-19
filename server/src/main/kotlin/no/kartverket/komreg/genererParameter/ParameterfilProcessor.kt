package regulering.parameterfil

import no.kartverket.komreg.repositories.ReguleringRepo
import no.kartverket.komreg.repositories.KjoringRepo
import no.kartverket.komreg.routes.EndringDTO
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.routes.TransformasjonDTO
import regulering.model.SheetType

object ParameterfilProcessor {

    fun lesOgTransformer(resourcePath: String, separator: String = ";"): List<TransformasjonDTO> {
        val sheet = lesSheet(resourcePath, separator)
        return when (sheet.type) {
            SheetType.VEG -> lesVegRader(sheet).flatMap { byggVegTransformasjoner(it) }
            SheetType.KRETS -> lesKretsRader(sheet).map { byggKretsTransformasjon(it) }
            SheetType.TEIG -> lesTeigRader(sheet).map { byggTeigTransformasjon(it) }
            SheetType.MATRIKKEL -> lesMatrikkelRader(sheet).map { byggMatrikkelTransformasjon(it) }
        }
    }

    fun lesOgTransformerMappe(resourceFolder: String, separator: String = ";"): List<TransformasjonDTO> {
        val filenames = finnCSVFilnavn(resourceFolder)
        if (filenames.isEmpty()) error("Fant ingen CSV-filer i: $resourceFolder")
        return filenames.flatMap { lesOgTransformer("$resourceFolder/$it", separator) }
    }

    fun genererParameterfil(
        reguleringRepo: ReguleringRepo,
        kjoringRepo: KjoringRepo,
        inputMappe: String,
        separator: String = ";",
    ) {
        val metadata = lesMetadata(inputMappe)
        val transformasjoner = lesOgTransformerMappe(inputMappe, separator)

        val endring = EndringDTO(
            id = metadata.id,
            navn = metadata.navn,
            type = "kommune",
            utgåendeFylker = emptyList(),
            utgåendeKommuner = emptyList(),
            nyeFylker = emptyList(),
            nyeKommuner = emptyList(),
            transformasjoner = transformasjoner,
        )

        val regulering = Regulering(
            id = metadata.id,
            navn = metadata.navn,
            dato = metadata.dato,
            endringer = listOf(endring),
        )

        // Thrower om det er en kjøring for reguleringen,
        // ellers updater/inserter vi regulering på nytt
        kjoringRepo.getStatusForKjoringMedReguleringsId(metadata.id)
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                error("Det finnes allerede en kjøring for regulering med id: ${metadata.id}. status: ${it}")
            }
        reguleringRepo.getReguleringById(metadata.id)?.let {
            reguleringRepo.updateRegulering(regulering)
        }
        reguleringRepo.insertRegulering(regulering)
    }
}