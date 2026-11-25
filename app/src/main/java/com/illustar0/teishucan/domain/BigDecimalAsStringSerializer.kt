package com.illustar0.teishucan.domain

import java.math.BigDecimal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** 以文本序列化 [BigDecimal]，避免经过浮点数损失金额精度。 */
internal object BigDecimalAsStringSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("com.illustar0.teishucan.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal {
        val text = decoder.decodeString()
        return text.toBigDecimalOrNull() ?: throw SerializationException("Malformed decimal: $text")
    }
}
