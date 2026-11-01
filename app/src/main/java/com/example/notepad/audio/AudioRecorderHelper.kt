package com.example.notepad.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.audio.AudioTranscoder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Records a single continuous 16kHz mono 16-bit PCM `.wav` file for Notepad voice notes.
 * No mid-recording patching or slicing is performed — the entire recording is captured intact
 * from start to stop and finalized as one lossless WAV file.
 */
class AudioRecorderHelper(private val context: Context) {

    data class RecordingResult(
        val file: File,
        val durationSec: Int,
        val hasSpeech: Boolean = true
    )

    companion object {
        private const val TAG = "AudioRecorderHelper"
        const val SAMPLE_RATE_HZ = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        const val FRAME_SAMPLES = 320 // 20ms @ 16kHz
        const val FRAME_BYTES = FRAME_SAMPLES * 2 // 640 bytes per 20ms frame

        // Adaptive voice activity detection constants (for silence-only detection, never cutting)
        private const val INITIAL_AMBIENT_RMS = 45.0
        private const val MIN_AMBIENT_RMS = 20.0
        private const val MAX_AMBIENT_RMS = 260.0
        private const val MIN_SPEECH_ONSET_RMS = 65.0
        private const val MIN_SPEECH_SUSTAIN_RMS = 42.0
        private const val SPEECH_HANGOVER_FRAMES = 6
    }

    /**
     * Lightweight, non-segmenting speech detector that tracks whether any human speech
     * occurred across the full recording without ever splitting or discarding audio frames.
     */
    internal class WholeClipSpeechDetector {
        var ambientRmsEstimate: Double = INITIAL_AMBIENT_RMS
            private set
        private var inSpeechState = false
        private var hangoverRemaining = 0
        private var speechFrames = 0

        var hasDetectedSpeech: Boolean = false
            private set

        fun onFrameRms(rms: Double) {
            val onsetThreshold = max(MIN_SPEECH_ONSET_RMS, ambientRmsEstimate * 2.5)
            val sustainThreshold = max(MIN_SPEECH_SUSTAIN_RMS, ambientRmsEstimate * 1.65)
            val isRawVoice = if (inSpeechState) rms >= sustainThreshold else rms >= onsetThreshold

            if (isRawVoice) {
                inSpeechState = true
                hangoverRemaining = SPEECH_HANGOVER_FRAMES
                speechFrames++
                if (speechFrames >= 3) {
                    hasDetectedSpeech = true
                }
            } else if (hangoverRemaining > 0) {
                hangoverRemaining--
                speechFrames++
                if (speechFrames >= 3) {
                    hasDetectedSpeech = true
                }
            } else {
                inSpeechState = false
                val clamped = rms.coerceIn(MIN_AMBIENT_RMS, MAX_AMBIENT_RMS)
                ambientRmsEstimate = (ambientRmsEstimate * 0.96 + clamped * 0.04)
                    .coerceIn(MIN_AMBIENT_RMS, MAX_AMBIENT_RMS)
            }
        }
    }

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var currentOutputFile: File? = null
    private var startTimeMillis: Long = 0L

    @Volatile
    private var detectedSpeech: Boolean = false

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        stopRecording()

        return try {
            val audioDir = File(context.filesDir, "audio_notes")
            if (!audioDir.exists()) audioDir.mkdirs()

            val file = File(audioDir, "rec_${System.currentTimeMillis()}.wav")
            currentOutputFile = file
            startTimeMillis = System.currentTimeMillis()
            detectedSpeech = false

            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufSize * 2).coerceAtLeast(SAMPLE_RATE_HZ)

            val record = createAudioRecord(bufferSize) ?: run {
                Log.e(TAG, "Could not initialize AudioRecord")
                currentOutputFile = null
                return null
            }

            audioRecord = record
            isRecording = true
            record.startRecording()

