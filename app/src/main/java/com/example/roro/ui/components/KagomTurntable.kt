package com.example.roro.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val N = KagomRenders.TURN_FRAMES
private const val ROWS = KagomRenders.TURN_ROWS
private const val STEP = 360f / N

/**
 * 돌려보기 각도. [yaw] = 도 단위 누적 각 (프레임 i = 리그를 +i×15° 돌린 것, 카메라가 곰의 왼쪽(+X)으로 돈다),
 * [pitch] = 고도 단 (0 = 거의 정면 높이, [ROWS]-1 = 위에서 내려다보기) 연속값.
 * [restFrame] = 손을 뗐을 때 돌아갈 방향 (치장·온보딩 0 = 정면, 홈 [KagomRenders.HOME_YAW]).
 */
@Stable
class TurnState(val restFrame: Int = 0) {
    internal val yaw = Animatable(restFrame * STEP)
    internal val pitch = Animatable(0f)
    internal var settleJob: Job? = null

    /** 한 번이라도 밀어 봤는지 (안내 문구, 프레임 준비 시작) */
    var touched by mutableStateOf(false)
        internal set
    var dragging by mutableStateOf(false)
        internal set

    val frame: Int get() = Math.floorMod((yaw.value / STEP).roundToInt(), N)
    val row: Int get() = pitch.value.roundToInt().coerceIn(0, ROWS - 1)
    /** 프레임 단위 연속 방향 (0..N), 소수 부분 = 다음 프레임 쪽으로 간 정도 */
    internal val yawFrames: Float get() = (yaw.value / STEP).mod(N.toFloat())

    /** 처음 각도에 멈춰 있음 */
    val atRest: Boolean get() = !dragging && frame == restFrame && row == 0 && !yaw.isRunning && !pitch.isRunning

    /**
     * 처음 각도로 (가까운 쪽으로 돈다).
     * settleJob을 여기서 취소하면 안 된다: 홈에서는 settleJob 안에서 이 함수를 부르므로 자기 자신이 취소돼
     * 제자리로 돌아오지 않았다. 다른 데서 부르면 animateTo가 진행 중인 회전을 끊으므로 따로 취소할 필요가 없다.
     */
    suspend fun reset(ms: Int = 450) = coroutineScope {
        launch { pitch.animateTo(0f, tween(ms)) }
        val r = restFrame * STEP
        yaw.animateTo(((yaw.value - r) / 360f).roundToInt() * 360f + r, tween(ms))
    }
}

/**
 * 좌우로 밀면 돌고, 위아래로 밀면 고도 단이 바뀐다 (아래로 밀면 위에서 내려다본다).
 * 손을 떼면 가까운 방향·단에 맞추고, [springBack]이면 잠시 뒤 처음 각도로 돌아간다.
 */
fun Modifier.turnGestures(
    state: TurnState,
    scope: CoroutineScope,
    enabled: Boolean,
    springBack: Boolean = false,
): Modifier = if (!enabled) this else pointerInput(state, springBack) {
    val tracker = VelocityTracker()
    // 디자인 1dp당 0.3° → 캐릭터 폭만큼 밀면 반 바퀴쯤. 위아래는 72dp마다 한 단
    val degPerPx = 0.3f / density
    val rowPerPx = 1f / (72.dp.toPx())

    fun settle() {
        state.dragging = false
        val v = -tracker.calculateVelocity().x * degPerPx
        state.settleJob = scope.launch {
            launch { state.pitch.animateTo(state.pitch.value.roundToInt().toFloat(), tween(180)) }
            if (abs(v) > 60f) state.yaw.animateDecay(v, exponentialDecay(frictionMultiplier = 2.2f))
            state.yaw.animateTo((state.yaw.value / STEP).roundToInt() * STEP, tween(160))
            if (springBack) {
                delay(900)
                state.reset()
            }
        }
    }

    detectDragGestures(
        onDragStart = {
            tracker.resetTracking()
            state.settleJob?.cancel()
            state.touched = true
            state.dragging = true
            scope.launch { state.yaw.stop(); state.pitch.stop() }
        },
        onDragEnd = { settle() },
        onDragCancel = { settle() },
    ) { change, d ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        scope.launch {
            // 손가락을 오른쪽으로 밀면 얼굴이 오른쪽으로 돈다 (= 카메라는 곰의 오른쪽(-X)으로)
            state.yaw.snapTo(state.yaw.value - d.x * degPerPx)
            state.pitch.snapTo((state.pitch.value + d.y * rowPerPx).coerceIn(0f, ROWS - 1f))
        }
    }
}

