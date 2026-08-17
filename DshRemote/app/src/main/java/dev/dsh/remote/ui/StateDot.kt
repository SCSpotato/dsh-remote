package dev.dsh.remote.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.dsh.remote.ui.theme.DshAmber
import dev.dsh.remote.ui.theme.DshGreen
import dev.dsh.remote.ui.theme.DshRed

/**
 * DSH StateDot. "ongoing" is a hollow chasing ring (running); done/warning/error
 * are a solid center with a faint 10% halo; "idle" renders nothing (like DSH).
 */
@Composable
fun StateDot(state: String, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    if (state == "idle") return
    val color: Color = when (state) {
        "ongoing" -> Color(0xFF5686FE) // deepseek-450
        "done" -> DshGreen
        "warning" -> DshAmber
        "error" -> DshRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (state == "ongoing") {
        val transition = rememberInfiniteTransition(label = "state-dot-ongoing")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "state-dot-alpha",
        )
        Box(
            modifier
                .size(size)
                .border((size.value / 5f).coerceAtLeast(1f).dp, color.copy(alpha = alpha), CircleShape),
        )
    } else {
        Box(modifier.size(size)) {
            Box(Modifier.matchParentSize().background(color.copy(alpha = 0.10f), CircleShape))
            Box(
                Modifier
                    .size(size * 0.6f)
                    .align(Alignment.Center)
                    .background(color, CircleShape),
            )
        }
    }
}
