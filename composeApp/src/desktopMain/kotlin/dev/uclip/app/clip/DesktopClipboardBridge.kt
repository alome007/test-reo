package dev.uclip.app.clip

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.FlavorEvent
import java.awt.datatransfer.FlavorListener
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DesktopClipboardBridge : ClipboardBridge {
    private val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    private val _changes = MutableSharedFlow<ClipboardContent>(extraBufferCapacity = 8)
    override val changes: Flow<ClipboardContent> = _changes.asSharedFlow()

    private val listener = FlavorListener { _: FlavorEvent ->
        val content = read() ?: return@FlavorListener
        // Non-blocking emit into the shared flow.
        _changes.tryEmit(content)
    }

    override fun read(): ClipboardContent? = try {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            val s = clipboard.getData(DataFlavor.stringFlavor) as? String
            s?.let { ClipboardContent.Text(it) }
        } else null
    } catch (_: Throwable) {
        null
    }

    override fun write(content: ClipboardContent) {
        when (content) {
            is ClipboardContent.Text -> clipboard.setContents(StringSelection(content.text), null)
        }
    }

    override fun start() {
        clipboard.addFlavorListener(listener)
    }

    override fun stop() {
        clipboard.removeFlavorListener(listener)
    }
}