/**
 * 합성한 돌려보기 프레임 캐시. 키 = 단 × N + 방향.
 * 지금 각도에서 가까운 프레임부터 만들고(같은 단 전부 → 이웃 단 ±3), 최대 [MAX]장만 들고 있는다.
 * 한 장 480×754 ARGB ≈ 1.4 MB → 최대 약 58 MB (3단을 다 들고 있으면 100 MB가 넘는다).
 */
@Stable
class TurnFrames internal constructor(private val context: Context, private val skinCode: String, private val worn: List<String>) {
    private val cache = mutableStateMapOf<Int, ImageBitmap>()
    private val missing = HashSet<Int>()

    /** (row, i)에 가장 가까운 준비된 프레임: 같은 단에서 가까운 방향 → 0단 */
    fun nearest(row: Int, i: Int): ImageBitmap? {
        for (r in if (row == 0) listOf(0) else listOf(row, 0)) {
            for (k in 0..N / 2) {
                cache[key(r, i + k)]?.let { return it }
                cache[key(r, i - k)]?.let { return it }
            }
        }
        return null
    }

    /** 정확히 (row, i) 프레임 (아직 없으면 null) */
    fun exact(row: Int, i: Int): ImageBitmap? = cache[key(row, i)]

    val hasAny: Boolean get() = cache.isNotEmpty()

    fun clear() = cache.clear()

    private fun key(row: Int, i: Int) = row * N + Math.floorMod(i, N)

    private fun wanted(row: Int, i: Int): List<Int> = buildList {
        for (k in 0..N / 2) { add(key(row, i + k)); add(key(row, i - k)) }
        for (r in listOf(row + 1, row - 1)) if (r in 0 until ROWS) for (k in 0..3) { add(key(r, i + k)); add(key(r, i - k)) }
    }.distinct()

    internal fun next(row: Int, i: Int): Int? = wanted(row, i).firstOrNull { it !in cache && it !in missing }

    internal suspend fun load(k: Int, row: Int, i: Int) {
        val bmp = withContext(Dispatchers.Default) { compose(context, skinCode, worn, k / N, k % N) }
        if (bmp == null) { missing += k; return }
        cache[k] = bmp
        if (cache.size > MAX) {
            fun dist(x: Int) = abs(x / N - row) * N + minOf(Math.floorMod(x % N - i, N), Math.floorMod(i - x % N, N))
            cache.keys.sortedByDescending(::dist).take(cache.size - MAX).forEach { cache.remove(it) }
        }
    }

    companion object { const val MAX = 40 }
}

/** [active]일 때만 프레임을 만든다 (홈은 처음 밀었을 때부터) */
@Composable
fun rememberTurnFrames(skinCode: String, worn: List<String>, state: TurnState, active: Boolean): TurnFrames {
    val context = LocalContext.current.applicationContext
    val frames = remember(skinCode, worn) { TurnFrames(context, skinCode, worn) }
    LaunchedEffect(frames, active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val row = state.row
            val i = state.frame
            val k = frames.next(row, i)
            if (k == null) {
                snapshotFlow { state.row to state.frame }.first { it != row to i }
                continue
            }
            frames.load(k, row, i)
        }
    }
    return frames
}

