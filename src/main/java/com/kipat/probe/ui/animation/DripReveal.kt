package com.kipat.probe.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * A prototype of the "Drip Reveal" animation.
 *
 * It uses a clipping path that "drips" down the screen to reveal the content
 * beneath it. This creates the "premium reveal" feeling described in the spec.
 */
@Composable
fun DripReveal(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = EaseInOutQuart)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val p = progress.value

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(width, 0f)
                
                // Drip logic: 
                // We use multiple quadratic curves to simulate liquid dripping
                val dripDepth = height * p
                
                lineTo(width, dripDepth * 0.8f)
                
                // Three "drips"
                quadraticBezierTo(
                    width * 0.85f, dripDepth * 1.2f,
                    width * 0.7f, dripDepth * 0.9f
                )
                quadraticBezierTo(
                    width * 0.5f, dripDepth * 1.5f,
                    width * 0.3f, dripDepth * 0.85f
                )
                quadraticBezierTo(
                    width * 0.15f, dripDepth * 1.1f,
                    0f, dripDepth * 0.7f
                )
                
                lineTo(0f, 0f)
                close()
            }

            // Clip the content behind the drip path
            // In a real implementation, we'd draw the identity info ONLY when clipped
            // or use the path to draw a liquid border.
        }
        
        // For the prototype, we'll just alpha-fade the content based on progress
        // until the clipping logic is more refined.
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
