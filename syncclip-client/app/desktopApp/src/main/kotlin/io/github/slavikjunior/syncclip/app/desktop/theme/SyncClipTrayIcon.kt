package io.github.slavikjunior.syncclip.app.desktop.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * Иконка трея рисуется кодом: отдельный растровый ресурс не нужен, а цвета
 * совпадают с темой Aqua.
 */
internal object SyncClipTrayIcon : Painter() {

    override val intrinsicSize: Size = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        val background = Color(0xFF1A6A8C)
        drawRoundRect(
            color = background,
            size = size,
            cornerRadius = CornerRadius(size.minDimension * 0.22f),
        )

        val boardWidth = size.width * 0.48f
        val boardHeight = size.height * 0.6f
        val boardTopLeft = Offset(
            x = (size.width - boardWidth) / 2f,
            y = size.height * 0.22f,
        )
        drawRoundRect(
            color = Color.White,
            topLeft = boardTopLeft,
            size = Size(boardWidth, boardHeight),
            cornerRadius = CornerRadius(size.minDimension * 0.08f),
        )

        val lineWidth = boardWidth * 0.62f
        val lineHeight = size.minDimension * 0.06f
        repeat(3) { index ->
            drawRoundRect(
                color = background,
                topLeft = Offset(
                    x = boardTopLeft.x + boardWidth * 0.19f,
                    y = boardTopLeft.y + boardHeight * (0.24f + index * 0.22f),
                ),
                size = Size(lineWidth, lineHeight),
                cornerRadius = CornerRadius(lineHeight / 2f),
            )
        }
    }
}
