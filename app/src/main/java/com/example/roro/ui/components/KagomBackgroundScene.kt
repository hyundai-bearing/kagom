package com.example.roro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.example.roro.core.Weather
import com.example.roro.ui.theme.KagomScenePalette
import com.example.roro.ui.theme.ScenePalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** 홈 배경 장면의 종류. 날씨 다섯 가지("오늘 날씨" BG-04, 맑음은 BG-01 맑은 들판과 같다)와 노을 언덕(BG-02), 동해 바다(BG-03) */
enum class SceneKind {
    CLEAR, CLOUDY, RAIN, SNOW, THUNDER, SUNSET, COAST;

    companion object {
        fun of(weather: Weather): SceneKind = when (weather) {
            Weather.CLEAR -> CLEAR
            Weather.CLOUDY -> CLOUDY
            Weather.RAIN -> RAIN
            Weather.SNOW -> SNOW
            Weather.THUNDER -> THUNDER
        }
    }
}

/**
 * 홈 배경 장면. 디자인에 없어 직접 그렸다 (가정).
 * 구도: 1280×1080 = Full 홈 캔버스, 땅은 아래 1/3, 칸 비율이 다르면 아래쪽을 기준으로 자른다 (Split, 옷장 미리보기, 썸네일).
 * 곰이 서는 가운데(x 480~800)에는 나무·배·등대를 두지 않는다.
 *
 * [animated]일 때만 움직인다 (구름이 흐르고, 나무가 흔들리고, 비·눈이 내리고, 파도가 친다). 주행 중과 썸네일은 멈춘 그림이다 (가이드 5.2).
 * [colorFilter]는 잠긴 배경을 탈색해 보일 때 쓴다.
 */
@Composable
fun BackgroundScene(
    kind: SceneKind, animated: Boolean, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null,
    /** 칸이 장면보다 좁아 좌우가 잘릴 때 어느 쪽을 남길지: -1 = 왼쪽, 0 = 가운데, 1 = 오른쪽 */
    horizontalBias: Float = 0f,
) {
    val palette = when (kind) {
        SceneKind.CLEAR -> KagomScenePalette.of(Weather.CLEAR)
        SceneKind.CLOUDY -> KagomScenePalette.of(Weather.CLOUDY)
        SceneKind.RAIN -> KagomScenePalette.of(Weather.RAIN)
        SceneKind.SNOW -> KagomScenePalette.of(Weather.SNOW)
        SceneKind.THUNDER -> KagomScenePalette.of(Weather.THUNDER)
        SceneKind.SUNSET -> KagomScenePalette.sunset
        SceneKind.COAST -> KagomScenePalette.coast
    }
    val model = remember(kind) { SceneModel(kind) }
    val layer = remember(colorFilter) { colorFilter?.let { Paint().apply { this.colorFilter = it } } }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            // 앱이 가려졌다 돌아오면 프레임 사이가 몇 분씩 벌어진다 → 그만큼 건너뛰지 않고 한 프레임만 나아간다.
            // 시간이 한없이 커지면 Float 정밀도가 떨어져 움직임이 끊기므로 한 시간마다 되감는다 (그때 구름 자리가 한 번 바뀐다)
            time.floatValue = (time.floatValue + ((now - last) / 1e9f).coerceIn(0f, MAX_FRAME_SECONDS)).mod(TIME_WRAP_SECONDS)
            last = now
        }
    }
    Canvas(modifier.clipToBounds()) {
        val fit = max(size.width / SCENE_W, size.height / SCENE_H)
        val draw: DrawScope.() -> Unit = {
            withTransform({
                translate((size.width - SCENE_W * fit) * (horizontalBias + 1f) / 2f, size.height - SCENE_H * fit)
                scale(fit, fit, Offset.Zero)
            }) {
                if (model.kind == SceneKind.COAST) drawCoast(model, palette, time.floatValue)
                else drawLand(model, palette, time.floatValue, still = !animated)
            }
        }
        if (layer == null) draw() else drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), layer)
            draw()
            canvas.restore()
        }
    }
}

private const val SCENE_W = 1280f
private const val SCENE_H = 1080f
/** 칸이 가로로 더 넓을 때를 생각해 양옆으로 넉넉히 그린다 */
private const val BLEED = 240f
private const val TAU = (2 * PI).toFloat()
private const val HORIZON = 600f
private const val MAX_FRAME_SECONDS = 0.1f
private const val TIME_WRAP_SECONDS = 3600f

