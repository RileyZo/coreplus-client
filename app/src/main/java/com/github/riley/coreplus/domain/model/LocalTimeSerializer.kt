package com.github.riley.coreplus.domain.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * [LocalTime] 的 kotlinx.serialization 序列化器。
 *
 * kotlinx.serialization 不内置 `java.time` 类型的序列化器，而作息表需要以 JSON 持久化，
 * 因此提供本对象：以 ISO-8601 的 `"HH:mm"`（含秒时保留秒）字符串形式读写。
 */
object LocalTimeSerializer : KSerializer<LocalTime> {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.github.riley.coreplus.LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.format(formatter))
    }

    override fun deserialize(decoder: Decoder): LocalTime =
        LocalTime.parse(decoder.decodeString(), formatter)
}