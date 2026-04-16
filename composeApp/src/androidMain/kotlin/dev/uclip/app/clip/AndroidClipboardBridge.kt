package dev.uclip.app.clip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidClipboardBridge(context: Context) : ClipboardBridge {
    private val appContext = context.applicationContext
    private val clipboard =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _changes = MutableSharedFlow<ClipboardContent>(extraBufferCapacity = 8)
    override val changes: Flow<ClipboardContent> = _changes.asSharedFlow()

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val content = read() ?: return@OnPrimaryClipChangedListener
        _changes.tryEmit(content)
    }

    override fun read(): ClipboardContent? {
        // Android 10+ only returns clip data while the app is foreground or
        // a focused input method. The foreground service + tile (phase 5
        // follow-up) keeps this reliable; for now we simply return null when
        // the OS denies the read.
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(appContext)?.toString() ?: return null
        return ClipboardContent.Text(text)
    }

    override fun write(content: ClipboardContent) {
        when (content) {
            is ClipboardContent.Text -> clipboard.setPrimaryClip(
                ClipData.newPlainText("universal-clipboard", content.text)
            )
        }
    }

    override fun start() {
        clipboard.addPrimaryClipChangedListener(listener)
    }

    override fun stop() {
        clipboard.removePrimaryClipChangedListener(listener)
    }
}
