package no.kartverket.komreg

/**
 * Motoren som lager transformasjonene. Velges med miljøvariabelen [ENV] (`legacy` eller `reform`), med
 * [LEGACY] som standard.
 *
 * - [LEGACY]: `IdentTransformer` med regler fra `Regulering`-DTO-en (`/reguleringer`).
 * - [REFORM]: `ReformTransformer` med lagrede `ReformOp`-er (`/reform-reguleringer`).
 */
enum class Transformasjonsmotor(val verdi: String) {
    LEGACY("legacy"),
    REFORM("reform"),
    ;

    companion object {
        const val ENV = "TRANSFORMASJONSMOTOR"

        fun fra(verdi: String?): Transformasjonsmotor {
            if (verdi.isNullOrBlank()) return LEGACY
            return entries.firstOrNull { it.verdi == verdi.trim().lowercase() }
                ?: error("Ugyldig $ENV=$verdi. Gyldige verdier: ${entries.joinToString { it.verdi }}")
        }
    }
}
