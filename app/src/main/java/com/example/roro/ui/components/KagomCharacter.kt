package com.example.roro.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.roro.core.Expression
import com.example.roro.core.Items
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 캐릭터·아이템 렌더 매핑 테이블 (가이드 8.3).
 * 렌더는 tools/closet3d/build_closet.py가 같은 카메라로 만든다 (assets/kagom, 614×965):
 *  - base_<P0n>_<표정>.webp  스킨 × 표정 7종 기본 캐릭터
 *  - item_<ID>.webp          착용 레이어 (기본 위에 그대로 겹치면 맞는다)
 *  - thumb_<ID>.webp         아이템·칭호 썸네일 (320×320)
 *  - turn/b_<P0n>_<단>_<nn>.webp, turn/i_<ID>_<단>_<nn>.webp  돌려보기 프레임 ([TURN_ROWS]고도 × [TURN_FRAMES]방향, 480×754)
 *  - motion/<동작>/b_<P0n>_<nn>.webp, motion/<동작>/i_<ID>_<nn>.webp  터치 반응 동작 프레임 (24 fps, [MOTION_W]×[MOTION_H])
 * base·item은 돌려보기 0단 [HOME_YAW]번 프레임과 같은 카메라라, 홈에서 밀기 시작하면 그대로 돌려보기로 이어진다.
 * 동작 프레임도 같은 카메라인데 팔·점프가 잘리지 않게 화면만 넓혔다: 가운데 ([MOTION_X], [MOTION_Y])부터 480×754가 돌려보기 프레임과 겹친다.
 */
object KagomRenders {
    /** 7.5° 간격 (예전 24장 = 지금 짝수 번호) */
    const val TURN_FRAMES = 48
    /** 고도 단: 0 = 약 9°(거의 정면 높이), 1 = 25°, 2 = 40° (위에서 내려다보기) */
    const val TURN_ROWS = 3
    /** 홈·보상 화면의 방향 = 돌려보기 0단 이 번호 프레임 (build_closet.py HOME_YAW와 같아야 한다) */
    const val HOME_YAW = 2
    const val TURN_W = 480
    const val TURN_H = 754
    /** 동작 프레임 크기와, 그 안에서 돌려보기 프레임(480×754)이 놓인 자리 (build_closet.py MW, MH) */
    const val MOTION_W = 640
    const val MOTION_H = 830
    const val MOTION_X = 80
    const val MOTION_Y = 76
    const val MOTION_FPS = 24

    /** Expression → 렌더 파일 키 */
    fun key(e: Expression) = when (e) {
        Expression.IDLE -> "idle"
        Expression.TIRED -> "tired"
        Expression.HUNGRY_1 -> "hungry1"
        Expression.HUNGRY_2 -> "hungry2"
        Expression.HUNGRY_3 -> "hungry3"
        Expression.STAIN -> "stain"
        Expression.SICK -> "sick"
    }

    fun basePath(skinCode: String, expression: Expression) = "kagom/base_${skinCode}_${key(expression)}.webp"
    /** 대기 표정에서 눈만 감은 렌더 (깜빡임용) */
    fun blinkPath(skinCode: String) = "kagom/base_${skinCode}_blink.webp"
    fun layerPath(itemId: String) = "kagom/item_$itemId.webp"
    fun thumbPath(itemId: String) = "kagom/thumb_$itemId.webp"
    fun turnBase(skinCode: String, row: Int, i: Int) = "kagom/turn/b_${skinCode}_${row}_%02d.webp".format(i)
    fun turnLayer(itemId: String, row: Int, i: Int) = "kagom/turn/i_${itemId}_${row}_%02d.webp".format(i)
    fun motionBase(act: TouchAct, skinCode: String, i: Int) = "kagom/motion/${act.key}/b_${skinCode}_%02d.webp".format(i)
    fun motionLayer(act: TouchAct, itemId: String, i: Int) = "kagom/motion/${act.key}/i_${itemId}_%02d.webp".format(i)

