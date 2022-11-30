package no.kartverket.komreg.transformation
import no.kartverket.komreg.domain.Grunneiendom
import no.kartverket.komreg.domain.Matrikkelenhet
import no.kartverket.komreg.experimental.*
import java.lang.System.Logger.Level.ERROR

sealed class Transform<T>(val entity: Entity<T>) {
    class Transformed<T>(data: Entity<T>, val transformation: TransformFunc<T>) : Transform<T>(data)
    class NoOp<T>(data: Entity<T>) : Transform<T>(data)
    //class Error<out T : EntityData>(data: T, val errors: List<String>) : Transform<T>(data)

    fun transform(trans: List<TransformFunc<T>>): Transform<T> = trans.fold(this) { acc, transformFunc ->
        acc.transform(transformFunc)
    }

    private fun transform(tf: TransformFunc<T>): Transform<T> = when (this) {
        is NoOp -> tf.map(this.entity)
        is Transformed -> {
            // TODO: Legg til higher kinded types i Kotlin ;-)
            val msgSupplier = { "Double t: ['${tf.description}', '${transformation.description}']" }
            val entity = when(entity) {
                is DatabaseEntity -> entity.copy(data = entity.data.log(ERROR, null,  msgSupplier))
                is VirtualEntity -> entity.copy(data = entity.data.log(ERROR, null,  msgSupplier))
            }
            Transformed(
                data = entity,
                transformation = this.transformation
            )
}
    }

    override fun toString(): String = "Transform(${this.entity}, " + when (this) {
        is NoOp -> "NoOp"
        is Transformed -> "Transformed: '${this.transformation.description}'@${Integer.toHexString(this.hashCode())}"
    } + ")"

    companion object {
        fun <T> noOp(t: Entity<T>): Transform<T> = NoOp(t)
    }
}

interface TransformFunc<T> {
    val description: String
    fun map(input: Entity<T>): Transform<T>
}

class AddGardsnummerRule<T : Matrikkelenhet>(
    private val range: IntRange,
    private val increase: Int,
) : TransformFunc<T> {
    override val description: String = "Add grunn $range +$increase"

    @Suppress("UNCHECKED_CAST")
    override fun map(input: Entity<T>): Transform<T> = input.fold({ Transform.NoOp(input) }) { data ->
        if (data is Grunneiendom && data.gardsnummer in range) {
            Transform.Transformed(input.transform { x ->
                (x as Grunneiendom).copy(gardsnummer = x.gardsnummer + increase) as T
            }, this)
        } else {
            Transform.NoOp(input)
        }
    }

}

fun main() {
    val rules = listOf(
        AddGardsnummerRule<Matrikkelenhet>(2..10, 50),
        AddGardsnummerRule(40..80, 5)
    )

    val grunneiendommer = listOf(
        Grunneiendom(1, 10, 10, emptySet(), emptySet()),
        Grunneiendom(1, 50, 10, emptySet(), emptySet()),
        Grunneiendom(1, 120, 10, emptySet(), emptySet()),
        Grunneiendom(1, 1020, 10, emptySet(), emptySet())
    ).map { SourceEntity<Matrikkelenhet>(GeneratedId(), Valid(it)) }
    println(grunneiendommer)

    grunneiendommer
        .map { Transform.noOp(it) }
        .map { it.transform(rules) }
        .onEach { println(it) }
        .filter { it !is Transform.NoOp }
        .map { it.entity }
}
