package com.example.roro.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.roro.core.Expression
import com.example.roro.core.Items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 캐릭터·아이템 렌더 매핑 테이블 (가이드 8.3).
 * 렌더는 tools/closet3d/build_closet.py가 같은 카메라로 만든다 (assets/kagom, 614×965):
 *  - base_<P0n>_<표정>.webp  스킨 × 표정 7종 기본 캐릭터
 *  - item_<ID>.webp          착용 레이어 (기본 위에 그대로 겹치면 맞는다)
 *  - thumb_<ID>.webp         아이템·칭호 썸네일 (320×320)
 *  - turn/b_<P0n>_<단>_<nn>.webp, turn/i_<ID>_<단>_<nn>.webp  돌려보기 프레임 ([TURN_ROWS]고도 × [TURN_FRAMES]방향, 480×754)
 * base·item은 돌려보기 0단 [HOME_YAW]번 프레임과 같은 카메라라, 홈에서 밀기 시작하면 그대로 돌려보기로 이어진다.
 */
object KagomRenders {
    const val TURN_FRAMES = 24
    /** 고도 단: 0 = 약 9°(거의 정면 높이), 1 = 25°, 2 = 40° (위에서 내려다보기) */
    const val TURN_ROWS = 3
    /** 홈·보상 화면의 방향 = 돌려보기 0단 이 번호 프레임 (build_closet.py HOME_YAW와 같아야 한다) */
    const val HOME_YAW = 1

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
fun AssetImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current
    val image = remember(path) { mutableStateOf(KagomRenders.peek(path)) }
    LaunchedEffect(path) {
        if (image.value == null) image.value = withContext(Dispatchers.IO) { KagomRenders.load(context, path) }
    }
    image.value?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = modifier) }
}

/** 착용 레이어 그리는 순서: 하의 → 상의 → 머리 (바깥 옷이 위) */
fun wornLayers(head: String?, top: String?, bottom: String?): List<String> = listOfNotNull(bottom, top, head)
    .filter { Items.byId(it) != null }

/**
 * 터치 반응 RAND_ACT_1~3. 몸과 옷을 겹친 그림을 통째로 움직여 옷이 어긋나지 않는다.
 * 기준점은 발끝([FEET])이라 찌그러지고 늘어나도 바닥에 붙어 있다.
 * 애니메이션 원칙을 작게 적용: 예비 동작 → 동작 → 여운, 찌그러질 때 부피 유지(가로가 늘면 세로가 준다).
 */
enum class TouchAct {
    /** 통통: 납작해졌다 길어지며 두 번 튕긴다 */
    SQUISH,
    /** 오뚝이: 발끝을 축으로 좌우로 흔들리다 선다 */
    WOBBLE,
    /** 깡충: 웅크렸다 살짝 뛰어올라 착지 반동 (예전 옆돌기보다 약한 동작) */
    HOP,
}

/** 렌더에서 발끝 위치 (돌려보기 카메라 0단 기준, 곰 발바닥 ≈ 높이의 95%) */
private val FEET = TransformOrigin(0.5f, 0.95f)

/** 터치 반응 값: 가로·세로 배율, 기울기(도), 떠오름(dp, 위가 -) */
private class TouchMotion {
    val sx = Animatable(1f)
    val sy = Animatable(1f)
    val tilt = Animatable(0f)
    val lift = Animatable(0f)

    suspend fun reset() { sx.snapTo(1f); sy.snapTo(1f); tilt.snapTo(0f); lift.snapTo(0f) }

    suspend fun play(act: TouchAct) = coroutineScope {
        reset()
        when (act) {
            TouchAct.SQUISH -> {
                launch { sx.animateTo(1f, keyframes { durationMillis = 560; 1.07f at 90; 0.96f at 220; 1.03f at 340; 0.99f at 450 }) }
                sy.animateTo(1f, keyframes { durationMillis = 560; 0.92f at 90; 1.05f at 220; 0.97f at 340; 1.01f at 450 })
            }
            TouchAct.WOBBLE -> {
                launch { sy.animateTo(1f, keyframes { durationMillis = 300; 0.97f at 110 }) }
                tilt.animateTo(0f, keyframes { durationMillis = 820; -7f at 110; 6f at 270; -4f at 430; 2.5f at 580; -1f at 700 })
            }
            TouchAct.HOP -> {
                launch { sx.animateTo(1f, keyframes { durationMillis = 680; 1.06f at 110; 0.96f at 260; 1f at 400; 1.06f at 500; 0.99f at 590 }) }
                launch { sy.animateTo(1f, keyframes { durationMillis = 680; 0.9f at 110; 1.06f at 260; 1.02f at 400; 0.92f at 500; 1.02f at 590 }) }
                launch { tilt.animateTo(0f, keyframes { durationMillis = 560; 4f at 250; -2f at 420 }) }
                lift.animateTo(0f, keyframes {
                    durationMillis = 460
                    0f at 110 using LinearOutSlowInEasing      // 솟을 때는 점점 느리게
                    -34f at 300 using FastOutLinearInEasing    // 떨어질 때는 점점 빠르게
                })
            }
        }
    }
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
    /** null이면 터치 반응 없음 (주행 중 등) */
    onTouch: (() -> Unit)? = null,
    /** true면 좌우·위아래로 밀어 돌려 볼 수 있고, 손을 떼면 잠시 뒤 제자리로 돌아온다 (홈, 기어 P + 대기 표정) */
    turnable: Boolean = false,
    /** true면 가만히 있을 때도 숨쉬고(대기 표정이면 눈도 깜빡인다). 주행 중에는 끈다 (홈, 기어 P) */
    alive: Boolean = false,
) {
    val context = LocalContext.current
    val motion = remember { TouchMotion() }
    var motionJob by remember { mutableStateOf<Job?>(null) }
    var lastAct by remember { mutableStateOf<TouchAct?>(null) }
    val scope = rememberCoroutineScope()

    // 숨쉬기: 3.2초에 한 번 세로로 1.2% 부풀었다 돌아온다 (가로는 반만큼 줄어 부피 유지)
    val breath = remember { Animatable(0f) }
    LaunchedEffect(alive) {
        if (!alive) { breath.animateTo(0f, tween(300)); return@LaunchedEffect }
        while (true) {
            breath.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
            breath.animateTo(0f, tween(1600, easing = FastOutSlowInEasing))
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
    LaunchedEffect(turn.dragging) { if (turn.dragging) warm = true }
    LaunchedEffect(turnable) { if (!turnable) turn.reset(300) }
    LaunchedEffect(warm, turn.atRest) {
        if (warm && turn.atRest) { delay(20_000); frames.clear(); warm = false }
    }
    val turned = if (turn.atRest) null else frames.nearest(turn.row, turn.frame)

    /** 바로 전과 다른 반응을 고른다. 반응 중에 또 누르면 새 반응으로 바로 바꾼다 */
    fun react() {
        val act = TouchAct.entries.filter { it != lastAct }.random()
        lastAct = act
        motionJob?.cancel()
        motionJob = scope.launch { motion.play(act) }
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
                    scaleX = motion.sx.value * (1f - 0.006f * b)
                    scaleY = motion.sy.value * (1f + 0.012f * b)
                    rotationZ = motion.tilt.value
                    translationY = motion.lift.value.dp.toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            // 돌리는 중에는 돌려보기 프레임. 기본 렌더는 숨겨만 둔다 (제자리로 오면 바로 다시 보이게)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (turned != null) 0f else 1f }) {
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
            if (turned != null) Image(turned, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
    LaunchedEffect(expression) { motionJob?.cancel(); motion.reset() }
}