    /** 디코딩한 이미지 캐시 (최대 약 40 MB) */
    private val cache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val listed = HashMap<String, Set<String>>()

    fun exists(context: Context, path: String): Boolean {
        val dir = path.substringBeforeLast('/')
        val all = synchronized(listed) {
            listed.getOrPut(dir) { runCatching { context.assets.list(dir)?.map { "$dir/$it" }?.toSet() }.getOrNull().orEmpty() }
        }
        return path in all
    }

    /** 캐시에 있으면 바로 (UI 스레드에서 디코딩하지 않음) */
    fun peek(path: String): ImageBitmap? = cache.get(path)

    fun load(context: Context, path: String): ImageBitmap? = cache.get(path) ?: runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()?.also { cache.put(path, it) }

    /** 표정 렌더가 없으면 대기 표정으로 */
    fun baseOrIdle(context: Context, skinCode: String, expression: Expression): String =
        basePath(skinCode, expression).takeIf { exists(context, it) } ?: basePath(skinCode, Expression.IDLE)
}

/**
 * assets 이미지 (없으면 아무것도 그리지 않는다).
 * 상태를 [path]마다 새로 만든다. produceState는 key가 바뀌어도 이전 값을 그대로 들고 있어서,
 * 같은 자리에서 path만 바뀌면(옷장 탭 전환 등) 다른 아이템 이미지가 남아 있었다.
 */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, colorFilter: androidx.compose.ui.graphics.ColorFilter? = null) {
    val context = LocalContext.current
    val image = remember(path) { mutableStateOf(KagomRenders.peek(path)) }
    LaunchedEffect(path) {
        if (image.value == null) image.value = withContext(Dispatchers.IO) { KagomRenders.load(context, path) }
    }
    image.value?.let { Image(it, contentDescription = null, contentScale = contentScale, colorFilter = colorFilter, modifier = modifier) }
}

/** 착용 레이어 그리는 순서: 하의 → 상의 → 머리 (바깥 옷이 위) */
fun wornLayers(head: String?, top: String?, bottom: String?): List<String> = listOfNotNull(bottom, top, head)
    .filter { Items.byId(it) != null }

/**
 * 터치 반응 RAND_ACT_1~3: 곰 뼈대로 움직인 3D 동작을 프레임으로 렌더해 둔 것 (tools/closet3d/build_closet.py MOTIONS).
 * 옷도 뼈를 따라 움직이게 렌더해서 착용 레이어를 겹치면 맞는다. 첫·끝 프레임은 기본 자세라 기본 렌더와 이어진다.
 */
enum class TouchAct(val key: String, val frames: Int, val label: String) {
    /** 손 흔들기: 살짝 움츠렸다가 왼팔을 돌려 올려 세 번 흔들고 고개를 갸웃 (1.75초) */
    WAVE("wave", 42, "손 흔들기"),
    /** 만세: 움츠렸다가 두 팔을 번쩍 들며 깡충 뛰고, 내려앉으며 몸이 눌렸다 돌아온다 (1.25초) */
    CHEER("cheer", 30, "만세"),
    /** 발 동동: 왼발·오른발을 번갈아 여섯 번 구르고 몸·고개·팔이 같이 흔들린다 (1.5초) */
    STOMP("stomp", 36, "발 동동"),
    /** 꾸벅 인사: 살짝 젖혔다가 허리를 숙이고 잠깐 멈춘 뒤 일어선다 (1.4초) */
    BOW("bow", 34, "꾸벅 인사"),
    /** 빙글 돌기: 반대로 살짝 감았다가 제자리에서 한 바퀴 돈다 (1.3초) */
    SPIN("spin", 32, "빙글 돌기"),
}

/** 렌더에서 발끝 위치 (돌려보기 카메라 0단 기준, 곰 발바닥 ≈ 높이의 95%). 숨쉬기 기준점 */
private val FEET = TransformOrigin(0.5f, 0.95f)

