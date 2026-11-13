@file:OptIn(ExperimentalLayoutApi::class)

package com.example.roro.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roro.core.Checkup
import com.example.roro.core.Expression
import com.example.roro.core.Items
import com.example.roro.core.Landmarks
import com.example.roro.core.PendingPopup
import com.example.roro.core.Quests
import com.example.roro.core.Severity
import com.example.roro.core.StateEngine
import com.example.roro.core.Tiers
import com.example.roro.core.VehicleStatus
import com.example.roro.data.AppData
import com.example.roro.data.Equipped
import com.example.roro.data.Profile
import com.example.roro.ui.DrivingToast
import com.example.roro.ui.josa
import com.example.roro.ui.components.Chip
import com.example.roro.ui.components.ConfettiLayer
import com.example.roro.ui.components.GiftBoxGlyph
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.IviCheckbox
import com.example.roro.ui.components.IviSwitch
import com.example.roro.ui.components.IviToast
import com.example.roro.ui.components.AssetImage
import com.example.roro.ui.components.BackgroundArt
import com.example.roro.ui.components.KagomBackgrounds
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.KagomRenders
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.components.LandmarkGlyphView
import com.example.roro.ui.components.LandmarkPin
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.SparkleGlyph
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSkinPalette
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import com.example.roro.ui.theme.PleosKit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ───────────── POP-01 주행 중 토스트 ─────────────

/** drivingToast: gear≠P에서 달성 시 상단 3초, 버튼 없음 */
@Composable
fun DrivingToastView(t: DrivingToast, kagomName: String, modifier: Modifier = Modifier) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val text = when (t.tail) {
        DrivingToast.TailKind.GIFT ->
            if (split) "${t.headline} 정차하면 선물을 열어줘요" else "${t.headline} 정차하면 ${kagomName.josa("이", "가")} 선물을 열어줘요 🎁"
        DrivingToast.TailKind.FOG ->
            if (split) "${t.headline} 정차하면 안개가 걷혀요" else "${t.headline} 정차하면 안개가 걷히고 보상이 열려요"
    }
    IviToast(text, modifier.then(if (split) Modifier.width(560.dp) else Modifier))
}

// ───────────── 모달 공통 ─────────────

/**
 * 중앙 모달 (Full 880 / Split 600) + 딤 + 컨페티.
 * [enter]: 딤이 깔리고 카드가 살짝 아래에서 튀어 오르며 나타난다 (spring, 끝에 조금 넘쳤다 돌아옴).
 * [flash]: hero에 번진 흰 빛이 걷히며 시작한다 (상자를 연 직후 공개 화면).
 */
@Composable
private fun ModalScaffold(
    heroHeight: Dp,
    heroChip: String,
    enter: Boolean = true,
    flash: Boolean = false,
    actions: @Composable () -> Unit,
    hero: @Composable BoxScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val appear = remember { Animatable(if (enter) 0f else 1f) }
    val glow = remember { Animatable(if (flash) 1f else 0f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
        glow.animateTo(0f, tween(Motion.FLASH_OUT_MS, easing = FastOutSlowInEasing))
    }
    BackHandler {}
    BoxWithConstraints(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(KagomColor.Overlay.dim).clickable(remember0(), null) {},
        contentAlignment = Alignment.Center,
    ) {
        ConfettiLayer()
        val shape = RoundedCornerShape(if (split) KagomRadius.r28 else KagomRadius.r32)
        Column(
            Modifier.width(if (split) 600.dp else 880.dp)
                .heightIn(max = maxHeight - 48.dp)
                .graphicsLayer {
                    val t = appear.value
                    scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t
                    translationY = (1f - t) * 48.dp.toPx()
                }
                .shadow(24.dp, shape, ambientColor = PleosKit.shadow, spotColor = PleosKit.shadow)
                .clip(shape).background(KagomColor.Surface.card),
        ) {
            Box(Modifier.fillMaxWidth().height(heroHeight).clipToBounds()) {
                hero()
                // Split은 hero를 600/880으로 줄여 그리지만 칩 글자는 줄이지 않는다 (Pleos 최소 글자 20px)
                val eyebrowScale = if (split) 600f / 880f else 1f
                Chip(heroChip, KagomColor.Accent.tier, KagomColor.Severity.fg(Severity.RECOMMEND),
                    Modifier.padding(start = (32f * eyebrowScale).dp, top = (28f * eyebrowScale).dp))
                if (flash) Box(Modifier.fillMaxSize().graphicsLayer { alpha = glow.value }.background(KagomColor.Overlay.flash))
            }
            // 승인된 높이 부족 정책: 본문만 스크롤하고 선택 버튼은 항상 보이게 둔다.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(start = if (split) 24.dp else 40.dp, end = if (split) 24.dp else 40.dp,
                    top = if (split) 24.dp else 28.dp), content = body)
            Column(Modifier.padding(horizontal = if (split) 24.dp else 40.dp)
                .padding(top = if (split) 14.dp else 16.dp, bottom = if (split) 24.dp else 28.dp)) { actions() }
        }
    }
}

