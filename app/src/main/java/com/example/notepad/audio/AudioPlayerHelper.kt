package com.example.notepad.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerHelper(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId: StateFlow<String?> = _currentlyPlayingId.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(1000)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun playAudio(itemId: String, filePath: String, onFinished: () -> Unit = {}) {
        stopAudio()

        try {
            val file = File(filePath)
            val uri = if (file.exists()) Uri.fromFile(file) else Uri.parse(filePath)

            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener {
                    _currentlyPlayingId.value = null
                    _currentPositionMs.value = 0
                    progressJob?.cancel()
                    it.release()
                    mediaPlayer = null
                    onFinished()
                }
                setOnErrorListener { mp, _, _ ->
                    _currentlyPlayingId.value = null
                    _currentPositionMs.value = 0
                    progressJob?.cancel()
                    mp.release()
                    mediaPlayer = null
                    true
                }
                start()
            }
            mediaPlayer = player
            _currentlyPlayingId.value = itemId
            _durationMs.value = player.duration.coerceAtLeast(1000)

            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive && _currentlyPlayingId.value != null) {
                    try {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) {
                                _currentPositionMs.value = mp.currentPosition
                            }
                        }
                    } catch (_: Exception) {}
                    delay(50)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "Failed to play audio from $filePath", e)
            _currentlyPlayingId.value = null
            _currentPositionMs.value = 0
        }
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        } catch (_: Exception) {}
    }

    fun stopAudio() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {}
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _currentlyPlayingId.value = null
        _currentPositionMs.value = 0
    }

    fun isPlaying(itemId: String): Boolean {
        return _currentlyPlayingId.value == itemId
    }
}
