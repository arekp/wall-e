package pl.walle.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nagrywa mikrofon (PCM16 LE, mono, 16 kHz) i oddaje ramki po 20 ms (640 B).
 * Format zgodny z docs/protocol.md.
 */
class AudioStreamer(private val onChunk: (ByteArray) -> Unit) {
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Wymaga przyznanego uprawnienia RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        val chunkBytes = SAMPLE_RATE / 1000 * CHUNK_MS * 2
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // z eliminacją echa
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, chunkBytes * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                recorder.startRecording()
                val buffer = ByteArray(chunkBytes)
                while (isActive) {
                    var filled = 0
                    while (filled < chunkBytes) {
                        val n = recorder.read(buffer, filled, chunkBytes - filled)
                        if (n < 0) return@launch
                        filled += n
                    }
                    onChunk(buffer.copyOf())
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_MS = 20
    }
}