/**
 * 표정별 가만히 있을 때의 움직임 (spec 6장 kagomMainCanvas: 이상 상태는 표정에 맞는 애니메이션을 되풀이, 단계가 높을수록 강하게).
 * 표정 렌더는 한 장이라 몸과 옷을 겹친 그림을 통째로 움직인다 (숨쉬기와 같은 방식, 기준점 발끝). 수치는 가정이다.
 */
private class IdleMotion(
    /** 숨 한 번 (ms)과 세로로 부푸는 비율 */
    val breathMs: Int = 3200,
    val breathAmp: Float = 0.012f,
    /** 몸짓 한 번: 0에서 시작해 ±1 사이를 오가다 0으로 끝난다. null이면 숨만 쉰다 */
    val gesture: KeyframesSpec<Float>? = null,
    /** 몸짓 사이에 쉬는 시간 (ms, 이 사이에서 고른다) */
    val restMs: LongRange = 0L..1L,
    /** 몸짓 값이 1일 때: 옆으로 밀리는 거리, 기우는 각도, 납작해지는 비율(가로는 그만큼 늘어난다) */
    val shift: Dp = 0.dp,
    val tiltDeg: Float = 0f,
    val squash: Float = 0f,
    /** 눌렀을 때 바로 한 번 하는 몸짓 (이상 상태의 터치 반응). null이면 [gesture]를 쓴다 */
    val touch: KeyframesSpec<Float>? = null,
)

/** 배가 꼬르륵: 짧게 출렁이다 잦아든다 */
private val RUMBLE = keyframes<Float> { durationMillis = 520; 1f at 70; -0.8f at 170; 0.6f at 270; -0.35f at 370; 0f at 520 }
/** 오들오들: 좌우로 잘게 떨다 잦아든다 */
private val SHIVER = keyframes<Float> {
    durationMillis = 640
    1f at 50; -1f at 110; 0.9f at 170; -0.9f at 230; 0.7f at 290; -0.7f at 350; 0.4f at 420; -0.3f at 500; 0f at 640
}
/** 꾸벅: 천천히 기울다가 잠깐 멈추고 화들짝 돌아온다 */
private val NOD = keyframes<Float> { durationMillis = 2100; 0f at 0 using FastOutSlowInEasing; 1f at 1200; 1f at 1600; -0.15f at 1850; 0f at 2100 }
/** 꾸벅했다 화들짝: [NOD]를 눌렀을 때 바로 보이게 줄인 것 */
private val DOZE = keyframes<Float> { durationMillis = 900; 0f at 0 using FastOutSlowInEasing; 1f at 350; 1f at 500; -0.15f at 700; 0f at 900 }
/** 갸우뚱: 좌우로 한 번씩 기울다 잦아든다 */
private val WOBBLE = keyframes<Float> { durationMillis = 700; 1f at 140; -0.7f at 320; 0.35f at 500; 0f at 700 }

private val IDLE_PLAIN = IdleMotion()
private val IDLE_TIRED = IdleMotion(breathMs = 4600, breathAmp = 0.018f, gesture = NOD, restMs = 2600L..5200L, tiltDeg = 2.4f, squash = 0.012f, touch = DOZE)
/** 얼룩은 가만히 있을 때 몸짓이 없고 눌렀을 때만 갸우뚱한다 */
private val IDLE_STAIN = IdleMotion(tiltDeg = 1.5f, touch = WOBBLE)
private val IDLE_HUNGRY_1 = IdleMotion(gesture = RUMBLE, restMs = 4200L..6400L, squash = 0.012f)
private val IDLE_HUNGRY_2 = IdleMotion(gesture = RUMBLE, restMs = 3000L..4600L, squash = 0.017f)
private val IDLE_HUNGRY_3 = IdleMotion(breathMs = 3800, breathAmp = 0.009f, gesture = RUMBLE, restMs = 2000L..3200L, squash = 0.022f)
private val IDLE_SICK = IdleMotion(breathMs = 3600, breathAmp = 0.009f, gesture = SHIVER, restMs = 2200L..3600L, shift = 2.5.dp)

