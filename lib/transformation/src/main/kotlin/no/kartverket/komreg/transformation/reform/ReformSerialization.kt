package no.kartverket.komreg.transformation.reform

import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import no.kartverket.komreg.integration.spi.Ident
import no.kartverket.komreg.integration.spi.IdentSerializer

/** Samme format som [IdentSerializer], men avviser `Ident.Empty`. */
object NonEmptyIdentSerializer : KSerializer<Ident.NonEmpty> {
    private val delegate = IdentSerializer()

    override val descriptor: SerialDescriptor = SerialDescriptor("NonEmptyIdent", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: Ident.NonEmpty) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Ident.NonEmpty =
        delegate.deserialize(decoder) as? Ident.NonEmpty
            ?: throw SerializationException("Forventet en ikke-tom ident")
}

/** Reglene for en regulering, slik `server` lagrer og kjører dem. */
@Serializable
data class ReformRegulering(
    val navn: String,
    val ikrafttredelsesdato: LocalDate,
    val regler: List<ReformOp>,
)