private class Cloud(val x: Float, val y: Float, val scale: Float, val speed: Float, val alpha: Float)
private class Particle(val x: Float, val phase: Float, val speed: Float, val size: Float, val sway: Float)
private class Tree(val x: Float, val y: Float, val scale: Float, val phase: Float, val pine: Boolean)
private class Dot(val x: Float, val y: Float, val size: Float, val accent: Boolean, val phase: Float)
private class Puddle(val x: Float, val y: Float, val rx: Float, val ry: Float, val phase: Float)
private class Bird(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)
private class WaveLine(val path: Path, val dash: Float, val gap: Float, val speed: Float, val width: Float, val alpha: Float)

/** 언덕 능선: 완만한 물결 두 개를 더한 선 */
private class Ridge(val base: Float, val a1: Float, val l1: Float, val p1: Float, val a2: Float, val l2: Float, val p2: Float) {
    fun y(x: Float) = base + a1 * sin(TAU * x / l1 + p1) + a2 * sin(TAU * x / l2 + p2)
    val path = Path().apply {
        moveTo(-BLEED, y(-BLEED))
        var x = -BLEED
        while (x < SCENE_W + BLEED) { x += 16f; lineTo(x, y(x)) }
        lineTo(SCENE_W + BLEED, SCENE_H); lineTo(-BLEED, SCENE_H); close()
    }
}

/** 장면 하나에 놓이는 것들. 난수는 씨앗을 고정해 그림이 늘 같다 */
private class SceneModel(val kind: SceneKind) {
    val wet = kind == SceneKind.RAIN || kind == SceneKind.THUNDER
    val snowy = kind == SceneKind.SNOW
    val coast = kind == SceneKind.COAST

    val ridgeFar = Ridge(590f, 46f, 640f, 1.0f, 20f, 230f, 0.3f)
    val ridgeNear = Ridge(642f, 30f, 780f, 4.2f, 12f, 300f, 1.7f)
    val hillFar = Ridge(705f, 34f, 900f, 0.6f, 14f, 370f, 2.1f)
    val hillMid = Ridge(790f, 26f, 1100f, 3.4f, 10f, 420f, 0.4f)
    val hillNear = Ridge(880f, 18f, 1300f, 1.2f, 8f, 510f, 4.0f)

    /** 뭉게구름: 원 네 개와 납작한 밑동. 원점은 왼쪽 아래 */
    val cloud = Path().apply {
        addRoundRect(RoundRect(Rect(10f, -52f, 330f, 0f), CornerRadius(26f)))
        addOval(Rect(Offset(72f, -50f), 48f)); addOval(Rect(Offset(146f, -84f), 66f))
        addOval(Rect(Offset(228f, -64f), 54f)); addOval(Rect(Offset(286f, -40f), 38f))
    }

    val clouds: List<Cloud> = when (kind) {
        SceneKind.CLEAR -> listOf(
            Cloud(520f, 150f, 0.55f, 5f, 0.9f), Cloud(120f, 420f, 0.95f, 8f, 1f),
            Cloud(860f, 520f, 0.7f, 11f, 1f), Cloud(1320f, 330f, 0.8f, 7f, 0.95f),
        )
        SceneKind.CLOUDY -> listOf(
            Cloud(380f, 170f, 1.25f, 4f, 0.75f), Cloud(980f, 185f, 1.4f, 5f, 0.8f), Cloud(-260f, 200f, 1.1f, 4.5f, 0.7f),
            Cloud(90f, 400f, 1.05f, 7f, 1f), Cloud(820f, 500f, 0.8f, 10f, 1f), Cloud(1300f, 350f, 0.95f, 8f, 0.95f),
            Cloud(520f, 300f, 0.6f, 6f, 0.85f),
        )
        SceneKind.SNOW -> listOf(
            Cloud(420f, 165f, 1.2f, 4f, 0.8f), Cloud(1010f, 180f, 1.3f, 5f, 0.85f), Cloud(-240f, 190f, 1.0f, 4.5f, 0.75f),
            Cloud(110f, 410f, 0.95f, 7f, 1f), Cloud(860f, 510f, 0.7f, 9f, 1f),
        )
        // 비구름: 위쪽을 넓게 덮는 큰 구름 띠와 아래로 흘러가는 조각구름
        SceneKind.RAIN, SceneKind.THUNDER -> listOf(
            Cloud(-300f, 165f, 1.45f, 5f, 0.9f), Cloud(190f, 190f, 1.6f, 4f, 0.95f), Cloud(700f, 170f, 1.5f, 5.5f, 0.9f),
            Cloud(1160f, 195f, 1.55f, 4.5f, 0.95f), Cloud(60f, 410f, 0.9f, 9f, 0.9f), Cloud(900f, 500f, 0.75f, 12f, 0.9f),
        )
        SceneKind.SUNSET -> listOf(
            Cloud(150f, 390f, 1.0f, 7f, 0.95f), Cloud(700f, 160f, 0.65f, 5f, 0.9f),
            Cloud(900f, 430f, 0.6f, 10f, 0.85f), Cloud(1350f, 300f, 0.85f, 6f, 0.9f),
        )
        SceneKind.COAST -> listOf(
            Cloud(180f, 370f, 0.95f, 8f, 1f), Cloud(600f, 150f, 0.6f, 5f, 0.9f),
            Cloud(1300f, 300f, 0.8f, 7f, 0.95f), Cloud(880f, 440f, 0.5f, 11f, 0.9f),
        )
    }

