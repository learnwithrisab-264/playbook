package com.sourabh.jarvistube.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.ui.study.PlayerPopups
import com.sourabh.jarvistube.ui.theme.OnVideo
import com.sourabh.jarvistube.ui.theme.rememberTouch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * YouTube's "Lock screen", made safer: while on, the video ignores every touch (no accidental
 * seeks or pauses in a pocket or a child's hands). A tap shows "Hold to unlock"; holding the pill
 * for under a second fills a ring and unlocks. Back also just shows the pill.
 */
@Composable
internal fun TouchLockOverlay(modifier: Modifier = Modifier) {
    if (!PlayerPopups.touchLock) return
    var hintAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hint by remember { mutableStateOf(true) }
    val ring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val touch = rememberTouch()
    LaunchedEffect(hintAt) { hint = true; delay(2800); if (ring.value == 0f) hint = false }
    BackHandler { hintAt = System.currentTimeMillis() }
    Box(
        modifier.fillMaxSize()
            // Swallow every touch before the video's own gestures see it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        e.changes.forEach { it.consume() }
                        if (e.changes.any { !it.pressed }) hintAt = System.currentTimeMillis()
                    }
                }
            }
    ) {
        AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)) {
            Row(
                Modifier.clip(CircleShape).background(OnVideo.scrimStrong)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val job = scope.launch {
                                ring.animateTo(1f, tween(800, easing = LinearEasing))
                                touch()
                                PlayerPopups.touchLock = false
                            }
                            // Lifting early cancels and the ring drains back.
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                            if (PlayerPopups.touchLock) {
                                job.cancel()
                                scope.launch { ring.animateTo(0f, tween(200)) }
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val accent = MaterialTheme.colorScheme.primary
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(22.dp)) {
                        drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, style = Stroke(2.dp.toPx()))
                        drawArc(accent, -90f, 360f * ring.value, false, style = Stroke(2.5.dp.toPx()))
                    }
                    Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Text("Hold to unlock", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