/** 눌렀을 때의 몸짓은 가만히 있을 때의 이 배 크기로 (배고픔 1의 1.2%는 눌러도 티가 안 난다) */
private const val TOUCH_GESTURE_SCALE = 2f

private fun idleMotion(expression: Expression) = when (expression) {
    Expression.IDLE -> IDLE_PLAIN
    Expression.STAIN -> IDLE_STAIN
    Expression.TIRED -> IDLE_TIRED
    Expression.HUNGRY_1 -> IDLE_HUNGRY_1
    Expression.HUNGRY_2 -> IDLE_HUNGRY_2
    Expression.HUNGRY_3 -> IDLE_HUNGRY_3
    Expression.SICK -> IDLE_SICK
}

/** 동작 프레임 한 장 합성: 몸 위에 착용 레이어를 차례로 (없으면 null) */
private fun composeMotion(context: Context, act: TouchAct, skinCode: String, worn: List<String>, i: Int): ImageBitmap? {
    fun decode(path: String, mutable: Boolean = false) = runCatching {
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inMutable = mutable }) }
    }.getOrNull()
    // 몸은 처음부터 고칠 수 있는 비트맵으로 읽어 그 위에 바로 옷을 그린다 (복사 한 번을 아낀다)
    val out = decode(KagomRenders.motionBase(act, skinCode, i), mutable = worn.isNotEmpty()) ?: return null
    if (worn.isNotEmpty()) {
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        worn.forEach { id -> decode(KagomRenders.motionLayer(act, id, i))?.let { canvas.drawBitmap(it, 0f, 0f, paint); it.recycle() } }
    }
    return out.asImageBitmap()
}

/** 탭하기 전에 미리 합성해 두는 앞부분 장수 (약 13 MB). 탭하면 바로 시작하고, 그동안 나머지를 합성한다 */
private const val MOTION_PRELOAD = 6

/** 다음에 보여 줄 동작의 앞 [MOTION_PRELOAD]장 (홈에 가만히 있을 때 미리 만든다) */
private class MotionHead(val act: TouchAct, val skinCode: String, val worn: List<String>, val frames: List<ImageBitmap>)

private suspend fun preloadMotion(context: Context, act: TouchAct, skinCode: String, worn: List<String>): MotionHead =
    withContext(Dispatchers.Default) {
        MotionHead(act, skinCode, worn, (0 until minOf(MOTION_PRELOAD, act.frames)).mapNotNull { composeMotion(context, act, skinCode, worn, it) })
    }

/**
 * 동작을 24 fps로 재생한다. 앞부분은 [head]에서 바로 쓰고, 나머지는 4장까지 동시에 합성해 차례로 받는다 (한 번에 약 10장 ≈ 20 MB).
 * 시간에 맞춰 보여 주고, 합성이 늦어 제 시간을 한 장 넘게 지난 프레임은 건너뛴다 (동작 길이는 그대로). 끝나면 [show]에 null.
 */