            recordingThread = thread(start = true, name = "NotepadAudioRecordThread") {
                val pcmBuffer = ShortArray(FRAME_SAMPLES)
                val byteChunk = ByteArray(FRAME_BYTES)
                var totalBytesWritten = 0
                val speechDetector = WholeClipSpeechDetector()

                var fos: FileOutputStream? = null
                try {
                    fos = FileOutputStream(file)
                    // Reserve 44-byte WAV header; finalized with exact PCM byte length on stop
                    fos.write(ByteArray(44))

                    while (isRecording) {
                        val readCount = record.read(pcmBuffer, 0, pcmBuffer.size)
                        if (readCount > 0) {
                            var byteIdx = 0
                            var sumSq = 0.0
                            for (i in 0 until readCount) {
                                val s = pcmBuffer[i].toInt()
                                sumSq += (s * s).toDouble()
                                byteChunk[byteIdx++] = (s and 0xFF).toByte()
                                byteChunk[byteIdx++] = ((s shr 8) and 0xFF).toByte()
                            }

                            fos.write(byteChunk, 0, byteIdx)
                            totalBytesWritten += byteIdx

                            val rms = sqrt(sumSq / readCount)
                            speechDetector.onFrameRms(rms)
                            detectedSpeech = speechDetector.hasDetectedSpeech
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error streaming PCM to WAV file", e)
                } finally {
                    detectedSpeech = speechDetector.hasDetectedSpeech
                    runCatching { fos?.flush() }
                    runCatching { fos?.close() }
                    finalizeWavHeader(file, totalBytesWritten)
                }
            }

            Log.d(TAG, "Started single-voice WAV recording to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cancelRecording()
            null
        }
    }

    fun stopRecording(): RecordingResult? {
        if (!isRecording) return null
        isRecording = false

        val record = audioRecord
        val threadToJoin = recordingThread
        audioRecord = null
        recordingThread = null

        try {
            record?.let {
                runCatching { it.stop() }
                runCatching { threadToJoin?.join(2500L) }
                runCatching { it.release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        val file = currentOutputFile ?: return null
        currentOutputFile = null

        val durationSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt().coerceAtLeast(1)
        return RecordingResult(
            file = file,
            durationSec = durationSec,
            hasSpeech = detectedSpeech
        )
    }

    fun cancelRecording() {
        isRecording = false
        val record = audioRecord
        audioRecord = null
        val threadToJoin = recordingThread
        recordingThread = null

        try {
            record?.let {
                runCatching { it.stop() }
                runCatching { threadToJoin?.join(200L) }
                runCatching { it.release() }
            }
        } catch (_: Exception) {}

        currentOutputFile?.delete()
        currentOutputFile = null
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        )
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(source, SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            runCatching { candidate.release() }
        }
        return null
    }

    private fun finalizeWavHeader(file: File, pcmBytesWritten: Int) {
        if (!file.exists() || pcmBytesWritten <= 0) return
        try {
            val header = AudioTranscoder.pcmToWav(
                pcm = ByteArray(0),
                sampleRate = SAMPLE_RATE_HZ,
                channels = 1,
                bitsPerSample = 16
            )
            val totalDataLen = pcmBytesWritten
            val totalFileSizeMinus8 = 36 + totalDataLen

            header[4] = (totalFileSizeMinus8 and 0xFF).toByte()
            header[5] = ((totalFileSizeMinus8 shr 8) and 0xFF).toByte()
            header[6] = ((totalFileSizeMinus8 shr 16) and 0xFF).toByte()
            header[7] = ((totalFileSizeMinus8 shr 24) and 0xFF).toByte()

            header[40] = (totalDataLen and 0xFF).toByte()
            header[41] = ((totalDataLen shr 8) and 0xFF).toByte()
            header[42] = ((totalDataLen shr 16) and 0xFF).toByte()
            header[43] = ((totalDataLen shr 24) and 0xFF).toByte()

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(0)
                raf.write(header, 0, 44)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize WAV header", e)
        }
    }
}