/** 보상 연출 시간 (ms). 모두 정차 중(기어 P)에만 뜨는 모달이다 */
private object Motion {
    const val OPEN_MS = 560          // 상자 흔들림 → 부풀며 빛
    const val FLASH_OUT_MS = 520     // 공개 화면의 흰 빛이 걷히는 시간
    const val POP_DELAY_MS = 160L    // 빛이 걷히기 시작하고 아이템이 튀어나오기까지
    const val EXP_DELAY_MS = 520L    // EXP 바가 차오르기 시작하기까지
    const val EXP_FILL_MS = 900
    const val FOG_DELAY_MS = 380L    // POP-03: 모달이 자리 잡고 안개가 걷히기 시작하기까지
    const val FOG_MS = 1100          // 안개가 핀에서부터 걷히는 시간
    const val STAMP_DELAY_MS = 1150L // 안개가 거의 걷혔을 때 방문 도장이 찍힌다
}

/** 언박싱 상자: 가만히 있을 때 2초마다 들썩 흔들리고, [open](0→1) 동안 크게 흔들리다 부풀며 사라진다 */
@Composable
private fun WobblingGift(modifier: Modifier, open: Float) {
    val wobble = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1400)
            wobble.animateTo(0f, keyframes { durationMillis = 600; -7f at 120; 6f at 270; -3f at 420 })
        }
    }
    val bob by rememberInfiniteTransition(label = "gift").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
    )
    GiftBoxGlyph(modifier.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)
        val rest = 1f - open
        val shake = sin(open * 7f * PI.toFloat()) * 12f * rest
        rotationZ = wobble.value * rest + shake
        val s = 1f + 0.03f * bob * rest + 0.3f * open * open
        scaleX = s; scaleY = s
        translationY = -6.dp.toPx() * bob * rest
        alpha = 1f - open * open * open
    })
}

@Composable private fun remember0() = remember { MutableInteractionSource() }

/**
 * 아이템 쇼케이스: 점선 원 + 흰 카드 + 아이템 썸네일 (없으면 Mark 글자).
 * 카드가 튀어나오듯 커지고(spring, 조금 넘쳤다 돌아옴), 점선 원은 천천히 돌며 안쪽 빛이 숨쉰다.
 */
@Composable
private fun ItemShowcase(mark: String, size: Dp, modifier: Modifier = Modifier, itemId: String? = null) {
    val context = LocalContext.current
    val thumb = itemId?.let { KagomRenders.thumbPath(it) }?.takeIf { KagomRenders.exists(context, it) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(Motion.POP_DELAY_MS); pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val loop = rememberInfiniteTransition(label = "showcase")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val glow by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    Box(modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            rotationZ = spin
            val s = 0.7f + 0.3f * pop.value.coerceAtMost(1f); scaleX = s; scaleY = s
        }) {
            drawCircle(KagomColor.Surface.card.copy(alpha = 0.25f + 0.2f * glow), radius = this.size.minDimension / 2.1f)
            drawCircle(KagomColor.Surface.card, radius = this.size.minDimension * 0.39f,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
        Box(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                .size(size).shadow(8.dp, RoundedCornerShape(24.dp)).background(KagomColor.Surface.card, RoundedCornerShape(24.dp))
                .border(4.dp, KagomColor.Surface.mint, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // 배경 보상(QST-06)은 3D 썸네일이 없고 그림 자체를 카드에 채운다
            if (KagomBackgrounds.has(itemId)) BackgroundArt(itemId,
                Modifier.fillMaxSize().padding(size * 0.08f).clip(RoundedCornerShape(KagomRadius.r16)))
            else if (thumb != null) AssetImage(thumb, Modifier.fillMaxSize().padding(size * 0.08f))
            else Text(mark, style = KagomType.Display40, color = KagomColor.Text.accent)
        }
    }
}

/** 반짝이: 하나씩 엇갈려 커졌다 작아지며 반짝인다 */
@Composable
private fun Sparkles(modifier: Modifier, spots: List<Pair<Float, Float>>) {
    val loop = rememberInfiniteTransition(label = "sparkles")
    Box(modifier) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            spots.forEachIndexed { i, (x, y) ->
                val tw by loop.animateFloat(
                    0.55f, 1f,
                    infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 260)),
                    label = "twinkle$i",
                )
                SparkleGlyph(
                    Modifier.offset(maxWidth * x, maxHeight * y).size(if (i % 2 == 0) 22.dp else 16.dp)
                        .graphicsLayer { scaleX = tw; scaleY = tw; alpha = 0.4f + 0.6f * tw; rotationZ = (1f - tw) * 45f },
                )
            }
        }
    }
}

