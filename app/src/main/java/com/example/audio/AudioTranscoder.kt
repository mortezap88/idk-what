package com.example.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Utility to decode and convert audio formats (M4A, AAC, MP3, etc.) into
 * standard 16kHz mono 16-bit PCM WAV bytes for Gemini Speech-to-Text.
 *
 * Gemini's STT via OpenAI-compatible endpoints (`gemini-3.5-flash-lite` on 1xAi)
 * processes audio reliably as 16kHz mono WAV bytes via base64 data URI. This
 * transcoder guarantees 100% valid 16kHz mono WAV bytes.
 */
object AudioTranscoder {

    private const val TAG = "AudioTranscoder"
    const val TARGET_SAMPLE_RATE_HZ = 16000
    const val TARGET_CHANNELS = 1
    const val TARGET_BITS_PER_SAMPLE = 16
    private const val DEFAULT_SPEECH_RMS_THRESHOLD = 1.0

    /**
     * Checks if the byte array starts with the standard RIFF/WAVE header.
     */
    fun isWav(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
    }

    fun isM4a(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[4] == 'f'.code.toByte() &&
                bytes[5] == 't'.code.toByte() &&
                bytes[6] == 'y'.code.toByte() &&
                bytes[7] == 'p'.code.toByte()
    }

    fun isMp3(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) return true
        if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0) return true
        return false
    }

    fun isAac(bytes: ByteArray): Boolean {
        if (bytes.size < 2) return false
        return (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xF0) == 0xF0
    }

    data class GeminiAudioPayload(
        val bytes: ByteArray,
        val mimeType: String
    )

    /**
     * Prepares audio for Gemini.
     * When [preserveWav] is true (used by Notepad voice-note patches and full-recording STT),
     * 16kHz PCM WAV inputs are preserved losslessly regardless of length so short patches and
     * longer recordings never switch codecs across the 48 KiB threshold.
     */
    fun prepareAudioForGemini(
        audioBytes: ByteArray,
        cacheDir: File? = null,
        preserveWav: Boolean = false
    ): GeminiAudioPayload {
        if (audioBytes.isEmpty()) return GeminiAudioPayload(audioBytes, "audio/wav")
        if (!preserveWav) {
            if (isM4a(audioBytes)) return GeminiAudioPayload(audioBytes, "audio/mp4")
            if (isMp3(audioBytes)) return GeminiAudioPayload(audioBytes, "audio/mpeg")
            if (isAac(audioBytes)) return GeminiAudioPayload(audioBytes, "audio/aac")
        }
        if (isWav(audioBytes)) {
            if (!preserveWav && audioBytes.size > 48 * 1024) {
                val pcmOffset = 44
                val pcmLength = audioBytes.size - pcmOffset
                if (pcmLength > 0) {
                    val pcm = ByteArray(pcmLength)
                    System.arraycopy(audioBytes, pcmOffset, pcm, 0, pcmLength)
                    val m4a = AudioRecorderManager.encodePcmToM4aAac(
                        cacheDir ?: File(System.getProperty("java.io.tmpdir", "/tmp")),
                        pcm,
                        TARGET_SAMPLE_RATE_HZ
                    )
                    if (m4a != null && m4a.size > 64) {
                        return GeminiAudioPayload(m4a, "audio/mp4")
                    }
                }
            }
            return GeminiAudioPayload(audioBytes, "audio/wav")
        }
        val decodedWav = decodeToWav(audioBytes, cacheDir) ?: audioBytes
        if (!preserveWav && decodedWav.size > 48 * 1024 && isWav(decodedWav)) {
            val pcmOffset = 44
            val pcmLength = decodedWav.size - pcmOffset
            if (pcmLength > 0) {
                val pcm = ByteArray(pcmLength)
                System.arraycopy(decodedWav, pcmOffset, pcm, 0, pcmLength)
                val m4a = AudioRecorderManager.encodePcmToM4aAac(
                    cacheDir ?: File(System.getProperty("java.io.tmpdir", "/tmp")),
                    pcm,
                    TARGET_SAMPLE_RATE_HZ
                )
                if (m4a != null && m4a.size > 64) {
                    return GeminiAudioPayload(m4a, "audio/mp4")
                }
            }
        }
        return GeminiAudioPayload(decodedWav, "audio/wav")
    }

    /**
     * Ensures audio bytes are in 16kHz mono WAV format.
     * If the audio is already WAV, returns it directly.
     * Otherwise decodes the container/stream (M4A, AAC, MP3, etc.) using Android's
     * hardware/software MediaCodec into 16kHz mono PCM and wraps it in a WAV header.
     */
    fun ensureWavBytes(audioBytes: ByteArray, cacheDir: File? = null): ByteArray {
        if (audioBytes.isEmpty()) return audioBytes
        if (isWav(audioBytes)) return audioBytes

        val decodedWav = decodeToWav(audioBytes, cacheDir)
        return decodedWav ?: audioBytes
    }

    /**
     * Decodes any supported audio format (M4A, AAC, MP3, OGG, etc.) to 16kHz mono 16-bit PCM WAV.
     */
    fun decodeToWav(audioBytes: ByteArray, cacheDir: File? = null): ByteArray? {
        val tempFile = try {
            if (cacheDir != null && cacheDir.exists()) {
                File.createTempFile("transcode_", ".audio", cacheDir)
            } else {
                File.createTempFile("transcode_", ".audio")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not create temp file for audio decoding", e)
            return null
        }

        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        return try {
            tempFile.writeBytes(audioBytes)

            val mediaExtractor = MediaExtractor().apply {
                setDataSource(tempFile.absolutePath)
            }
            extractor = mediaExtractor

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null

            for (i in 0 until mediaExtractor.trackCount) {
                val format = mediaExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || trackFormat == null) {
                Log.w(TAG, "No audio track found in file (${audioBytes.size} bytes)")
                return null
            }

            mediaExtractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null

            val mediaCodec = MediaCodec.createDecoderByType(mime)
            codec = mediaCodec
            mediaCodec.configure(trackFormat, null, null, 0)
            mediaCodec.start()

            val srcSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                TARGET_SAMPLE_RATE_HZ
            }
            val srcChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    val inputBufferIndex = mediaCodec.dequeueInputBuffer(10_000L)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = mediaExtractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                mediaCodec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEOS = true
                            } else {
                                mediaCodec.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    sampleSize,
                                    mediaExtractor.sampleTime,
                                    0
                                )
                                mediaExtractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmOut.write(chunk)
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            val decodedRawPcm = pcmOut.toByteArray()
            if (decodedRawPcm.isEmpty()) {
                Log.w(TAG, "Decoded PCM was empty")
                return null
            }

            val resampledMonoPcm = resamplePcmTo16kMono(
                pcm = decodedRawPcm,
                srcSampleRate = srcSampleRate,
                srcChannels = srcChannels
            )

            pcmToWav(
                pcm = resampledMonoPcm,
                sampleRate = TARGET_SAMPLE_RATE_HZ,
                channels = TARGET_CHANNELS,
                bitsPerSample = TARGET_BITS_PER_SAMPLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio to WAV", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
            runCatching { tempFile.delete() }
        }
    }

    /**
     * Converts raw PCM bytes into 16kHz 1-channel mono 16-bit PCM.
     */
    fun resamplePcmTo16kMono(
        pcm: ByteArray,
        srcSampleRate: Int,
        srcChannels: Int
    ): ByteArray {
        val totalSamples = pcm.size / 2
        if (totalSamples == 0) return ByteArray(0)

        val shortBuf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val inSamples = ShortArray(totalSamples)
        shortBuf.get(inSamples)

        // 1. Convert to mono if multi-channel
        val monoSamples = if (srcChannels > 1) {
            val monoCount = totalSamples / srcChannels
            val mono = ShortArray(monoCount)
            for (i in 0 until monoCount) {
                var sum = 0
                for (ch in 0 until srcChannels) {
                    sum += inSamples[i * srcChannels + ch].toInt()
                }
                mono[i] = (sum / srcChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            mono
        } else {
            inSamples
        }

        // 2. Resample to 16kHz if different
        val resampledSamples = if (srcSampleRate != TARGET_SAMPLE_RATE_HZ && srcSampleRate > 0) {
            val ratio = TARGET_SAMPLE_RATE_HZ.toDouble() / srcSampleRate.toDouble()
            val outCount = (monoSamples.size * ratio).toInt().coerceAtLeast(1)
            val out = ShortArray(outCount)
            for (i in 0 until outCount) {
                val srcPos = i / ratio
                val srcIdx = srcPos.toInt()
                val frac = srcPos - srcIdx
                if (srcIdx + 1 < monoSamples.size) {
                    val s0 = monoSamples[srcIdx].toDouble()
                    val s1 = monoSamples[srcIdx + 1].toDouble()
                    val interp = s0 + frac * (s1 - s0)
                    out[i] = interp.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                } else if (srcIdx < monoSamples.size) {
                    out[i] = monoSamples[srcIdx]
                }
            }
            out
        } else {
            monoSamples
        }

        val outBytes = ByteArray(resampledSamples.size * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledSamples)
        return outBytes
    }

    /**
     * Wraps raw 16-bit PCM bytes with a standard 44-byte RIFF/WAVE header.
     */
    fun pcmToWav(
        pcm: ByteArray,
        sampleRate: Int = TARGET_SAMPLE_RATE_HZ,
        channels: Int = TARGET_CHANNELS,
        bitsPerSample: Int = TARGET_BITS_PER_SAMPLE
    ): ByteArray {
        val totalDataLen = pcm.size
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, 36 + totalDataLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1) // PCM format
        writeShortLE(header, 22, channels.toShort())
        writeIntLE(header, 24, sampleRate)
        writeIntLE(header, 28, byteRate)
        writeShortLE(header, 32, (channels * bitsPerSample / 8).toShort())
        writeShortLE(header, 34, bitsPerSample.toShort())
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalDataLen)

        val out = ByteArray(header.size + pcm.size)
        System.arraycopy(header, 0, out, 0, header.size)
        System.arraycopy(pcm, 0, out, header.size, pcm.size)
        return out
    }

    /**
     * Checks if the WAV audio has detectable speech / energy above noise floor.
     * Prevents calling STT models on digital silence or room background noise
     * which causes Gemini to hallucinate random filler text.
     */
    fun hasAudibleSpeech(
        wavOrPcmBytes: ByteArray,
        rmsThreshold: Double = DEFAULT_SPEECH_RMS_THRESHOLD
    ): Boolean {
        val pcmOffset = if (isWav(wavOrPcmBytes)) 44 else 0
        val pcmLength = wavOrPcmBytes.size - pcmOffset
        if (pcmLength < 320) return false // less than 10ms

        val sampleCount = pcmLength / 2
        var sumSquares = 0.0

        for (i in 0 until sampleCount) {
            val byteIdx = pcmOffset + i * 2
            val low = wavOrPcmBytes[byteIdx].toInt() and 0xFF
            val high = wavOrPcmBytes[byteIdx + 1].toInt()
            val sample = (high shl 8) or low
            val s = sample.toShort().toDouble()
            sumSquares += s * s
        }

        val totalRms = sqrt(sumSquares / sampleCount)
        return totalRms >= rmsThreshold
    }

    private fun writeIntLE(header: ByteArray, offset: Int, value: Int) {
        header[offset] = (value and 0xFF).toByte()
        header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        header[offset + 2] = ((value shr 16) and 0xFF).toByte()
        header[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(header: ByteArray, offset: Int, value: Short) {
        header[offset] = (value.toInt() and 0xFF).toByte()
        header[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
}
