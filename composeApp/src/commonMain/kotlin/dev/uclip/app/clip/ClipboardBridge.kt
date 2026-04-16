package dev.uclip.app.clip

import kotlinx.coroutines.flow.Flow

sealed interface ClipboardContent {
    data class Text(val text: String) : ClipboardContent
    // Future: Html(html, plainFallback), Image(bytes, mime), File(path, mime)
}

/**
 * Thin abstraction over the OS clipboard. Implementations emit on [changes]
 * when the user copies something, independent of whether we wrote to the
 * clipboard ourselves — dedupe of our own writes lives in ClipSyncController.
 */
interface ClipboardBridge {
    val changes: Flow<ClipboardContent>

    fun read(): ClipboardContent?
    fun write(content: ClipboardContent)

    fun start()
    fun stop()
}
