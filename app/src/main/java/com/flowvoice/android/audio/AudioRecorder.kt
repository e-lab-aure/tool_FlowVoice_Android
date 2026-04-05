package com.flowvoice.android.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Captures PCM audio from the microphone and encodes it as WAV bytes.
 *
 * Usage (must run on a background thread / IO dispatcher):
 *   val recorder = AudioRecorder()
 *   recorder.start()       // blocking loop until stop() is called
 *   val wav = recorder.stop()
 */
class AudioRecorder {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val encoding = AudioFormat.ENCODING_PCM_16BIT

    @Volatile private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val pcmBuffer = mutableListOf<ByteArray>()

    /**
     * Starts recording. Blocks the calling thread until [stop] sets [isRecording] to false.
     * Must be called from Dispatchers.IO.
     */
    fun start() {
        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufSize = maxOf(minBufSize * 4, 8192)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            encoding,
            bufSize
        ).also { it.startRecording() }

        isRecording = true
        pcmBuffer.clear()

        val chunk = ByteArray(minBufSize)
        while (isRecording) {
            val read = audioRecord!!.read(chunk, 0, chunk.size)
            if (read > 0) {
                pcmBuffer.add(chunk.copyOf(read))
            }
        }
    }

    /**
     * Stops the recording loop, releases the [AudioRecord], and returns WAV-encoded bytes.
     * Thread-safe: may be called from any thread.
     */
    fun stop(): ByteArray {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val totalSize = pcmBuffer.sumOf { it.size }
        val pcmData = ByteArray(totalSize)
        var offset = 0
        for (chunk in pcmBuffer) {
            chunk.copyInto(pcmData, offset)
            offset += chunk.size
        }
        pcmBuffer.clear()

        return WavEncoder.encode(pcmData, sampleRate, channels = 1, bitsPerSample = 16)
    }

    val isActive: Boolean get() = isRecording
}
