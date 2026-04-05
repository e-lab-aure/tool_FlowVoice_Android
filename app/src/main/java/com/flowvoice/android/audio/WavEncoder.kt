package com.flowvoice.android.audio

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a standard 44-byte WAV header and prepends it to raw PCM data.
 * No external dependencies - pure Kotlin ByteBuffer manipulation.
 */
object WavEncoder {

    fun encode(
        pcm: ByteArray,
        sampleRate: Int = 16_000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val dataSize = pcm.size
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        return ByteArrayOutputStream(44 + dataSize).apply {
            writeAscii("RIFF")
            writeInt32LE(36 + dataSize)   // overall file size minus 8 bytes
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeInt32LE(16)              // PCM subchunk1 size
            writeInt16LE(1)               // audio format: PCM = 1
            writeInt16LE(channels)
            writeInt32LE(sampleRate)
            writeInt32LE(byteRate)
            writeInt16LE(blockAlign)
            writeInt16LE(bitsPerSample)
            writeAscii("data")
            writeInt32LE(dataSize)
            write(pcm)
        }.toByteArray()
    }

    private fun OutputStream.writeInt32LE(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun OutputStream.writeInt16LE(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    private fun OutputStream.writeAscii(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }
}