    private val random = Random(20261113)

    // ───────────── 바다 (BG-03) ─────────────

    /** 왼쪽 곶: 수평선 위로 솟았다가 바다로 내려앉는 능선 */
    fun headlandY(x: Float): Float {
        val along = ((x - 60f) / 400f).coerceIn(0f, 1f)
        // 끝은 물속으로 천천히 잠긴다 (끝이 툭 잘려 보이지 않게)
        return 676f - 170f * 0.5f * (1f + cos(PI.toFloat() * along)) + 5f * sin(x / 38f) + max(0f, x - 440f) * 0.45f
    }
    val headland = Path().apply {
        moveTo(-BLEED, headlandY(-BLEED))
        var x = -BLEED
        while (x < 580f) { x += 14f; lineTo(x, headlandY(x)) }
        // 아래쪽은 모래밭이 덮는다
        lineTo(x, 820f); lineTo(-BLEED, 820f); close()
    }
    val island = Path().apply {
        moveTo(980f, HORIZON + 2f); cubicTo(1050f, 566f, 1130f, 548f, 1230f, 560f)
        cubicTo(1330f, 572f, 1420f, 560f, SCENE_W + BLEED, 572f); lineTo(SCENE_W + BLEED, HORIZON + 2f); close()
    }
    /** 모래밭과 바다가 만나는 선 */
    fun shoreY(x: Float) = 716f + 9f * sin(TAU * x / 620f + 1.1f) + 4f * sin(TAU * x / 210f)
    val sand = Path().apply {
        moveTo(-BLEED, shoreY(-BLEED))
        var x = -BLEED
        while (x < SCENE_W + BLEED) { x += 16f; lineTo(x, shoreY(x)) }
        lineTo(SCENE_W + BLEED, SCENE_H); lineTo(-BLEED, SCENE_H); close()
    }
    /** 밀려왔다 빠지는 물. 프레임마다 다시 채운다 */
    val swash = Path()
    val swashEdge = Path()
    val waves: List<WaveLine> = if (!coast) emptyList() else listOf(
        Triple(628f, 3f, 0.55f), Triple(650f, 3.5f, 0.6f), Triple(674f, 4f, 0.7f), Triple(696f, 4.5f, 0.75f),
    ).mapIndexed { i, (y, width, alpha) ->
        val path = Path().apply {
            var x = -BLEED
            moveTo(x, y)
            while (x < SCENE_W + BLEED) { x += 12f; lineTo(x, y + (2f + i) * sin(TAU * x / (150f + 40f * i) + i)) }
        }
        // 물마루: 긴 물결선 가운데 띄엄띄엄 보이는 흰 줄이 옆으로 흘러간다
        WaveLine(path, 46f + 14f * i, 120f + 30f * i, 10f + 5f * i, width, alpha)
    }
    val sailboat = Offset(396f, 650f)

    // ───────────── 들판 ─────────────

    val trees: List<Tree> = if (coast) {
        listOf(196f to 0.9f, 336f to 0.6f).map { (x, scale) -> Tree(x, headlandY(x) + 6f, scale, random.nextFloat() * TAU, pine = true) }
    } else {
        listOf(255f to 0.9f, 322f to 1.25f, 384f to 0.8f, 968f to 0.85f, 1032f to 1.2f, 1100f to 0.95f)
            .map { (x, scale) -> Tree(x, hillFar.y(x) + 8f, scale, random.nextFloat() * TAU, pine = snowy) }
    }