private suspend fun playMotion(
    context: Context, act: TouchAct, skinCode: String, worn: List<String>, head: MotionHead?, show: (ImageBitmap?) -> Unit,
) = coroutineScope {
    val pre = head?.takeIf { it.act == act && it.skinCode == skinCode && it.worn == worn }?.frames.orEmpty()
    val ready = Channel<ImageBitmap?>(capacity = 4)
    val sent = AtomicInteger(0)     // 채널에 넣은 장수 (다음 프레임이 이미 와 있는지 보려고)
    launch(Dispatchers.Default) {
        pre.forEach { ready.send(it); sent.incrementAndGet() }
        val window = ArrayDeque<Deferred<ImageBitmap?>>()
        var next = pre.size
        while (next < act.frames || window.isNotEmpty()) {
            while (next < act.frames && window.size < 4) {
                val i = next++
                window.addLast(async { composeMotion(context, act, skinCode, worn, i) })
            }
            ready.send(window.removeFirst().await())
            sent.incrementAndGet()
        }
        ready.close()
    }
    // 시계는 실제 시간: 화면 갱신(vsync)을 기다리며 재면 기다린 시간이 쌓여 모든 프레임이 늦은 것으로 보였다
    val frameNanos = 1_000_000_000L / KagomRenders.MOTION_FPS
    var start = -1L
    var index = 0
    try {
        for (bmp in ready) {
            if (bmp == null) break                         // 렌더가 없으면 그만 (기본 렌더 그대로)
            val now = System.nanoTime()
            if (start < 0) start = now
            val due = start + index * frameNanos
            index++
            // 늦었고 다음 프레임이 이미 와 있으면 건너뛴다. 다음 것이 아직이면 늦어도 보여 줘서 멈춰 보이지 않게 (마지막 = 기본 자세는 꼭 보인다)
            if (now > due + frameNanos && index < act.frames && sent.get() > index) continue
            show(bmp)
            val wait = due + frameNanos - System.nanoTime()
            if (wait > 0) delay(wait / 1_000_000)
        }
    } finally {
        show(null)
        ready.cancel()
    }
}

