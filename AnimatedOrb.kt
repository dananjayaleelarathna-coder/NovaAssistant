package com.nova.assistant.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.nova.assistant.ui.AssistantStatus

@Composable
fun AnimatedOrb(status: AssistantStatus, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulseDuration = when (status) {
        AssistantStatus.LISTENING -> 900
        AssistantStatus.THINKING -> 1400
        AssistantStatus.SPEAKING -> 600
        AssistantStatus.IDLE -> 2600
    }
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(pulseDuration), RepeatMode.Reverse),
        label = "scale"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier.size(220.dp)) {
        val radius = (size.minDimension / 2) * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.9f), secondary.copy(alpha = 0.4f), primary.copy(alpha = 0f)),
                center = center,
                radius = radius * 1.6f
            ),
            radius = radius * 1.6f,
            center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary, primary),
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}