    /** 들꽃 (맑음·흐림·노을). 바다에서는 모래 위의 조개 */
    val flowers: List<Dot> = when {
        wet || snowy -> emptyList()
        coast -> List(7) {
            val x = 130f + random.nextFloat() * 1020f
            Dot(x, 800f + random.nextFloat() * 240f, 5f + random.nextFloat() * 3f, random.nextBoolean(), random.nextFloat() * TAU)
        }
        else -> List(when (kind) { SceneKind.CLEAR -> 16; SceneKind.SUNSET -> 11; else -> 8 }) {
            val x = 120f + random.nextFloat() * 1040f
            Dot(x, hillNear.y(x) + 26f + random.nextFloat() * 150f, 6f + random.nextFloat() * 3f, random.nextBoolean(), random.nextFloat() * TAU)
        }
    }
    /** 풀 포기. 바다에서는 곰이 서는 가운데를 비우고 모래밭 양옆에 둔다 */
    val tufts: List<Dot> = when {
        snowy -> emptyList()
        coast -> List(9) {
            val x = if (it % 2 == 0) 110f + random.nextFloat() * 300f else 880f + random.nextFloat() * 290f
            Dot(x, 790f + random.nextFloat() * 250f, 1f, false, random.nextFloat() * TAU)
        }
        else -> List(12) {
            val x = 100f + random.nextFloat() * 1080f
            val onMid = random.nextBoolean()
            Dot(x, (if (onMid) hillMid.y(x) + 22f + random.nextFloat() * 50f else hillNear.y(x) + 20f + random.nextFloat() * 150f), 1f, false, random.nextFloat() * TAU)
        }
    }
    val puddles: List<Puddle> = if (!wet) emptyList() else listOf(
        Puddle(300f, 968f, 78f, 14f, 0f), Puddle(1010f, 940f, 62f, 11f, 0.4f), Puddle(872f, 1034f, 50f, 9f, 0.75f),
    )
    /** 노을의 새 떼, 바다의 갈매기 */
    val birds: List<Bird> = when (kind) {
        SceneKind.SUNSET -> listOf(Bird(420f, 330f, 16f, 15f, 0f), Bird(470f, 356f, 16f, 12f, 1.3f), Bird(376f, 362f, 16f, 11f, 2.2f))
        SceneKind.COAST -> listOf(Bird(300f, 300f, 20f, 17f, 0.4f), Bird(1040f, 380f, 14f, 13f, 1.9f))
        else -> emptyList()
    }

    private fun particles(count: Int, speed: Float, size: Float, sway: Float) = List(count) {
        Particle(random.nextFloat(), random.nextFloat(), speed * (0.85f + random.nextFloat() * 0.3f), size * (0.8f + random.nextFloat() * 0.4f), sway)
    }
    /** 먼 겹은 언덕 뒤로, 가까운 겹은 언덕 앞으로 내린다 */
    val far: List<Particle> = when {
        kind == SceneKind.THUNDER -> particles(96, 700f, 26f, 0f)
        wet -> particles(72, 620f, 24f, 0f)
        snowy -> particles(46, 46f, 3.5f, 14f)
        else -> emptyList()
    }
    val near: List<Particle> = when {
        kind == SceneKind.THUNDER -> particles(60, 980f, 40f, 0f)
        wet -> particles(44, 880f, 38f, 0f)
        snowy -> particles(30, 78f, 7f, 22f)
        else -> emptyList()
    }

    /** 바람: 비가 오면 나무가 더 크게 흔들린다 */
    val swayDegrees = when (kind) { SceneKind.THUNDER -> 4f; SceneKind.RAIN -> 3f; else -> 1.2f }

    /** 전나무 세 단: 단마다 (나무, 그 위의 밝은 면 = 눈 또는 햇빛). 원점은 밑동 */
    val pine: List<Pair<Path, Path>> = listOf(Triple(-34f, 46f, 62f), Triple(-70f, 38f, 56f), Triple(-104f, 28f, 48f)).map { (bottom, half, height) ->
        Path().apply { moveTo(0f, bottom - height); lineTo(-half, bottom); lineTo(half, bottom); close() } to Path().apply {
            moveTo(0f, bottom - height); lineTo(-half * 0.62f, bottom - height * 0.38f)
            lineTo(-half * 0.2f, bottom - height * 0.5f); lineTo(half * 0.2f, bottom - height * 0.34f)
            lineTo(half * 0.62f, bottom - height * 0.38f); close()
        }
    }

    val bolt = Path().apply {
        moveTo(0f, 0f); lineTo(-52f, 112f); lineTo(-10f, 112f); lineTo(-44f, 218f)
        lineTo(62f, 74f); lineTo(16f, 74f); lineTo(50f, 0f); close()
    }
}

private fun DrawScope.drawSky(p: ScenePalette, bottom: Float) {
    val colors = listOfNotNull(p.skyTop, p.skyMid, p.skyBottom)
    drawRect(Brush.verticalGradient(colors, 0f, bottom), Offset(-BLEED, 0f), Size(SCENE_W + 2 * BLEED, SCENE_H))
}

