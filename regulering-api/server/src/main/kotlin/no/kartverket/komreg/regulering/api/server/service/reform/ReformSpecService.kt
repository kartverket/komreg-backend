package no.kartverket.komreg.regulering.api.server.service.reform

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.KommuneService
import no.kartverket.komreg.regulering.reform.format.FormatErr
import no.kartverket.komreg.regulering.reform.format.JsonPath
import no.kartverket.komreg.regulering.reform.format.ReformSpec
import no.kartverket.komreg.regulering.reform.format.Resolved
import no.kartverket.komreg.regulering.reform.format.SegmentRegistry
import no.kartverket.komreg.regulering.reform.format.resolve
import no.kartverket.komreg.regulering.reform.rules.DataErr
import no.kartverket.komreg.regulering.reform.rules.DomainErr
import no.kartverket.komreg.regulering.reform.rules.validateData
import no.kartverket.komreg.regulering.reform.rules.validateDomain
import no.kartverket.komreg.routes.Regulering
import no.kartverket.komreg.routes.Umappbar
import no.kartverket.komreg.routes.toReguleringDTO
import no.kartverket.komreg.transformation.reform.ReformClaims
import no.kartverket.komreg.transformation.reform.ReformErr
import no.kartverket.komreg.transformation.reform.ReformOp

/**
 * Kjeden fra [ReformSpec] til [Regulering]: `resolve` → `ReformClaims.validate` → `validateDomain` →
 * `validateData` → `toReguleringDTO`. Kjeden stopper etter første steg som feiler, og hvert steg rapporterer
 * alle sine feil med JSON-stier. Ingenting lagres, og alt avledes på nytt ved hvert kall, siden dataene fra
 * [kommuneService] kan endre seg.
 *
 * @param dataKommuneService brukes av datareglene, som ikke trenger kommunevåpen.
 */
class ReformSpecService(
    private val registry: SegmentRegistry,
    private val kommuneService: KommuneService,
    private val dataKommuneService: KommuneService = kommuneService,
) {
    /** Alle feil fra det første steget som feiler, eller en tom liste hvis specen er gyldig. */
    suspend fun validate(spec: ReformSpec): List<ReformFeil> =
        derive(VALIDATION_ID, spec).fold({ it.toList() }, { emptyList() })

    /** Den avledede [Regulering] med [id] som DTO-id, eller feilene fra det første steget som feiler. */
    suspend fun regulering(id: Int, spec: ReformSpec): Either<NonEmptyList<ReformFeil>, Regulering> =
        derive(id.toString(), spec)

    private suspend fun derive(id: String, spec: ReformSpec): Either<NonEmptyList<ReformFeil>, Regulering> = either {
        val resolved = spec.resolve(registry).mapLeft { errs -> errs.map { it.toFeil() } }.bind()
        val claims = ReformClaims.validate(resolved.ops).mapLeft { errs -> errs.map { it.toFeil(resolved) } }.bind()
        claims.validateDomain(registry).mapLeft { errs -> errs.map { it.toFeil(resolved) } }.bind()
        claims.validateData(dataKommuneService).mapLeft { errs -> errs.map { it.toFeil(resolved) } }.bind()
        claims.toReguleringDTO(id, spec.navn, spec.ikrafttredelse, kommuneService)
            .mapLeft { errs -> errs.map { it.toFeil(resolved) } }
            .bind()
    }

    private companion object {
        const val VALIDATION_ID = "validering"
    }
}

/** Leser en spec fra JSON. Formfeil (ukjent `op`, manglende felt og lignende) blir en [ReformSteg.FORM]-feil. */
fun Json.parseReformSpec(text: String): Either<ReformFeil, ReformSpec> =
    try {
        decodeFromString(ReformSpec.serializer(), text).right()
    } catch (e: SerializationException) {
        e.toFormFeil().left()
    } catch (e: IllegalArgumentException) {
        e.toFormFeil().left()
    }

private val pathInMessage = Regex("""path: (\$\S*)""")

private fun Exception.toFormFeil(): ReformFeil {
    val message = message ?: "Ugyldig JSON"
    return ReformFeil(
        sti = pathInMessage.find(message)?.groupValues?.get(1) ?: JsonPath.Root.value,
        steg = ReformSteg.FORM,
        kode = "InvalidShape",
        melding = message.lineSequence().first().take(300),
    )
}

private fun FormatErr.toFeil() = ReformFeil(
    sti = path.value,
    steg = ReformSteg.FORMAT,
    kode = code,
    melding = message,
    flereSti = if (this is FormatErr.KeptAndConsumed) listOf(sourcePath.value) else emptyList(),
)

private fun ReformErr.toFeil(resolved: Resolved) = resolved.feil(
    steg = ReformSteg.KONFLIKT,
    kode = this::class.simpleName!!,
    melding = message,
    hint = null,
    ops = when (this) {
        is ReformErr.Diverges -> ops
        is ReformErr.Conflict -> ops
        is ReformErr.SourceIsTarget -> sourceOps + targetOps
        is ReformErr.DeriveFromTarget -> listOf(derive) + targetOps
        is ReformErr.DeriveTypeMismatch -> listOf(derive)
        is ReformErr.TargetUnderSource -> listOf(targetOp) + sourceOps
        is ReformErr.SourceUnderCreatedTarget -> listOf(sourceOp, createOp)
    },
)

private fun DomainErr.toFeil(resolved: Resolved) =
    resolved.feil(ReformSteg.DOMENE, rule, message, hint, ops)

private fun DataErr.toFeil(resolved: Resolved) =
    resolved.feil(ReformSteg.DATA, rule, message, hint, ops)

private fun Umappbar.toFeil(resolved: Resolved) = resolved.feil(
    steg = ReformSteg.BRO,
    kode = this::class.simpleName!!,
    melding = melding,
    hint = null,
    ops = when (this) {
        is Umappbar.IngenDtoEkvivalent -> ops
        is Umappbar.FinnesIkke -> resolved.ops.filter { nøkkel in it.keys() }
        is Umappbar.UgyldigPayload -> resolved.ops.filter { mål in it.keys() }
        is Umappbar.UlikTransformasjon -> (mangler + ekstra).toList()
    },
)

private fun Resolved.feil(steg: String, kode: String, melding: String, hint: String?, ops: Collection<ReformOp>): ReformFeil {
    val paths = ops.flatMap { origins[it].orEmpty() }.distinct().map { it.value }
    return ReformFeil(
        sti = paths.firstOrNull() ?: JsonPath.Root.value,
        steg = steg,
        kode = kode,
        melding = melding,
        hint = hint,
        flereSti = paths.drop(1),
    )
}

private fun ReformOp.keys(): List<Ident.NonEmpty> = when (this) {
    is ReformOp.Create -> listOf(target)
    is ReformOp.Derive -> listOf(target, from)
    is ReformOp.Move -> listOf(source, target)
    is ReformOp.Retire -> listOfNotNull(source, successor)
    is ReformOp.RetireTo -> listOf(source, target)
}