/** 프레임 사이 위치 [f](0..1) → 다음 프레임 비중. 가운데 [BLEND] 구간에서만 섞는다 (반반 섞으면 7.5° 차이로 얼굴이 두 개로 보였다) */
private const val BLEND = 0.3f
private fun mix(f: Float): Float {
    val t = ((f - (1 - BLEND) / 2) / BLEND).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/**
 * 지금 각도 그림. 프레임 사이 각도에서는 양옆 방향 두 장과 위아래 단 두 장(최대 네 장)을 섞어 넘어가는 순간을 부드럽게 한다.
 * 대부분의 각도에서는 가까운 한 장이 또렷하게 보이고, 다음 프레임으로 넘어가는 가운데 구간([BLEND])에서만 짧게 섞는다.
 * 섞기는 미리 곱한 알파끼리 더하기(Plus)라 겹친 곳이 비쳐 보이지 않는다.
 * 섞을 프레임이 아직 없으면 가까운 프레임, 그것도 없으면 직전에 보이던 그림.
 */
@Composable
fun TurnImage(frames: TurnFrames, state: TurnState, modifier: Modifier = Modifier) {
    val last = remember { arrayOfNulls<ImageBitmap>(1) }
    val y = state.yawFrames
    val i0 = y.toInt()
    val fy = mix(y - i0)
    val p = state.pitch.value.coerceIn(0f, ROWS - 1f)
    val r0 = p.toInt().coerceAtMost(ROWS - 1)
    val fr = mix(p - r0)
    val parts = buildList {
        for ((r, wr) in listOf(r0 to 1 - fr, (r0 + 1).coerceAtMost(ROWS - 1) to fr)) {
            for ((i, wi) in listOf(i0 to 1 - fy, i0 + 1 to fy)) {
                val w = wr * wi
                if (w > 0.004f) frames.exact(r, i)?.let { add(it to w) }
            }
        }
    }
    val total = parts.sumOf { it.second.toDouble() }.toFloat()
    if (parts.isEmpty() || total < 0.6f) {
        // 섞을 프레임이 모자라면 (막 돌리기 시작했을 때) 가까운 한 장
        val shown = frames.nearest(state.row, state.frame) ?: last[0]
        last[0] = shown
        if (shown != null) Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
        return
    }
    last[0] = parts.maxBy { it.second }.first
    Spacer(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                val img = parts[0].first
                val s = minOf(size.width / img.width, size.height / img.height)
                val w = (img.width * s).roundToInt()
                val h = (img.height * s).roundToInt()
                val at = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt())
                parts.forEach { (bmp, wt) ->
                    drawImage(bmp, dstOffset = at, dstSize = IntSize(w, h), alpha = wt / total,
                        blendMode = BlendMode.Plus, filterQuality = FilterQuality.Medium)
                }
            },
    )
}

/**
 * 돌려보기 (치장 시착·온보딩): 좌우로 밀면 제자리에서 돌고, 위아래로 밀면 위에서 내려다본다.
 * [enabled] = false (주행 중)이면 정면으로 돌아가고 밀어도 움직이지 않는다.
 */
@Composable
fun KagomTurntable(
    skinCode: String,
    worn: List<String>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showControls: Boolean = true,
) {
    val state = remember { TurnState(0) }
    val frames = rememberTurnFrames(skinCode, worn, state, active = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(enabled) { if (!enabled) state.reset(400) }

    Box(
        modifier
            .semantics { contentDescription = "카곰 돌려보기, 좌우로 밀면 돌고 위아래로 밀면 위에서 볼 수 있어요" }
            .turnGestures(state, scope, enabled),
        contentAlignment = Alignment.Center,
    ) {
        TurnImage(frames, state, Modifier.fillMaxSize())

        // 안내 (처음 한 번) / 정면으로
        // Split 프리뷰(폭 240)보다 안내 글이 길다: 줄을 바꾸지 않고 캔버스 밖으로 넘쳐 가운데에 놓는다
        AnimatedVisibility(showControls && enabled && !state.touched, Modifier.align(Alignment.BottomCenter).wrapContentWidth(unbounded = true), enter = fadeIn(), exit = fadeOut()) {
            Pill("‹  좌우·위아래로 밀어서 돌려보기  ›")
        }
        AnimatedVisibility(showControls && enabled && (state.frame != 0 || state.row != 0), Modifier.align(Alignment.BottomEnd), enter = fadeIn(), exit = fadeOut()) {
            Pill("정면", Modifier.clickable { scope.launch { state.reset() } })
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.padding(8.dp).height(36.dp)
            .background(KagomColor.Surface.card.copy(alpha = 0.9f), RoundedCornerShape(KagomRadius.full))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = KagomType.Caption14Strong, color = KagomColor.Text.secondary, maxLines = 1, softWrap = false) }
}

/** 프레임 합성: 기본 몸 위에 착용 레이어를 차례로 */
private fun compose(context: Context, skinCode: String, worn: List<String>, row: Int, i: Int): ImageBitmap? {
    fun decode(path: String): Bitmap? = runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
    val base = decode(KagomRenders.turnBase(skinCode, row, i)) ?: return null
    if (worn.isEmpty()) return base.asImageBitmap()
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    base.recycle()
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    worn.forEach { id ->
        decode(KagomRenders.turnLayer(id, row, i))?.let { canvas.drawBitmap(it, 0f, 0f, paint); it.recycle() }
    }
    return out.asImageBitmap()
}
