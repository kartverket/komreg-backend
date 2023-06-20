package no.kartverket.komreg.core.util.kotlin

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

/**
 * Finn det komplette settet av typer som en type arver fra, inkludert seg selv,
 * der alle typeparametere er erstattet med de typene de er bundet til i `type`.
 *
 * F.eks. vil `typeClosure(typeOf<List<String>>())`</code> være
 * `[List<String>, Collection<String>, Iterable<String>, Any]`
 *
 * @type type som skal finne alle supertyper for
 * @return alle konkrete supertyper for type, inkludert typen selv
 */
fun typeClosure(type: KType): Set<KType> = typeClosureImpl(type, emptyMap())

/**
 * Implementasjon av [typeClosure].
 *
 * @param type type som skal finne alle supertyper for
 * @param env map av typeparametere som vi har argumenter for
 */
private fun typeClosureImpl(
    type: KType,
    env: Map<KTypeParameter, KTypeProjection>
): Set<KType> = when (val classifier = type.classifier) {
    is KClass<*> -> {
        val updatedArgs =
            replaceTypeProjections(type.arguments, env)
        val updatedEnv =
            env + classifier
                .typeParameters
                .mapIndexed { n, param ->
                    param to updatedArgs[n]
                }
        setOf(classifier.createType(updatedArgs)) + classifier
            .supertypes
            .flatMap {
                typeClosureImpl(
                    it,
                    updatedEnv
                )
            }
    }

    else -> setOf(type)
}

/**
 * Erstatter alle typeparametere i `args` med det typeparameteren er mappet til
 * `env`.
 *
 * Implementasjonsdetalj: Vi må gjøre det rekurivt fordi argumentet kan
 * være f.eks. `Set<T>`, og ikke bare `T` direkte.
 *
 */
private fun replaceTypeProjections(
    args: List<KTypeProjection>,
    env: Map<KTypeParameter, KTypeProjection>
): List<KTypeProjection> {
    return args.map { projection ->
        val type: KType? = projection.type
        if (type != null) {
            when (val classifier = type.classifier) {
                is KClass<*> ->
                    projection.copy(
                        type = classifier.createType(
                            replaceTypeProjections(type.arguments, env),
                            type.isMarkedNullable,
                            type.annotations
                        )
                    )

                is KTypeParameter ->
                    env.getOrDefault(classifier, projection)

                else ->
                    // Antar at dette ikke er mulig, det betyr at vi har et
                    // typeargument som ikke er en klasse/parameterisert klasse
                    // eller typeparameter.
                    // (f.eks. en intersection type)
                    throw IllegalArgumentException(
                        "Unknown classifier for type projection $projection: $classifier"
                    )
            }
        } else {
            // projection er en stjerneprojeksjon, siden projection.type == null,
            // og stjerneprojeksjoner kan ikke ha typeparametere, så vi kan bare
            // returnere den uendret
            projection
        }
    }
}