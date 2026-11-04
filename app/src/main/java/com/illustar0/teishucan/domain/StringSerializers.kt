package com.illustar0.teishucan.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal object LocalDateAsStringSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("com.illustar0.teishucan.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = decoder.decodeParsed(LocalDate::parse)
}

internal object LocalTimeAsStringSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("com.illustar0.teishucan.LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = decoder.decodeParsed(LocalTime::parse)
}

/** 以文本序列化 [BigDecimal]，避免经过浮点数损失金额精度。 */
internal object BigDecimalAsStringSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("com.illustar0.teishucan.BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeParsed(::BigDecimal)
}

/** 格式不对时统一转为 [SerializationException]，调用方只需处理这一种解码失败。 */
private inline fun <T> Decoder.decodeParsed(parse: (String) -> T): T {
    val text = decodeString()
    return try {
        parse(text)
    } catch (cause: DateTimeParseException) {
        throw SerializationException("Malformed value: $text", cause)
    } catch (cause: NumberFormatException) {
        throw SerializationException("Malformed value: $text", cause)
    }
}