/** EXP 상승 바: 기존(진한 민트) + 이번 상승분(밝은 민트). 모달이 뜨고 잠시 뒤 상승분이 차오르며 숫자도 같이 올라간다 */
@Composable
private fun ExpGain(gain: Int, before: Int, after: Int) {
    val next = Tiers.nextThreshold(before)
    val fill = remember(before, after) { Animatable(before.toFloat()) }
    LaunchedEffect(before, after) {
        delay(Motion.EXP_DELAY_MS)
        fill.animateTo(after.toFloat(), tween(Motion.EXP_FILL_MS, easing = FastOutSlowInEasing))
    }
    val now = fill.value.roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("+$gain EXP", KagomColor.Surface.mint, KagomColor.Text.accent)
            Text(
                if (next == null) "$now EXP · 최고 레벨" else if (now >= next) "$now / $next EXP · 레벨 업!"
                else "$now / $next EXP · 다음 레벨까지 ${next - now} EXP",
                style = KagomType.Caption14, color = KagomColor.Text.secondary,
                modifier = Modifier.weight(1f).padding(start = KagomSpace.s8), textAlign = TextAlign.End,
            )
        }
        val denom = (next ?: after).coerceAtLeast(1).toFloat()
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(KagomRadius.full)).background(KagomColor.Progress.track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((fill.value / denom).coerceIn(0f, 1f)).background(KagomColor.Progress.gain, RoundedCornerShape(KagomRadius.full)))
            Box(Modifier.fillMaxHeight().fillMaxWidth((before / denom).coerceIn(0f, 1f)).background(KagomColor.Progress.fill, RoundedCornerShape(KagomRadius.full)))
        }
    }
}

/** 하단 버튼 (Full: 가로 [주 버튼 | 나중에], Split: 세로) + 각주 */
@Composable
private fun ModalActions(primary: String, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    if (split) {
        IviButton(primary, onPrimary, Modifier.fillMaxWidth(), size = IviButtonSize.Medium)
        if (secondary != null) {
            Spacer(Modifier.height(10.dp))
            IviButton(secondary, onSecondary, Modifier.fillMaxWidth(), type = IviButtonType.Primary, size = IviButtonSize.Medium)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            IviButton(primary, onPrimary, Modifier.weight(1f), size = IviButtonSize.Large)
            if (secondary != null) IviButton(secondary, onSecondary, Modifier.width(163.dp), type = IviButtonType.Primary, size = IviButtonSize.Large)
        }
    }
}

// ───────────── POP-02 보상 (언박싱 → 공개) ─────────────