private fun DrawScope.drawLand(m: SceneModel, p: ScenePalette, t: Float, still: Boolean) {
    drawSky(p, 700f)
    drawSun(m, p, t)
    m.clouds.forEach { drawCloud(m, p, it, t) }
    m.birds.forEach { drawBird(p.trunk.copy(alpha = 0.75f), it, t, leftward = true) }

    // 먼 산은 아래로 갈수록 하늘에 섞여 안개 낀 것처럼 옅어진다
    drawPath(m.ridgeFar.path, Brush.verticalGradient(listOf(p.ridgeFar, lerp(p.ridgeFar, p.skyBottom, 0.6f)), 540f, 720f))
    drawPath(m.ridgeNear.path, Brush.verticalGradient(listOf(p.ridgeNear, lerp(p.ridgeNear, p.skyBottom, 0.45f)), 610f, 760f))

    drawFalling(m, p, m.far, t, alpha = if (m.snowy) 0.8f else 0.4f)

    drawPath(m.hillFar.path, Brush.verticalGradient(listOf(p.hillFar, lerp(p.hillFar, p.hillShade, 0.3f)), 670f, 860f))
    m.trees.forEach { drawTree(m, p, it, t) }
    drawPath(m.hillMid.path, Brush.verticalGradient(listOf(p.hillMid, lerp(p.hillMid, p.hillShade, 0.35f)), 760f, 940f))
    drawPath(m.hillNear.path, Brush.verticalGradient(listOf(p.hillNear, lerp(p.hillNear, p.hillShade, 0.55f)), 860f, SCENE_H))

    m.tufts.forEach { drawTuft(p.hillShade.copy(alpha = 0.55f), it, t, m.swayDegrees) }
    m.flowers.forEach { drawFlower(p, it, t) }
    m.puddles.forEach { drawPuddle(p, it, t) }

    drawFalling(m, p, m.near, t, alpha = if (m.snowy) 0.95f else 0.55f)
    if (m.kind == SceneKind.THUNDER) drawLightning(m, p, t, still)
}

/** BG-03 동해 바다: 수평선에 걸린 해, 왼쪽 곶의 등대와 소나무, 밀려오는 파도, 곰이 서는 모래밭 */
private fun DrawScope.drawCoast(m: SceneModel, p: ScenePalette, t: Float) {
    drawSky(p, HORIZON)
    drawSun(m, p, t)
    m.clouds.forEach { drawCloud(m, p, it, t) }
    drawPath(m.island, Brush.verticalGradient(listOf(p.ridgeFar, lerp(p.ridgeFar, p.skyBottom, 0.5f)), 545f, HORIZON))

    drawRect(Brush.verticalGradient(listOf(p.sea, p.seaDeep), HORIZON, 720f), Offset(-BLEED, HORIZON), Size(SCENE_W + 2 * BLEED, 180f))
    // 해가 바다에 비친 빛: 아래로 갈수록 넓어지고 물결 따라 일렁인다
    p.sun?.let { sun ->
        repeat(5) { i ->
            val half = 34f + 15f * i + 9f * sin(t * 1.3f + i * 1.7f)
            val alpha = 0.5f + 0.22f * sin(t * 1.7f + i * 1.3f)
            drawOval(lerp(sun, p.white, 0.65f).copy(alpha = alpha), Offset(SUN_COAST.x - half, 613f + 17f * i), Size(half * 2, 8f))
        }
    }
    m.waves.forEach { wave ->
        drawPath(wave.path, p.particle.copy(alpha = wave.alpha), style = Stroke(
            wave.width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(wave.dash, wave.gap), -t * wave.speed),
        ))
    }
    drawSailboat(m, p, t)

    drawPath(m.headland, Brush.verticalGradient(listOf(p.ridgeNear, lerp(p.ridgeNear, p.seaDeep, 0.35f)), 500f, 720f))
    drawLighthouse(p, Offset(256f, m.headlandY(256f) + 4f), t)
    m.trees.forEach { drawTree(m, p, it, t) }

    drawPath(m.sand, Brush.verticalGradient(listOf(p.hillNear, lerp(p.hillNear, p.hillShade, 0.7f)), 700f, SCENE_H))
    // 물이 닿았던 자리는 젖어서 조금 짙다
    drawPath(m.sand, Brush.verticalGradient(listOf(p.hillFar.copy(alpha = 0.85f), p.hillFar.copy(alpha = 0f)), 705f, 800f))

    // 얕은 물이 모래밭 위로 밀려왔다 빠진다: 모래밭 끝에서 물 끝까지의 띠와 그 끝의 흰 거품
    val reach = 0.5f + 0.5f * sin(TAU * t / 5.2f)
    m.swash.rewind(); m.swashEdge.rewind()
    var x = -BLEED
    while (x <= SCENE_W + BLEED) {
        val y = m.shoreY(x) + 6f + 26f * reach + 5f * sin(TAU * x / 170f + t * 0.9f)
        if (x == -BLEED) { m.swash.moveTo(x, y); m.swashEdge.moveTo(x, y) } else { m.swash.lineTo(x, y); m.swashEdge.lineTo(x, y) }
        x += 20f
    }
    while (x >= -BLEED) { m.swash.lineTo(x, m.shoreY(x) - 3f); x -= 20f }
    m.swash.close()
    drawPath(m.swash, Brush.verticalGradient(listOf(p.seaDeep, p.water), 700f, 750f), alpha = 0.92f)
    drawPath(m.swashEdge, p.particle.copy(alpha = 0.95f), style = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    m.tufts.forEach { drawTuft(p.tree.copy(alpha = 0.7f), it, t, m.swayDegrees * 2f) }
    m.flowers.forEach { shell ->
        drawOval(lerp(if (shell.accent) p.accent else p.particle, p.hillNear, 0.35f), Offset(shell.x - shell.size, shell.y - shell.size * 0.7f), Size(shell.size * 2, shell.size * 1.4f))
    }
    m.birds.forEach { drawBird(p.particle, it, t, leftward = false) }
}

