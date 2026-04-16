package dev.uclip.app.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ClipSyncTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Push clipboard"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // TODO(phase 5): foreground the app long enough to read clipboard and push.
    }
}