@Composable
fun RewardModal(p: PendingPopup.Reward, profile: Profile, equipped: Equipped, onOpen: () -> Unit, onLater: () -> Unit, onFinish: (equip: Boolean) -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val q = Quests.byId(p.questId) ?: return
    val item = Items.byId(p.itemId) ?: return
    val palette = KagomSkinPalette.of(profile.skin)
    val heroH = if (split) 260.dp else 300.dp
    var unboxing by remember(p.key) { mutableStateOf(!p.opened) }

    if (!p.opened || unboxing) {
        // 상자 열기: 크게 흔들리다 부풀며 빛이 번진 뒤 공개 화면으로 (공개 화면은 그 빛이 걷히며 시작)
        val scope = rememberCoroutineScope()
        val opening = remember { Animatable(0f) }
        var busy by remember { mutableStateOf(false) }
        val open: () -> Unit = {
            if (!busy) {
                busy = true
                // 공개 상태를 먼저 보존해 주행 전환·Activity 재생성 중 보상이 처음으로 되돌아가지 않게 한다.
                onOpen()
                scope.launch { opening.animateTo(1f, tween(Motion.OPEN_MS, easing = LinearEasing)); unboxing = false }
            }
        }
        ModalScaffold(heroH, "보상 도착", actions = {
            ModalActions("선물 열기", open, "나중에", onLater)
        }, hero = {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.radialGradient(listOf(palette.heroGlow, palette.body), center, size.width * 0.32f)) })
            Sparkles(Modifier.fillMaxSize(), listOf(0.33f to 0.15f, 0.5f to 0.08f, 0.66f to 0.22f, 0.3f to 0.83f, 0.68f to 0.8f))
            WobblingGift(Modifier.offset(x = if (split) 225.dp else 330.dp, y = if (split) 47.73.dp else 70.dp)
                .size(if (split) 150.dp else 220.dp, if (split) 136.36.dp else 200.dp), opening.value)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = opening.value.let { it * it * it } }.background(KagomColor.Overlay.flash))
            KagomCharacter(profile.skin, Expression.IDLE, Modifier.offset(x = if (split) 436.36.dp else 640.dp,
                y = if (split) 71.45.dp else 104.8.dp)
                .size(if (split) 103.09.dp else 151.2.dp, if (split) 160.36.dp else 235.2.dp), worn = equipped.layers())
        }) {
            Text("${q.rewardKind} 보상이 도착했어요", style = if (split) KagomType.Title28 else KagomType.Title32, color = KagomColor.Text.primary)
            Spacer(Modifier.height(KagomSpace.s8))
            Text("${q.title} 달성", style = if (split) KagomType.Caption14 else KagomType.Body16, color = KagomColor.Text.secondary)
            Spacer(Modifier.height(KagomSpace.s16))
            PopupQuote("안에 뭐가 들었을까요? 같이 열어봐요!")
        }
        return
    }

    ModalScaffold(heroH, "보상 획득!", enter = false, flash = true, actions = {
        ModalActions("바로 장착하기", { onFinish(true) }, "나중에", { onFinish(false) })
    }, hero = {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.radialGradient(listOf(palette.heroGlow, palette.body),
            Offset(size.width * 0.7727f, size.height / 2), size.width * 0.29545f)) })
        KagomCharacter(profile.skin, Expression.IDLE, Modifier.offset(x = if (split) 81.82.dp else 120.dp, y = if (split) 34.09.dp else 50.dp)
            .size(if (split) 122.73.dp else 180.dp, if (split) 190.91.dp else 280.dp), worn = equipped.layers())
        Sparkles(Modifier.fillMaxSize(), listOf(0.6f to 0.2f, 0.75f to 0.08f, 0.88f to 0.18f, 0.58f to 0.72f, 0.9f to 0.75f))
        ItemShowcase(item.mark, if (split) 102.27.dp else 150.dp,
            Modifier.offset(x = if (split) 340.91.dp else 500.dp, y = if (split) 20.45.dp else 30.dp), item.id)
    }) {
        // Figma 62:3263 / 64:3893 최신본: 공개 본문은 아이템명부터 시작해 카드 높이를 줄인다.
        ItemTitleRow(item.name, listOf(item.category.label to false, "NEW" to true))
        Spacer(Modifier.height(if (split) 14.dp else 16.dp))
        ExpGain(p.exp, p.expBefore, p.expAfter)
        Spacer(Modifier.height(if (split) 14.dp else 16.dp))
        PopupQuote(item.line.ifEmpty { "멋진 보상이에요! 바로 해 볼까요?" })
    }
}