/** Full 헤더의 EXP 막대(x 900까지)에 닿지 않게 오른쪽 위에 둔다 */
private val SUN_HIGH = Offset(990f, 136f)
/** 노을: 먼 산 뒤로 반쯤 넘어간 큰 해 */
private val SUN_SET = Offset(910f, 556f)
/** 동해: 수평선에 걸린 해 */
private val SUN_COAST = Offset(900f, 596f)

private fun DrawScope.drawSun(m: SceneModel, p: ScenePalette, t: Float) {
    val sun = p.sun ?: return
    val pulse = 1f + 0.04f * sin(TAU * t / 6f)
    val center = when (m.kind) { SceneKind.SUNSET -> SUN_SET; SceneKind.COAST -> SUN_COAST; else -> SUN_HIGH }
    val radius = when (m.kind) { SceneKind.SUNSET -> 124f; SceneKind.COAST -> 104f; else -> 76f }
    val veiled = m.kind == SceneKind.CLOUDY
    p.sunGlow?.let { glow ->
        val reach = radius * (if (veiled) 2.5f else 3.4f) * pulse
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = if (veiled) 0.6f else 0.9f), glow.copy(alpha = 0f)), center, reach), reach, center)
    }
    if (m.kind == SceneKind.CLEAR) {
        // 햇살: 천천히 도는 부드러운 살 열두 개
        rotate(t * 3f, center) {
            repeat(12) { i ->
                rotate(i * 30f, center) {
                    drawLine(sun.copy(alpha = 0.4f), center + Offset(0f, -108f), center + Offset(0f, if (i % 2 == 0) -158f else -138f), 9f, StrokeCap.Round)
                }
            }
        }
    }
    val highlight = center + Offset(-radius * 0.24f, -radius * 0.24f)
    drawCircle(Brush.radialGradient(listOf(lerp(sun, p.white, 0.4f), sun), highlight, radius * 1.37f), radius, center, alpha = if (veiled) 0.75f else 1f)
}

private fun DrawScope.drawCloud(m: SceneModel, p: ScenePalette, c: Cloud, t: Float) {
    val margin = 360f * c.scale + BLEED
    val span = SCENE_W + 2 * margin
    val x = (c.x + c.speed * t + margin).mod(span) - margin
    withTransform({ translate(x, c.y); scale(c.scale, c.scale, Offset.Zero) }) {
        drawPath(m.cloud, Brush.verticalGradient(listOf(p.cloudLight, p.cloudShade), -150f, 0f), alpha = c.alpha)
    }
}

private fun DrawScope.drawBird(color: Color, bird: Bird, t: Float, leftward: Boolean) {
    val span = SCENE_W + 2 * BLEED
    val travelled = bird.speed * t
    val x = (bird.x + (if (leftward) -travelled else travelled) + BLEED).mod(span) - BLEED
    val y = bird.y + 7f * sin(t * 0.8f + bird.phase)
    // 날갯짓: 날개 끝이 위아래로 오르내린다
    val lift = bird.size * (0.25f + 0.45f * sin(TAU * t / 0.9f + bird.phase))
    val body = Offset(x, y)
    drawLine(color, body, body + Offset(-bird.size, -lift), bird.size / 4.5f, StrokeCap.Round)
    drawLine(color, body, body + Offset(bird.size, -lift), bird.size / 4.5f, StrokeCap.Round)
}

