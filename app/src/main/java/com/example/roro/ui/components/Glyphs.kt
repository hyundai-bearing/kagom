package com.example.roro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.roro.core.LandmarkGlyph
import com.example.roro.core.QuestIcon
import com.example.roro.ui.theme.KagomColor
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * Figma 벡터 글리프를 Canvas로 옮긴 것. 실 에셋이 생기면 같은 자리에서 교체한다 (가이드 8.3).
 */

/** 자물쇠 (잠긴 셀·핀·패널) */
@Composable
fun LockGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val bodyTop = h * 0.45f
    drawArc(
        color, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.27f, h * 0.12f), size = Size(w * 0.46f, h * 0.56f),
        style = Stroke(width = w * 0.12f),
    )
    drawLine(color, Offset(w * 0.27f, h * 0.40f), Offset(w * 0.27f, bodyTop + 1f), strokeWidth = w * 0.12f)
    drawLine(color, Offset(w * 0.73f, h * 0.40f), Offset(w * 0.73f, bodyTop + 1f), strokeWidth = w * 0.12f)
    drawRoundRect(color, Offset(w * 0.16f, bodyTop), Size(w * 0.68f, h * 0.45f), CornerRadius(w * 0.1f))
}

/** 체크 표시 */
@Composable
fun CheckGlyph(modifier: Modifier, color: Color, stroke: Dp = 3.dp) = Canvas(modifier) {
    val p = Path().apply {
        moveTo(size.width * 0.2f, size.height * 0.52f)
        lineTo(size.width * 0.42f, size.height * 0.72f)
        lineTo(size.width * 0.8f, size.height * 0.3f)
    }
    drawPath(p, color, style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** 닫기 (×) */
@Composable
fun CloseGlyph(modifier: Modifier, color: Color, stroke: Dp = 2.5.dp) = Canvas(modifier) {
    val a = size.width * 0.2f; val b = size.width * 0.8f
    drawLine(color, Offset(a, a), Offset(b, b), strokeWidth = stroke.toPx(), cap = StrokeCap.Round)
    drawLine(color, Offset(b, a), Offset(a, b), strokeWidth = stroke.toPx(), cap = StrokeCap.Round)
}

/** 지도 북쪽 화살표 */
@Composable
fun NorthArrowGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val p = Path().apply {
        moveTo(size.width / 2, 0f)
        lineTo(size.width, size.height)
        lineTo(size.width / 2, size.height * 0.72f)
        lineTo(0f, size.height)
        close()
    }
    drawPath(p, color)
}

/** 랜드마크 일러스트 (배·해돋이·커피잔). 실 일러스트 교체 지점 */
@Composable
fun LandmarkGlyphView(glyph: LandmarkGlyph, modifier: Modifier, dark: Color = KagomColor.Surface.dark, light: Color = KagomColor.Surface.card) =
    Canvas(modifier) {
        when (glyph) {
            LandmarkGlyph.BOAT -> drawBoat(dark, light)
            LandmarkGlyph.SUNRISE -> drawSun()
            LandmarkGlyph.COFFEE -> drawCup(dark, light)
        }
    }

private fun DrawScope.drawBoat(hull: Color, sail: Color) {
    val w = size.width; val h = size.height
    drawPath(Path().apply { moveTo(w * 0.47f, 0f); lineTo(w * 0.47f, h * 0.62f); lineTo(w * 0.12f, h * 0.62f); close() }, sail)
    drawPath(Path().apply { moveTo(w * 0.53f, h * 0.05f); lineTo(w * 0.53f, h * 0.62f); lineTo(w * 0.86f, h * 0.62f); close() }, sail)
    drawPath(Path().apply { moveTo(0f, h * 0.68f); lineTo(w, h * 0.68f); lineTo(w * 0.84f, h); lineTo(w * 0.16f, h); close() }, hull)
}

private fun DrawScope.drawSun() {
    val c = Offset(size.width / 2, size.height * 0.6f)
    val r = size.minDimension * 0.42f
    drawCircle(KagomColor.Accent.tier.copy(alpha = 0.45f), r * 1.5f, c)
    drawCircle(KagomColor.Accent.tier, r, c)
    drawCircle(KagomColor.Severity.fg(com.example.roro.core.Severity.RECOMMEND), r, c, style = Stroke(r * 0.08f))
}

private fun DrawScope.drawCup(dark: Color, light: Color) {
    val w = size.width; val h = size.height
    drawRoundRect(light, Offset(w * 0.12f, h * 0.3f), Size(w * 0.6f, h * 0.6f), CornerRadius(w * 0.12f))
    drawArc(light, -90f, 180f, false, Offset(w * 0.58f, h * 0.4f), Size(w * 0.3f, h * 0.3f), style = Stroke(w * 0.07f))
    drawRect(dark, Offset(w * 0.12f, h * 0.3f), Size(w * 0.6f, h * 0.1f))
    for (i in 0..2) drawLine(light, Offset(w * (0.27f + i * 0.15f), h * 0.05f), Offset(w * (0.27f + i * 0.15f), h * 0.2f), w * 0.05f, StrokeCap.Round)
}

/** questIcon (Figma questIcon/flag 등, 194:1837 …): 도전과제 카드 아이콘. 40 격자에 선 굵기 3.33 → 크기의 1/12 */
@Composable
fun QuestIconGlyph(icon: QuestIcon, modifier: Modifier, color: Color = KagomColor.Text.accent) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val line = Stroke(w / 12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun at(x: Float, y: Float) = Offset(w * x, h * y)
    fun stroke(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = line)
    when (icon) {
        QuestIcon.FLAG -> {
            drawLine(color, at(0.18f, 0.12f), at(0.18f, 0.92f), line.width, StrokeCap.Round)
            stroke {
                moveTo(w * 0.18f, h * 0.16f)
                quadraticTo(w * 0.34f, h * 0.06f, w * 0.5f, h * 0.16f); quadraticTo(w * 0.66f, h * 0.26f, w * 0.84f, h * 0.16f)
                lineTo(w * 0.84f, h * 0.6f)
                quadraticTo(w * 0.66f, h * 0.7f, w * 0.5f, h * 0.6f); quadraticTo(w * 0.34f, h * 0.5f, w * 0.18f, h * 0.6f)
            }
        }
        QuestIcon.GAUGE -> {
            drawArc(color, 160f, 220f, false, at(0.1f, 0.2f), Size(w * 0.8f, h * 0.8f), style = line)
            drawLine(color, at(0.5f, 0.6f), at(0.66f, 0.42f), line.width, StrokeCap.Round)
        }
        QuestIcon.COMPASS -> {
            drawCircle(color, w * 0.4f, at(0.5f, 0.5f), style = line)
            stroke { moveTo(w * 0.64f, h * 0.36f); lineTo(w * 0.57f, h * 0.57f); lineTo(w * 0.36f, h * 0.64f); lineTo(w * 0.43f, h * 0.43f); close() }
        }
        QuestIcon.MAP -> {
            stroke {
                moveTo(w * 0.1f, h * 0.26f); lineTo(w * 0.37f, h * 0.14f); lineTo(w * 0.63f, h * 0.26f); lineTo(w * 0.9f, h * 0.14f)
                lineTo(w * 0.9f, h * 0.74f); lineTo(w * 0.63f, h * 0.86f); lineTo(w * 0.37f, h * 0.74f); lineTo(w * 0.1f, h * 0.86f); close()
            }
            drawLine(color, at(0.37f, 0.14f), at(0.37f, 0.74f), line.width, StrokeCap.Round)
            drawLine(color, at(0.63f, 0.26f), at(0.63f, 0.86f), line.width, StrokeCap.Round)
        }
        QuestIcon.WRENCH -> {
            // 머리(오른쪽 위로 벌어진 고리) + 왼쪽 아래로 뻗은 자루
            drawArc(color, -20f, 290f, false, at(0.46f, 0.1f), Size(w * 0.44f, h * 0.44f), style = line)
            drawLine(color, at(0.52f, 0.48f), at(0.16f, 0.84f), line.width * 1.6f, StrokeCap.Round)
        }
    }
}

/** avatarBear (Figma 194:4585): 달성률 카드의 곰 얼굴. 40 격자 좌표를 그대로 옮겼다 */
@Composable
fun AvatarBearGlyph(modifier: Modifier) = Canvas(modifier) {
    val u = size.minDimension / 40f
    fun dot(c: Color, x: Float, y: Float, r: Float) = drawCircle(c, r * u, Offset(x * u, y * u))
    dot(KagomColor.Avatar.ear, 10.5f, 11.5f, 6.5f); dot(KagomColor.Avatar.ear, 30.5f, 11.5f, 6.5f)
    dot(KagomColor.Avatar.earInner, 10f, 11f, 3f); dot(KagomColor.Avatar.earInner, 30f, 11f, 3f)
    dot(KagomColor.Avatar.head, 20.5f, 22.5f, 14.5f)
    drawOval(KagomColor.Avatar.muzzle, Offset(13f * u, 22f * u), Size(15f * u, 11f * u))
    dot(KagomColor.Avatar.face, 15f, 20f, 2f); dot(KagomColor.Avatar.face, 26f, 20f, 2f)
    drawOval(KagomColor.Avatar.face, Offset(18f * u, 24f * u), Size(5f * u, 3f * u))
}

/** POP-02 언박싱 선물 상자 */
@Composable
fun GiftBoxGlyph(modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val box = KagomColor.confetti[2]
    val lid = box.copy(red = box.red * 0.92f, green = box.green * 0.85f, blue = box.blue * 0.85f)
    val ribbon = KagomColor.Accent.tier
    drawRoundRect(box, Offset(w * 0.05f, h * 0.4f), Size(w * 0.9f, h * 0.6f), CornerRadius(w * 0.06f))
    drawRoundRect(lid, Offset(0f, h * 0.22f), Size(w, h * 0.26f), CornerRadius(w * 0.05f))
    drawRect(ribbon, Offset(w * 0.44f, h * 0.22f), Size(w * 0.12f, h * 0.78f))
    drawOval(ribbon, Offset(w * 0.24f, 0f), Size(w * 0.26f, h * 0.2f))
    drawOval(ribbon, Offset(w * 0.5f, 0f), Size(w * 0.26f, h * 0.2f))
    drawCircle(lid, w * 0.055f, Offset(w * 0.5f, h * 0.1f))
}

/** sparkle: 4각 별 장식 */
@Composable
fun SparkleGlyph(modifier: Modifier, color: Color = KagomColor.sparkle) = Canvas(modifier) {
    val cx = size.width / 2; val cy = size.height / 2; val r = size.minDimension / 2; val k = r * 0.22f
    drawPath(Path().apply {
        moveTo(cx, cy - r); quadraticTo(cx + k, cy - k, cx + r, cy); quadraticTo(cx + k, cy + k, cx, cy + r)
        quadraticTo(cx - k, cy + k, cx - r, cy); quadraticTo(cx - k, cy - k, cx, cy - r); close()
    }, color)
}

/**
 * confettiBurst: 모달 뒤 장식 레이어.
 * 처음 1.2초 동안 가운데(카드 뒤)에서 터져 나와 포물선을 그리며 제자리로 퍼지고, 그 뒤엔 천천히 흔들린다.
 */
@Composable
fun ConfettiLayer(modifier: Modifier = Modifier, seed: Int = 7, count: Int = 42) {
    val pieces = remember(seed, count) {
        val r = Random(seed)
        List(count) {
            Piece(r.nextFloat(), r.nextFloat(), r.nextFloat() * 360f, KagomColor.confetti[r.nextInt(KagomColor.confetti.size)],
                r.nextInt(3), 8f + r.nextFloat() * 18f)
        }
    }
    val burst = remember(seed) { Animatable(0f) }
    LaunchedEffect(seed) { burst.animateTo(1f, tween(1200, easing = LinearEasing)) }
    val t = rememberInfiniteTransition(label = "confetti")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Canvas(modifier.fillMaxSize()) {
        val b = burst.value
        val e = 1f - (1f - b) * (1f - b) * (1f - b)          // 빨리 퍼졌다 천천히 멈춘다
        val cx = size.width * 0.5f
        val cy = size.height * 0.45f
        pieces.forEachIndexed { i, p ->
            val tx = p.x * size.width
            val ty = p.y * size.height + drift * 10f * (if (p.shape == 0) 1 else -1)
            val x = cx + (tx - cx) * e
            // 위로 솟았다 내려앉는 포물선 (작은 조각일수록 높이)
            val y = cy + (ty - cy) * e - sin(PI.toFloat() * e) * size.height * (0.06f + 0.1f * (1f - p.size / 26f))
            val spin = (1f - e) * 540f * (if (i % 2 == 0) 1 else -1)
            val c = p.color.copy(alpha = p.color.alpha * (b * 6f).coerceAtMost(1f))
            rotate(p.angle + drift * 30f + spin, Offset(x, y)) {
                when (p.shape) {
                    0 -> drawCircle(c, p.size * 0.35f, Offset(x, y))
                    else -> drawRoundRect(c, Offset(x - p.size / 2, y - p.size * 0.25f), Size(p.size, p.size * 0.5f), CornerRadius(3f))
                }
            }
        }
    }
}

private data class Piece(val x: Float, val y: Float, val angle: Float, val color: Color, val shape: Int, val size: Float)