@Composable
private fun ItemTitleRow(name: String, chips: List<Pair<String, Boolean>>, limited: Boolean = false) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
        Text(name, style = if (split) KagomType.Title28 else KagomType.Title32, color = KagomColor.Text.primary, modifier = Modifier.align(Alignment.CenterVertically))
        chips.forEach { (t, isNew) ->
            if (isNew) Chip(t, KagomColor.Severity.bg(Severity.URGENT), KagomColor.Severity.fg(Severity.URGENT), Modifier.align(Alignment.CenterVertically))
            else Chip(t, KagomColor.Surface.subtle, KagomColor.Text.secondary, Modifier.align(Alignment.CenterVertically))
        }
        if (limited) Chip("랜드마크 한정", KagomColor.Severity.bg(Severity.RECOMMEND), KagomColor.Severity.fg(Severity.RECOMMEND),
            Modifier.align(Alignment.CenterVertically))
    }
}

// ───────────── POP-03 랜드마크 해금 ─────────────

@Composable
fun LandmarkUnlockModal(p: PendingPopup.Landmark, profile: Profile, equipped: Equipped, onFinish: (equip: Boolean) -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val lm = Landmarks.byId(p.landmarkId) ?: return
    val item = Items.byId(p.itemId) ?: return
    val date = LocalDate.ofEpochDay(p.day).format(DateTimeFormatter.ofPattern("yyyy. MM. dd"))
    val scale = if (split) 600f / 880f else 1f

    ModalScaffold(if (split) 260.dp else 300.dp, "보상 도착", actions = {
        ModalActions("바로 장착하기", { onFinish(true) }, "나중에", { onFinish(false) })
    }, hero = {
        // Split은 원본 지형 비율을 유지하고 남은 hero 아래쪽을 바다색으로 채운다.
        Box(Modifier.fillMaxSize().background(KagomColor.Map.sea))
        AssetImage("popups/landmark-terrain.png", Modifier.size((651.547f * scale).dp, (300f * scale).dp))
        Canvas(Modifier.fillMaxWidth().height((300f * scale).dp)) {
            val dark = KagomColor.Accent.positive.copy(alpha = 0.22f)
            drawOval(dark, Offset(size.width * 0.07f, size.height * 0.13f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.37f))
            drawOval(dark, Offset(size.width * 0.44f, size.height * 0.5f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.37f))
            drawLine(KagomColor.Surface.card, Offset(0f, size.height * 0.37f), Offset(size.width, size.height * 0.37f), 2.dp.toPx())
        }
        LandmarkGlyphView(lm.glyph, Modifier.offset(x = (420f * scale).dp, y = (20f * scale).dp).size((160f * scale).dp, (90f * scale).dp))
        // 안개 걷힘 (spec 6장 UNLOCKING "안개 걷힘(Reveal) 이펙트"): 미해금 색으로 덮였다가 핀에서부터 동그랗게 걷힌다
        val reveal = remember { Animatable(0f) }
        val stamp = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { delay(Motion.FOG_DELAY_MS); reveal.animateTo(1f, tween(Motion.FOG_MS, easing = FastOutSlowInEasing)) }
            delay(Motion.STAMP_DELAY_MS)
            stamp.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = reveal.value
            if (r < 1f) {
                // 핀(Figma 470,150 · 64) 한가운데
                val pin = Offset((502f * scale).dp.toPx(), (182f * scale).dp.toPx())
                // 핀에서 가장 먼 모서리(왼쪽 위나 아래)까지 닿으면 다 걷힌 것
                val radius = maxOf(pin.getDistance(), (pin - Offset(0f, size.height)).getDistance()) * r
                clipPath(Path().apply { addOval(Rect(pin, radius)) }, ClipOp.Difference) { drawRect(KagomColor.Map.landFogged) }
                if (r > 0f) drawCircle(KagomColor.Surface.card, radius, pin, alpha = 0.7f * (1f - r), style = Stroke(8.dp.toPx()))
            }
        }
        LandmarkPin(PinState.Unlocked, Modifier.offset(x = (470f * scale).dp, y = (150f * scale).dp).size((64f * scale).dp))
        Box(
            // Split은 도장 글자를 줄이지 않아 폭이 넓다: 핀(470)에 닿지 않게 왼쪽으로 당긴다
            Modifier.offset(x = ((if (split) 270f else 300f) * scale).dp, y = (200f * scale).dp)
                // 도장: 크게 떠 있다가 쿵 찍힌다
                .graphicsLayer { val t = stamp.value; scaleX = 1.9f - 0.9f * t; scaleY = 1.9f - 0.9f * t; alpha = t.coerceIn(0f, 1f) }
                .rotate(-10f)
                .border((2f * scale).dp, KagomColor.Accent.stamp, RoundedCornerShape(10.dp)).padding(horizontal = (12f * scale).dp, vertical = (6f * scale).dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 도장 글자도 Split에서 Pleos 최소(20px) 아래로 줄이지 않는다
                Text("${lm.name} 방문", style = if (split) KagomType.Caption14Strong else KagomType.Body18Strong, color = KagomColor.Accent.stamp)
                Text(date, style = KagomType.Caption14Strong, color = KagomColor.Accent.stamp)
            }
        }
        KagomCharacter(profile.skin, Expression.IDLE, Modifier.offset(x = if (split) 81.82.dp else 120.dp, y = if (split) 34.09.dp else 50.dp)
            .size(if (split) 122.73.dp else 180.dp, if (split) 190.91.dp else 280.dp), worn = equipped.layers())
        Sparkles(Modifier.fillMaxSize(), listOf(0.72f to 0.25f, 0.85f to 0.12f, 0.93f to 0.3f, 0.75f to 0.75f, 0.92f to 0.8f))
        ItemShowcase(item.mark, (130f * scale).dp, Modifier.offset(x = (620f * scale).dp, y = (60f * scale).dp), item.id)
    }) {
        Text("${lm.name}에 도착했어요!", style = if (split) KagomType.Body16Strong else KagomType.Body18Strong, color = KagomColor.Text.secondary)
        Spacer(Modifier.height(2.dp))
        Text("강릉 랜드마크 ${p.visitedPlaces} / ${Landmarks.all.size} 방문",
            style = if (split) KagomType.Caption14 else KagomType.Body16, color = KagomColor.Text.tertiary)
        Spacer(Modifier.height(if (split) 14.dp else 16.dp))
        ItemTitleRow(item.name, listOf(item.category.label to false, "NEW" to true), limited = true)
        Spacer(Modifier.height(if (split) 14.dp else 16.dp))
        ExpGain(p.exp, p.expBefore, p.expAfter)
        Spacer(Modifier.height(if (split) 14.dp else 16.dp))
        PopupQuote(item.line)
    }
}