private fun DrawScope.drawTree(m: SceneModel, p: ScenePalette, tree: Tree, t: Float) {
    val base = Offset(tree.x, tree.y)
    val sway = m.swayDegrees * sin(TAU * t / 4.6f + tree.phase)
    // 발밑 그늘은 나무와 같이 흔들리지 않는다
    if (!m.coast) drawOval(p.hillShade.copy(alpha = 0.28f), base - Offset(44f * tree.scale, 7f * tree.scale), Size(88f * tree.scale, 16f * tree.scale))
    withTransform({ rotate(sway, base); translate(base.x, base.y); scale(tree.scale, tree.scale, Offset.Zero) }) {
        drawRoundRect(p.trunk, Offset(-5f, -40f), Size(10f, 44f), CornerRadius(4f))
        if (tree.pine) {
            m.pine.forEach { (tier, lit) ->
                drawPath(tier, p.tree)
                drawPath(lit, p.treeLight, alpha = if (m.snowy) 1f else 0.55f)
            }
        } else {
            drawCircle(p.tree, 26f, Offset(-22f, -58f)); drawCircle(p.tree, 24f, Offset(22f, -56f)); drawCircle(p.tree, 36f, Offset(0f, -76f))
            drawCircle(p.treeLight, 20f, Offset(-10f, -86f), alpha = 0.7f)
        }
    }
}

private fun DrawScope.drawTuft(color: Color, d: Dot, t: Float, swayDegrees: Float) {
    val lean = swayDegrees * 2f * sin(TAU * t / 3.4f + d.phase)
    listOf(-22f, 0f, 22f).forEach { angle ->
        rotate(angle + lean, Offset(d.x, d.y)) { drawLine(color, Offset(d.x, d.y), Offset(d.x, d.y - 15f), 3.5f, StrokeCap.Round) }
    }
}

private fun DrawScope.drawFlower(p: ScenePalette, d: Dot, t: Float) {
    val x = d.x + 2.5f * sin(TAU * t / 3.8f + d.phase)
    drawLine(p.hillShade.copy(alpha = 0.6f), Offset(d.x, d.y + d.size * 2.2f), Offset(x, d.y), 2.5f, StrokeCap.Round)
    drawCircle(if (d.accent) p.accent else p.particle, d.size, Offset(x, d.y))
    drawCircle(if (d.accent) p.particle else p.accent, d.size * 0.38f, Offset(x, d.y))
}

private fun DrawScope.drawPuddle(p: ScenePalette, puddle: Puddle, t: Float) {
    val center = Offset(puddle.x, puddle.y)
    drawOval(p.water.copy(alpha = 0.85f), center - Offset(puddle.rx, puddle.ry), Size(puddle.rx * 2, puddle.ry * 2))
    // 빗방울이 떨어진 자리의 물결: 가운데서 퍼지며 옅어진다
    repeat(2) { i ->
        val grow = (t / 2.4f + puddle.phase + i * 0.5f).mod(1f)
        val rx = puddle.rx * (0.15f + 0.75f * grow); val ry = puddle.ry * (0.15f + 0.75f * grow)
        drawOval(p.white.copy(alpha = 0.7f * (1f - grow)), center - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(2.5f))
    }
}

private fun DrawScope.drawFalling(m: SceneModel, p: ScenePalette, particles: List<Particle>, t: Float, alpha: Float) {
    if (particles.isEmpty()) return
    val color = p.particle.copy(alpha = alpha)
    val fall = SCENE_H + 120f
    if (m.snowy) particles.forEach { f ->
        val y = (f.phase * fall + f.speed * t).mod(fall) - 60f
        val x = -BLEED / 2 + f.x * (SCENE_W + BLEED) + f.sway * sin(TAU * t / 4.2f + f.phase * TAU)
        drawCircle(color, f.size, Offset(x, y))
    } else particles.forEach { f ->
        // 빗줄기는 바람에 왼쪽으로 기울어 떨어진다
        val travelled = (f.phase * fall + f.speed * t).mod(fall)
        val start = Offset(f.x * (SCENE_W + 2 * BLEED) - BLEED / 2 - RAIN_SLANT * travelled, travelled - 60f)
        // 비는 위쪽 구름 띠 아래에서부터 보인다
        if (start.y > RAIN_FROM) drawLine(color, start, start + Offset(-RAIN_SLANT * f.size, f.size), f.size / 11f, StrokeCap.Round)
    }
}

