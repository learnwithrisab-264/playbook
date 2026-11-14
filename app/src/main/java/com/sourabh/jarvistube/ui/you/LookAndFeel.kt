package com.sourabh.jarvistube.ui.you

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sourabh.jarvistube.AppSettings
import com.sourabh.jarvistube.ui.theme.EpicTheme
import com.sourabh.jarvistube.ui.theme.rememberTouch
import kotlin.math.cos
import kotlin.math.sin

/**
 * The theme gallery: one card per place from the epics, painted in that theme's own colours,
 * with its meaning. Tapping one glides the whole app into it.
 */
@Composable
internal fun ThemeGallery() {
    val touch = rememberTouch()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(EpicTheme.entries, key = { it.key }) { t ->
            ThemeCard(t, selected = AppSettings.themeKey == t.key) {
                if (AppSettings.themeKey != t.key) touch()
                AppSettings.updateTheme(t.key)
            }
        }
    }
}

@Composable
private fun ThemeCard(t: EpicTheme, selected: Boolean, onClick: () -> Unit) {
    val lift by animateFloatAsState(if (selected) 1f else 0.94f, spring(dampingRatio = 0.6f), label = "lift")
    val ring by animateColorAsState(if (selected) t.primary else Color.Transparent, label = "ring")
    val ink = if (t.lightOnly) Color(0xFF14110C) else Color(0xFFF1EFEA)
    Column(
        Modifier.width(150.dp).scale(lift).clip(RoundedCard).border(2.dp, ring, RoundedCard)
            .background(Brush.verticalGradient(listOf(t.raised, t.night)))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Emblem(t)
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(t.primary),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = if (t.lightOnly) Color.White else t.night) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.title, color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(t.meaning, color = ink.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        // The theme's colours, as a little row of lamps.
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(t.primary, t.secondary, t.raised).forEach { c ->
                Box(Modifier.size(12.dp).clip(CircleShape).background(c).border(1.dp, ink.copy(alpha = 0.2f), CircleShape))
            }
        }
    }
}

private val RoundedCard = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)

/** Each theme's mark, drawn: flame, bow, waves, peacock eye, sun-gold, leaf, sun-over-city. */
@Composable
private fun Emblem(t: EpicTheme) {
    Canvas(Modifier.size(56.dp)) {
        val c = center
        val r = size.minDimension / 2f
        val p = t.primary
        val s = t.secondary
        drawCircle(Brush.radialGradient(listOf(p.copy(alpha = 0.35f), Color.Transparent), c, r), r, c)
        when (t) {
            EpicTheme.Agni -> { // a diya flame
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y - r * 0.75f)
                    quadraticTo(c.x + r * 0.45f, c.y, c.x, c.y + r * 0.45f)
                    quadraticTo(c.x - r * 0.45f, c.y, c.x, c.y - r * 0.75f)
                }
                drawPath(path, Brush.verticalGradient(listOf(s, p), c.y - r * 0.75f, c.y + r * 0.45f))
                drawOval(p.copy(alpha = 0.8f), Offset(c.x - r * 0.55f, c.y + r * 0.45f), androidx.compose.ui.geometry.Size(r * 1.1f, r * 0.3f))
            }
            EpicTheme.Kurukshetra -> { // Arjuna's bow and arrow
                drawArc(s, -60f, 120f, false, Offset(c.x - r * 0.9f, c.y - r * 0.8f), androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.6f), style = Stroke(r * 0.1f))
                drawLine(p, Offset(c.x - r * 0.6f, c.y), Offset(c.x + r * 0.8f, c.y), r * 0.08f)
                drawLine(p, Offset(c.x + r * 0.8f, c.y), Offset(c.x + r * 0.55f, c.y - r * 0.2f), r * 0.08f)
                drawLine(p, Offset(c.x + r * 0.8f, c.y), Offset(c.x + r * 0.55f, c.y + r * 0.2f), r * 0.08f)
            }
            EpicTheme.Dwarka -> { // waves of the sea
                for (i in 0..2) {
                    val y = c.y - r * 0.35f + i * r * 0.35f
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(c.x - r * 0.8f, y)
                        quadraticTo(c.x - r * 0.4f, y - r * 0.25f, c.x, y)
                        quadraticTo(c.x + r * 0.4f, y + r * 0.25f, c.x + r * 0.8f, y)
                    }
                    drawPath(path, if (i == 1) s else p, style = Stroke(r * 0.09f))
                }
            }
            EpicTheme.Vrindavan -> { // the eye of a peacock feather
                drawOval(s, Offset(c.x - r * 0.55f, c.y - r * 0.75f), androidx.compose.ui.geometry.Size(r * 1.1f, r * 1.5f))
                drawOval(p, Offset(c.x - r * 0.35f, c.y - r * 0.45f), androidx.compose.ui.geometry.Size(r * 0.7f, r * 0.95f))
                drawOval(Color(0xFF0A1020), Offset(c.x - r * 0.15f, c.y - r * 0.2f), androidx.compose.ui.geometry.Size(r * 0.3f, r * 0.42f))
            }
            EpicTheme.Lanka -> { // the golden sun-disc
                drawCircle(Brush.radialGradient(listOf(s, p), c, r * 0.5f), r * 0.5f, c)
                for (i in 0 until 12) {
                    val a = Math.toRadians(i * 30.0).toFloat()
                    drawLine(p, Offset(c.x + cos(a) * r * 0.6f, c.y + sin(a) * r * 0.6f), Offset(c.x + cos(a) * r * 0.85f, c.y + sin(a) * r * 0.85f), r * 0.07f)
                }
            }
            EpicTheme.Panchavati -> { // a leaf of the forest
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x - r * 0.7f, c.y + r * 0.6f)
                    quadraticTo(c.x - r * 0.6f, c.y - r * 0.7f, c.x + r * 0.7f, c.y - r * 0.6f)
                    quadraticTo(c.x + r * 0.6f, c.y + r * 0.7f, c.x - r * 0.7f, c.y + r * 0.6f)
                }
                drawPath(path, p)
                drawLine(s, Offset(c.x - r * 0.7f, c.y + r * 0.6f), Offset(c.x + r * 0.5f, c.y - r * 0.45f), r * 0.06f)
            }
            EpicTheme.Ayodhya -> { // the rising sun over the palace
                drawArc(p, 180f, 180f, true, Offset(c.x - r * 0.6f, c.y - r * 0.4f), androidx.compose.ui.geometry.Size(r * 1.2f, r * 1.2f))
                drawRect(s, Offset(c.x - r * 0.75f, c.y + r * 0.2f), androidx.compose.ui.geometry.Size(r * 1.5f, r * 0.14f))
                drawRect(s, Offset(c.x - r * 0.55f, c.y + r * 0.42f), androidx.compose.ui.geometry.Size(r * 1.1f, r * 0.12f))
            }
        }
    }
}