// ───────────── POP-04 티어 승급 ─────────────

/** Figma 7:2542 / 64:4135: 월계관과 광선은 원본 노드에서 내보낸 에셋을 쓴다. */
@Composable
fun TierUpModal(p: PendingPopup.TierUp, profile: Profile, equipped: Equipped, onConfirm: () -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    // 다른 보상 모달(ModalScaffold)과 같은 등장: 딤이 깔리고 카드가 아래에서 튀어 오른다
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 320f)) }
    BackHandler {}
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
        .background(KagomColor.Overlay.dim).clickable(remember0(), null) {},
        contentAlignment = Alignment.Center) {
        ConfettiLayer()
        val shape = RoundedCornerShape(if (split) KagomRadius.r28 else KagomRadius.r32)
        Column(Modifier.width(if (split) 600.dp else 880.dp).heightIn(max = maxHeight - 48.dp)
            .graphicsLayer {
                val t = appear.value
                scaleX = 0.9f + 0.1f * t; scaleY = 0.9f + 0.1f * t
                translationY = (1f - t) * 48.dp.toPx()
            }
            .shadow(24.dp, shape).clip(shape)
            .background(Brush.radialGradient(listOf(KagomColor.Level.glow, KagomColor.Level.mid, KagomColor.Level.edge)))) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                val suffix = if (split) "split" else "full"
                Box(Modifier.fillMaxWidth().height(if (split) 250.dp else 330.dp)) {
                    AssetImage("popups/level-rays-$suffix.png", Modifier.align(Alignment.TopCenter)
                        .offset(y = if (split) (-117).dp else (-155).dp)
                        .wrapContentSize(Alignment.TopCenter, unbounded = true).size(if (split) 500.dp else 660.dp))
                    AssetImage("popups/level-laurel-$suffix.png", Modifier.align(Alignment.TopCenter)
                        .offset(y = if (split) 23.dp else 25.dp).size(if (split) 220.dp else 300.dp))
                    Canvas(Modifier.align(Alignment.TopCenter).offset(y = if (split) 61.dp else 77.dp)
                        .size(if (split) 144.dp else 196.dp)) {
                        drawCircle(KagomColor.sparkle.copy(alpha = 0.08f))
                        drawCircle(KagomColor.sparkle, style = Stroke(2.dp.toPx()))
                    }
                    KagomCharacter(profile.skin, Expression.IDLE, Modifier.align(Alignment.TopCenter)
                        .offset(y = if (split) 80.dp else 102.dp)
                        .size(if (split) 70.dp else 95.dp, if (split) 110.dp else 150.dp), worn = equipped.layers())
                    Sparkles(Modifier.fillMaxSize(), listOf(0.49f to 0f, 0.25f to 0.47f, 0.74f to 0.17f, 0.77f to 0.8f))
                }
                Text("LEVEL UP!", style = if (split) KagomType.LevelTitleCompact else KagomType.LevelTitle,
                    color = KagomColor.sparkle)
                Text("${p.tier}", style = if (split) KagomType.LevelNumberCompact else KagomType.LevelNumber,
                    color = KagomColor.Level.gold)
                Spacer(Modifier.height(if (split) 16.dp else 20.dp))
                // "B" 글자(줄 높이 34)가 들어가게 Figma 28 칸을 36으로, 줄 높이를 48로 맞췄다
                Row(Modifier.height(48.dp)
                    .background(KagomColor.Severity.bg(Severity.RECOMMEND), RoundedCornerShape(KagomRadius.full))
                    .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).background(KagomColor.Level.blue, RoundedCornerShape(KagomRadius.r8)),
                        contentAlignment = Alignment.Center) {
                        Text("B", style = KagomType.Body18Strong, color = KagomColor.Text.inverse)
                    }
                    Text("블루멤버스 +${StateEngine.fmt(p.points)}P", style = if (split) KagomType.Body16Strong else KagomType.Body18Strong, color = KagomColor.Text.primary)
                }
                Spacer(Modifier.height(if (split) 28.dp else 52.dp))
            }
            Column(Modifier.padding(horizontal = if (split) 24.dp else 40.dp)
                .padding(top = if (split) 16.dp else 0.dp, bottom = if (split) 24.dp else 36.dp)) {
                ModalActions("확인", onConfirm, null, {})
            }
        }
    }
}