private const val RAIN_SLANT = 0.26f
private const val RAIN_FROM = 170f
/** 번개 한 번의 길이(초)와 번개 사이 간격 */
private const val FLASH_SECONDS = 0.7f
private const val FLASH_EVERY = 6.5f

private fun DrawScope.drawLightning(m: SceneModel, p: ScenePalette, t: Float, still: Boolean) {
    val cycle = (t / FLASH_EVERY).toInt()
    val at = t - cycle * FLASH_EVERY
    // 한 번도 움직인 적 없는 그림(썸네일, 주행 중에 켠 홈)에는 번개를 그려 둔다. 움직이다 멈춘 그림(주행 시작)에는
    // 번개를 새로 띄우지 않는다 (기어를 바꾸는 순간 그림이 달라지지 않게). 어느 쪽도 하늘은 번쩍이지 않는다
    val strength = when {
        still -> if (t == 0f) 1f else 0f
        at > FLASH_SECONDS -> 0f
        at < 0.1f -> 1f
        at < 0.18f -> 0.15f
        else -> 0.9f * (1f - (at - 0.18f) / (FLASH_SECONDS - 0.18f))
    }
    if (strength <= 0f) return
    if (!still) drawRect(p.white.copy(alpha = 0.2f * strength), Offset(-BLEED, 0f), Size(SCENE_W + 2 * BLEED, SCENE_H))
    // 오른쪽과 왼쪽 구름 아래에서 번갈아 친다. 왼쪽은 Split 헤더(위쪽) 아래에 둔다
    val origin = if (still || cycle % 2 == 0) Offset(950f, 205f) else Offset(330f, 300f)
    withTransform({ translate(origin.x, origin.y) }) {
        drawPath(m.bolt, lerp(p.accent, p.white, 0.6f).copy(alpha = 0.4f * strength), style = Stroke(14f, join = StrokeJoin.Round))
        drawPath(m.bolt, p.accent, alpha = strength)
    }
}

/** 곶 위의 등대: 흰 몸통, 붉은 띠와 지붕, 숨 쉬듯 밝아지는 불빛 */
private fun DrawScope.drawLighthouse(p: ScenePalette, base: Offset, t: Float) {
    val glow = 0.35f + 0.3f * sin(TAU * t / 3.6f)
    val lamp = base + Offset(0f, -92f)
    p.sunGlow?.let { drawCircle(Brush.radialGradient(listOf(it.copy(alpha = glow), it.copy(alpha = 0f)), lamp, 54f), 54f, lamp) }
    drawPath(Path().apply {
        moveTo(base.x - 15f, base.y); lineTo(base.x - 10f, base.y - 80f); lineTo(base.x + 10f, base.y - 80f); lineTo(base.x + 15f, base.y); close()
    }, p.particle)
    drawRect(p.accent, Offset(base.x - 12.6f, base.y - 46f), Size(25.2f, 13f))
    drawRoundRect(p.trunk, Offset(base.x - 14f, base.y - 86f), Size(28f, 6f), CornerRadius(2f))
    drawRect(lerp(p.sunGlow ?: p.particle, p.white, 0.3f), Offset(base.x - 8f, base.y - 102f), Size(16f, 16f))
    drawPath(Path().apply { moveTo(base.x - 13f, base.y - 102f); lineTo(base.x, base.y - 120f); lineTo(base.x + 13f, base.y - 102f); close() }, p.accent)
}

/** 돛단배: 물결 따라 오르내리며 조금 기운다 */
private fun DrawScope.drawSailboat(m: SceneModel, p: ScenePalette, t: Float) {
    val at = m.sailboat + Offset(0f, 2.5f * sin(t * 1.4f))
    rotate(2.2f * sin(t * 1.1f + 0.6f), at) {
        drawPath(Path().apply {
            moveTo(at.x - 30f, at.y - 2f); lineTo(at.x + 30f, at.y - 2f); lineTo(at.x + 20f, at.y + 12f); lineTo(at.x - 20f, at.y + 12f); close()
        }, p.accent)
        drawLine(p.trunk, at + Offset(0f, -2f), at + Offset(0f, -58f), 3f)
        drawPath(Path().apply { moveTo(at.x + 3f, at.y - 56f); lineTo(at.x + 3f, at.y - 8f); lineTo(at.x + 30f, at.y - 8f); close() }, p.particle)
        drawPath(Path().apply { moveTo(at.x - 3f, at.y - 48f); lineTo(at.x - 3f, at.y - 8f); lineTo(at.x - 22f, at.y - 8f); close() }, p.cloudShade)
    }
}
