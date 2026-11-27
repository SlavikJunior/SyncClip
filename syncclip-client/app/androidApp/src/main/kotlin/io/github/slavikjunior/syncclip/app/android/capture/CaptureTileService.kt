package io.github.slavikjunior.syncclip.app.android.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Плитка в шторке: «Захватить буфер» (FR-3.2, п.3).
 *
 * Сама плитка буфер прочитать не может — Android 10+ отдаёт его только
 * приложению с фокусом. Поэтому плитка открывает прозрачную
 * [CaptureActivity], которая получает фокус, читает буфер и сразу закрывается.
 */
class CaptureTileService : TileService() {

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, CaptureActivity::class.java).apply {
            action = CaptureActivity.ACTION_CAPTURE_CLIPBOARD
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(io.github.slavikjunior.syncclip.app.android.R.string.tile_capture_label)
            updateTile()
        }
    }
}
