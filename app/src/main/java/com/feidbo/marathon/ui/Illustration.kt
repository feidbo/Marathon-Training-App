package com.feidbo.marathon.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.feidbo.marathon.ui.theme.Gold
import com.feidbo.marathon.ui.theme.Green
import com.feidbo.marathon.ui.theme.GreenLight

enum class ShapeKind { LINE, RECT, CIRCLE }
enum class ShapeStyle { FLOOR, FRAME, RAIL, PAD, WEIGHT, BACKREST }

/** A machine part. [coords] holds the start-pose values followed by the end-pose values. */
class Shape(val kind: ShapeKind, val coords: FloatArray, val width: Float, val style: ShapeStyle)

/** A limb as a polyline; [start] and [end] are flattened x,y pairs. Far-side limbs are drawn lighter. */
class Chain(val start: FloatArray, val end: FloatArray, val far: Boolean)

class Illustration(val shapes: List<Shape>, val head: FloatArray, val headRadius: Float, val chains: List<Chain>)

/**
 * Draws an exercise figure on a 100×100 grid, moving between the start and end positions of a rep
 * (or showing the start pose when [animate] is false).
 */
@Composable
fun ExerciseIllustration(id: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val ill = illustrations[id] ?: return
    val t = if (animate) {
        val transition = rememberInfiniteTransition(label = "rep")
        transition.animateFloat(
            initialValue = 0f, targetValue = 1f, label = "rep",
            animationSpec = infiniteRepeatable(tween(1400, delayMillis = 350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        ).value
    } else 0f
    val dark = isSystemInDarkTheme()
    val body = if (dark) GreenLight else Green
    val bodyFar = if (dark) Color(0xFF3D6E5C) else Color(0xFF9FD5BE)
    val frame = if (dark) Color(0xFF8A949C) else Color(0xFF5E6870)
    val rail = if (dark) Color(0xFF59636B) else Color(0xFFA7B0B8)
    val pad = if (dark) Color(0xFF4A5C70) else Color(0xFF2B3E52)
    val backrest = if (dark) Color(0xFF2E3A44) else Color(0xFFD5DEE6)
    val floor = if (dark) Color(0xFF3A403D) else Color(0xFFCFC8BF)

    Canvas(modifier.aspectRatio(1f)) {
        val s = size.minDimension / 100f
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        fun color(style: ShapeStyle) = when (style) {
            ShapeStyle.FLOOR -> floor
            ShapeStyle.FRAME -> frame
            ShapeStyle.RAIL -> rail
            ShapeStyle.PAD -> pad
            ShapeStyle.WEIGHT -> Gold
            ShapeStyle.BACKREST -> backrest
        }
        for (shape in ill.shapes) {
            val n = shape.coords.size / 2
            val v = FloatArray(n) { lerp(shape.coords[it], shape.coords[it + n]) * s }
            when (shape.kind) {
                ShapeKind.LINE -> drawLine(color(shape.style), Offset(v[0], v[1]), Offset(v[2], v[3]), shape.width * s, StrokeCap.Round)
                ShapeKind.RECT -> drawRoundRect(color(shape.style), Offset(v[0], v[1]), Size(v[2], v[3]), CornerRadius(2f * s))
                ShapeKind.CIRCLE -> drawCircle(color(shape.style), v[2], Offset(v[0], v[1]))
            }
        }
        val stroke = Stroke(width = 4.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (pass in listOf(true, false)) {
            for (chain in ill.chains.filter { it.far == pass }) {
                val path = Path()
                for (i in 0 until chain.start.size / 2) {
                    val x = lerp(chain.start[2 * i], chain.end[2 * i]) * s
                    val y = lerp(chain.start[2 * i + 1], chain.end[2 * i + 1]) * s
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, if (chain.far) bodyFar else body, style = stroke)
            }
        }
        drawCircle(body, ill.headRadius * s, Offset(lerp(ill.head[0], ill.head[2]) * s, lerp(ill.head[1], ill.head[3]) * s))
    }
}
