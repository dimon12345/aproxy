package com.aproxy.app.proxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** One point of the rolling traffic chart: bytes transferred during one second. */
data class TrafficPoint(
    val upload: Long,
    val download: Long,
)

data class TrafficState(
    val totalUpload: Long = 0L,
    val totalDownload: Long = 0L,
    val uploadSpeed: Long = 0L,
    val downloadSpeed: Long = 0L,
    val history: List<TrafficPoint> = emptyList(),
)

/**
 * Thread-safe byte counters. [addUpload]/[addDownload] are called from many
 * socket-copying coroutines, while [sample] is called once per second.
 */
class TrafficMeter {

    private val uploadBytes = AtomicLong(0L)
    private val downloadBytes = AtomicLong(0L)
    private var lastUploadBytes = 0L
    private var lastDownloadBytes = 0L

    private val _state = MutableStateFlow(TrafficState())
    val state: StateFlow<TrafficState> = _state.asStateFlow()

    fun addUpload(bytes: Long) {
        if (bytes > 0) uploadBytes.addAndGet(bytes)
    }

    fun addDownload(bytes: Long) {
        if (bytes > 0) downloadBytes.addAndGet(bytes)
    }

    /** Takes a one-second sample and appends it to the chart history. */
    fun sample() {
        val upload = uploadBytes.get()
        val download = downloadBytes.get()
        val upSpeed = (upload - lastUploadBytes).coerceAtLeast(0L)
        val downSpeed = (download - lastDownloadBytes).coerceAtLeast(0L)
        lastUploadBytes = upload
        lastDownloadBytes = download

        val previous = _state.value
        _state.value = previous.copy(
            totalUpload = upload,
            totalDownload = download,
            uploadSpeed = upSpeed,
            downloadSpeed = downSpeed,
            history = (previous.history + TrafficPoint(upSpeed, downSpeed))
                .takeLast(HISTORY_SIZE),
        )
    }

    fun idle() {
        _state.value = _state.value.copy(uploadSpeed = 0L, downloadSpeed = 0L)
    }

    fun reset() {
        uploadBytes.set(0L)
        downloadBytes.set(0L)
        lastUploadBytes = 0L
        lastDownloadBytes = 0L
        _state.value = TrafficState()
    }

    companion object {
        const val HISTORY_SIZE = 60
    }
}