/** Figma kagomSays에는 이름 접두어가 없어 공용 SpeechBubble을 바꾸지 않고 팝업 안에서만 맞춘다. */
@Composable
private fun PopupQuote(text: String) {
    Text(text, style = KagomType.Body16, color = KagomColor.Text.secondary,
        modifier = Modifier.fillMaxWidth().background(KagomColor.Surface.subtle, RoundedCornerShape(KagomRadius.r16))
            .padding(horizontal = 16.dp, vertical = 12.dp))
}

// ───────────── POP-05 정비 시트 ─────────────

/**
 * maintenanceSheet: Full = 오른쪽 사이드 드로어(640), Split = 바텀시트.
 * [visible]이 바뀌면 딤이 깔리며 Full은 오른쪽에서, Split은 아래에서 밀려 들어오고 같은 길로 나간다.
 */
@Composable
fun MaintenanceSheet(
    visible: Boolean,
    status: VehicleStatus,
    data: AppData,
    gearP: Boolean,
    onClose: () -> Unit,
    onRefilled: () -> Unit,
    onOilChanged: () -> Unit,
    onSevere: (Boolean) -> Unit,
    onRecommendedOil: (Boolean) -> Unit,
    onToggleCheck: (String) -> Unit,
    onCompleteCheckup: () -> Unit,
    onFindShop: () -> Unit,
) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val slideIn = tween<IntOffset>(KagomMotion.SHEET, easing = FastOutSlowInEasing)
    val slideOut = tween<IntOffset>(KagomMotion.SHEET_OUT, easing = FastOutSlowInEasing)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(KagomMotion.SHEET)), exit = fadeOut(tween(KagomMotion.SHEET_OUT))) {
            Box(Modifier.fillMaxSize().background(KagomColor.Overlay.dim).clickable(remember0(), null, onClick = onClose))
        }
        val shape = if (split) RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp) else RoundedCornerShape(0.dp)
        AnimatedVisibility(
            visible,
            if (split) Modifier.align(Alignment.BottomCenter).padding(top = 128.dp) else Modifier.align(Alignment.CenterEnd),
            enter = if (split) slideInVertically(slideIn) { it } else slideInHorizontally(slideIn) { it },
            exit = if (split) slideOutVertically(slideOut) { it } else slideOutHorizontally(slideOut) { it },
        ) {
            Column(
                (if (split) Modifier.fillMaxWidth().heightIn(max = 952.dp) else Modifier.width(640.dp).fillMaxHeight())
                    .background(KagomColor.Surface.card, shape)
                    .clip(shape)
                    .clickable(remember0(), null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(start = if (split) 20.dp else 28.dp, end = if (split) 20.dp else 28.dp,
                        top = if (split) 24.dp else 32.dp, bottom = if (split) 20.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(if (split) 10.dp else 20.dp),
            ) {
                Text("정비", style = KagomType.Title32, color = KagomColor.Text.primary)

                // Figma 7:2433 / 7:2471: 섹션은 차량 상태로 고르고 스타일·문구는 같은 카드를 쓴다.
                status.shopGuide?.let { g ->
                    MaintenanceSection(KagomColor.Severity.bg(Severity.URGENT)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(g.label, style = KagomType.Body18Strong, color = KagomColor.Severity.fg(Severity.URGENT))
                            com.example.roro.ui.components.StatusBadge(Severity.URGENT, "긴급")
                        }
                        Text("가까운 정비소에서 바로 점검받으세요.", style = KagomType.Body16, color = KagomColor.Text.secondary)
                        IviButton("가까운 정비소 찾기", onFindShop, enabled = gearP)
                    }
                }
                if (status.engineType != com.example.roro.core.EngineType.EV && (status.oilChange != null || status.engineOilAlert)) {
                    MaintenanceSection {
                        Text("엔진오일 교체 시기", style = KagomType.Heading22, color = KagomColor.Text.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IviButton("보충했어요", onRefilled, enabled = gearP && status.engineOilAlert && !data.maintenance.engineOilDismissed)
                            IviButton("오일 교체했어요", onOilChanged, type = IviButtonType.Primary, enabled = gearP)
                        }
                        ToggleRow("짧은 거리 위주로 타거나 험한 길을 자주 달려요", data.maintenance.severe, gearP, onSevere)
                        ToggleRow("제조사 추천(순정) 오일을 써요", data.maintenance.recommendedOil, gearP, onRecommendedOil)
                    }
                }
                MaintenanceSection {
                    Text(if (status.checkup.due) "${status.checkup.round}만 km 정기 점검" else "정기 점검",
                        style = KagomType.Heading22, color = KagomColor.Text.primary)
                    if (status.checkup.due) {
                        Checkup.itemsFor(status.engineType).forEach { item ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IviCheckbox(item.key in data.checkupChecks, "", { onToggleCheck(item.key) },
                                    Modifier.size(64.dp).semantics { contentDescription = item.label }, enabled = gearP)
                                Text(item.label, style = KagomType.Body20, color = KagomColor.Text.primary,
                                    modifier = Modifier.weight(1f).clickable(enabled = gearP) { onToggleCheck(item.key) })
                            }
                        }
                        IviButton("점검 다 했어요", onCompleteCheckup, type = IviButtonType.Primary,
                            enabled = gearP && Checkup.canComplete(status.checkup, status.engineType, data.checkupChecks))
                    } else {
                        if (data.maintenance.checkupRoundsDone > 0) {
                            Text("${data.maintenance.checkupRoundsDone}만 km 점검을 완료했어요",
                                style = KagomType.Body20, color = KagomColor.Text.primary)
                        }
                        Text("다음 점검: ${status.checkup.round}만 km · ${status.checkup.display}",
                            style = KagomType.Body20, color = KagomColor.Text.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KagomType.Body20, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
        IviSwitch(checked, onChange, enabled = enabled)
    }
}


/** 착용 레이어 (하의 → 상의 → 머리) */
private fun Equipped.layers() = wornLayers(head, top, bottom)

/** 정비 시트의 세 카드만 감싸 공용 화면 스타일에 영향을 주지 않는다. */
@Composable
private fun MaintenanceSection(color: Color = KagomColor.Surface.subtle, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(color, RoundedCornerShape(KagomRadius.r16)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