/** 동작 프레임을 기본 렌더와 같은 자리에 그린다 (가운데 480×754가 Fit으로 놓인 기본·돌려보기 그림과 겹치고, 넓힌 부분은 박스 밖으로 나간다) */
private fun DrawScope.drawMotionFrame(image: ImageBitmap) {
    val s = minOf(size.width / KagomRenders.TURN_W, size.height / KagomRenders.TURN_H)
    val left = (size.width - KagomRenders.TURN_W * s) / 2 - KagomRenders.MOTION_X * s
    val top = (size.height - KagomRenders.TURN_H * s) / 2 - KagomRenders.MOTION_Y * s
    drawImage(
        image,
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize((KagomRenders.MOTION_W * s).roundToInt(), (KagomRenders.MOTION_H * s).roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

/**
 * kagomCharacter (Skin=P01~P08). 렌더를 FIT으로 세로 가운데에 그린다. [worn] = 착용 아이템 ID (wornLayers 순서)
 * 가이드 v1.2: 상태 텍스트 라벨(대기/아픔/배고픔 …)은 없다. 상태는 표정·움직임으로만 전하고, 화면 읽기용 설명에만 남긴다.
 */
@Composable
fun KagomCharacter(
    skinCode: String,
    expression: Expression,
    modifier: Modifier = Modifier,
    worn: List<String> = emptyList(),
    /** null이면 터치 반응 없음 (주행 중 등). 대기 표정이면 3D 동작, 이상 상태면 그 표정의 몸짓으로 답한다 */
    onTouch: (() -> Unit)? = null,
    /** true면 좌우·위아래로 밀어 돌려 볼 수 있고, 손을 떼면 잠시 뒤 제자리로 돌아온다 (홈, 기어 P + 대기 표정) */
    turnable: Boolean = false,
    /** true면 가만히 있을 때도 숨쉬고(대기 표정이면 눈도 깜빡인다). 주행 중에는 끈다 (홈, 기어 P) */
    alive: Boolean = false,
    /** 시연 패널에서 고른 동작 (TouchAct.key). 3D 동작을 틀 수 있을 때(기어 P + 대기 표정) 그 동작을 바로 한 번 틀고 [onDemoActShown]을 부른다 */
    demoAct: String? = null,
    onDemoActShown: () -> Unit = {},
) {
    val context = LocalContext.current
    var motionJob by remember { mutableStateOf<Job?>(null) }
    var motionFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    // 숨쉬기: 대기 표정은 3.2초에 한 번 세로로 1.2% 부풀었다 돌아온다 (가로는 반만큼 줄어 부피 유지). 표정마다 빠르기·깊이가 다르다
    val idle = idleMotion(expression)
    val breath = remember { Animatable(0f) }
    LaunchedEffect(alive, idle) {
        if (!alive) { breath.animateTo(0f, tween(300)); return@LaunchedEffect }
        while (true) {
            breath.animateTo(1f, tween(idle.breathMs / 2, easing = FastOutSlowInEasing))
            breath.animateTo(0f, tween(idle.breathMs / 2, easing = FastOutSlowInEasing))
        }
    }
    // 표정별 몸짓: 배고픔 = 배가 꼬르륵, 아픔 = 오들오들, 피곤함 = 꾸벅 (쉬었다가 되풀이)
    val gesture = remember { Animatable(0f) }
    // 이상 상태에서 누르면 그 표정의 몸짓을 바로 한 번, 평소보다 크게 한다 (누른 것이 보이게. 배율은 가정).
    // 3D 동작 프레임은 대기 얼굴뿐이라 이상 상태에 틀면 동작하는 동안 표정·소품이 사라진다
    var poke by remember { mutableIntStateOf(0) }
    val poked = remember { intArrayOf(0) }
    var gestureScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(alive, idle, poke) {
        gesture.snapTo(0f)
        gestureScale = 1f
        if (!alive) return@LaunchedEffect
        val touch = idle.touch ?: idle.gesture
        val fresh = poked[0] != poke
        poked[0] = poke
        if (fresh && touch != null) {
            gestureScale = TOUCH_GESTURE_SCALE
            gesture.animateTo(0f, touch)
            gestureScale = 1f
        }
        val once = idle.gesture ?: return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(idle.restMs.first, idle.restMs.last))
            gesture.animateTo(0f, once)
        }
    }
    // 눈 깜빡임: 대기 표정일 때 2.6~5.2초마다 0.12초, 네 번에 한 번은 두 번 연속
    val blinkPath = KagomRenders.blinkPath(skinCode)
    val canBlink = alive && expression == Expression.IDLE && KagomRenders.exists(context, blinkPath)
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(canBlink) {
        blinking = false
        if (!canBlink) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2600, 5200))
            blinking = true; delay(120); blinking = false
            if (Random.nextInt(4) == 0) { delay(160); blinking = true; delay(110); blinking = false }
        }
    }

    // 돌려보기: 처음 밀 때부터 프레임을 만들고, 한동안 안 돌리면 내려 둔다 (홈에 늘 수십 MB를 들고 있지 않게)
    val turn = remember { TurnState(KagomRenders.HOME_YAW) }
    var warm by remember { mutableStateOf(false) }
    val frames = rememberTurnFrames(skinCode, worn, turn, active = warm)
    LaunchedEffect(turn.dragging) {
        if (turn.dragging) { warm = true; motionJob?.cancel() }   // 돌리기 시작하면 터치 반응은 멈춘다
    }
    LaunchedEffect(turnable) { if (!turnable) turn.reset(300) }
    LaunchedEffect(warm, turn.atRest) {
        if (warm && turn.atRest) { delay(20_000); frames.clear(); warm = false }
    }
    val turning = !turn.atRest && frames.hasAny

    // 다음 반응은 미리 정해 두고, 3D 동작을 틀 수 있는 동안(홈, 기어 P + 대기 표정) 앞부분을 합성해 둔다 → 탭하면 바로 움직인다
    val canAct = onTouch != null && expression == Expression.IDLE
    var nextAct by remember { mutableStateOf(TouchAct.entries.random()) }
    var head by remember { mutableStateOf<MotionHead?>(null) }
    LaunchedEffect(canAct, nextAct, skinCode, worn) {
        head = null
        if (canAct) head = preloadMotion(context.applicationContext, nextAct, skinCode, worn)
    }

    /** 대기 표정: 바로 전과 다른 3D 동작을 고른다 (반응 중에 또 누르면 새 반응으로 바로 바꾼다). 이상 상태: 표정 몸짓 */
    fun react(chosen: TouchAct? = null) {
        if (!canAct) { poke++; return }
        val act = chosen ?: nextAct
        val ready = head      // 미리 만든 앞부분은 같은 동작일 때만 쓰인다 (playMotion)
        nextAct = TouchAct.entries.filter { it != act }.random()
        motionJob?.cancel()
        motionJob = scope.launch {
            // 돌려 둔 채로 누르면 정면으로 빨리 돌려놓고 시작한다: 동작 프레임은 정면(HOME_YAW)뿐이라 돌아간 그림 위에 겹쳐 잔상처럼 보였다.
            // 제자리로 돌아오려고 기다리던 settleJob은 끈다 (동작 중에 다시 돌려보기 그림이 올라오지 않게). 0.2초는 가정
            if (!turn.atRest) {
                turn.settleJob?.cancel()
                turn.reset(200)
            }
            playMotion(context.applicationContext, act, skinCode, worn, ready) { motionFrame = it }
        }
    }
    val playing = motionFrame != null
    LaunchedEffect(demoAct, canAct) {
        val act = TouchAct.entries.firstOrNull { it.key == demoAct } ?: return@LaunchedEffect
        if (canAct) { react(act); onDemoActShown() }
    }

    val names = worn.mapNotNull { Items.byId(it)?.name }
    Box(
        modifier.semantics {
            contentDescription = "카곰 ${expression.label}" + (if (names.isEmpty()) "" else ", ${names.joinToString()} 착용") +
                if (turnable) ", 밀어서 돌려 볼 수 있어요" else ""
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .turnGestures(turn, scope, turnable, springBack = true)
                .then(
                    if (onTouch != null) Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                        onTouch(); react()
                    } else Modifier,
                )
                .graphicsLayer {
                    transformOrigin = FEET
                    val b = breath.value
                    val g = gesture.value * gestureScale
                    scaleX = 1f - idle.breathAmp / 2 * b + idle.squash * g
                    scaleY = 1f + idle.breathAmp * b - idle.squash * g
                    translationX = idle.shift.toPx() * g
                    rotationZ = idle.tiltDeg * g
                },
            contentAlignment = Alignment.Center,
        ) {
            // 돌리는 중에는 돌려보기 프레임, 터치 반응 중에는 동작 프레임. 기본 렌더는 숨겨만 둔다 (끝나면 바로 다시 보이게)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (turning || playing) 0f else 1f }) {
                // 표정이 바뀌면 살짝 교차 페이드
                Crossfade(KagomRenders.baseOrIdle(context, skinCode, expression), Modifier.fillMaxSize(), tween(220), label = "expr") { path ->
                    AssetImage(path, Modifier.fillMaxSize())
                }
                // 감은 눈 렌더를 미리 올려 두고 깜빡일 때만 보인다 (옷 레이어 아래)
                if (canBlink) AssetImage(blinkPath, Modifier.fillMaxSize().graphicsLayer { alpha = if (blinking) 1f else 0f })
                worn.forEach { id ->
                    key(id) {
                        val path = KagomRenders.layerPath(id)
                        if (KagomRenders.exists(context, path)) AssetImage(path, Modifier.fillMaxSize())
                    }
                }
            }
            // 둘을 같이 그리지 않는다 (각도가 다른 두 그림이 겹친다)
            if (turning && !playing) TurnImage(frames, turn, Modifier.fillMaxSize())
            motionFrame?.let { img -> Spacer(Modifier.fillMaxSize().drawBehind { drawMotionFrame(img) }) }
        }
    }
    // 표정이 바뀌거나 터치 반응이 막히면(주행 시작) 하던 동작도 바로 멈춘다
    LaunchedEffect(expression, onTouch != null) { motionJob?.cancel() }
}
