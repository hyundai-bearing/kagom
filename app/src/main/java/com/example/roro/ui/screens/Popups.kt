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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
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
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.KagomRenders
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.components.LandmarkGlyphView
import com.example.roro.ui.components.LandmarkPin
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.SparkleGlyph
import com.example.roro.ui.components.SpeechBubble
import com.example.roro.ui.components.TierEmblem
import com.example.roro.ui.josa
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
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
            .background(KagomColor.Overlay.dim).clickable(remember0(), null) {},
        contentAlignment = Alignment.Center,
    ) {
        ConfettiLayer()
        val shape = RoundedCornerShape(KagomRadius.r32)
        Column(
            Modifier.width(if (split) 600.dp else 880.dp)
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
                Chip(heroChip, KagomColor.Accent.tier, KagomColor.Severity.fg(Severity.RECOMMEND),
                    Modifier.padding(start = if (split) 24.dp else 32.dp, top = if (split) 20.dp else 28.dp))
                if (flash) Box(Modifier.fillMaxSize().graphicsLayer { alpha = glow.value }.background(KagomColor.Overlay.flash))
            }
            Column(Modifier.padding(horizontal = if (split) 24.dp else 40.dp, vertical = if (split) 24.dp else 32.dp), content = body)
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
    const val RAYS_MS = 9000         // POP-04: 햇살 무늬가 한 칸(30°) 도는 시간
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
    Box(modifier.size(size * 1.3f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {
            rotationZ = spin
            val s = 0.7f + 0.3f * pop.value.coerceAtMost(1f); scaleX = s; scaleY = s
        }) {
            drawCircle(KagomColor.Surface.card.copy(alpha = 0.25f + 0.2f * glow), radius = this.size.minDimension / 2.1f)
            drawCircle(KagomColor.Surface.card, radius = this.size.minDimension / 2.1f,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
        Box(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                .size(size).shadow(8.dp, RoundedCornerShape(24.dp)).background(KagomColor.Surface.card, RoundedCornerShape(24.dp))
                .border(4.dp, KagomColor.Surface.mint, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) AssetImage(thumb, Modifier.fillMaxSize().padding(size * 0.08f))
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
    Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip("+$gain EXP", KagomColor.Surface.mint, KagomColor.Text.accent, height = 32.dp)
            Spacer(Modifier.weight(1f))
            Text(
                if (next == null) "$now EXP · 최고 티어" else if (now >= next) "$now / $next EXP · 티어 승급!"
                else "$now / $next EXP · 다음 티어까지 ${next - now} EXP",
                style = KagomType.Caption14Strong, color = KagomColor.Text.secondary,
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
private fun ModalActions(primary: String, onPrimary: () -> Unit, secondary: String?, onSecondary: () -> Unit, footnote: String?) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    if (split) {
        if (footnote != null) Text(footnote, style = KagomType.Caption14, color = KagomColor.Text.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(KagomSpace.s16))
        IviButton(primary, onPrimary, Modifier.fillMaxWidth(), size = IviButtonSize.Large)
        if (secondary != null) {
            Spacer(Modifier.height(10.dp))
            IviButton(secondary, onSecondary, Modifier.fillMaxWidth(), type = IviButtonType.Primary, size = IviButtonSize.Large)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            IviButton(primary, onPrimary, Modifier.weight(1f), size = IviButtonSize.Large)
            if (secondary != null) IviButton(secondary, onSecondary, type = IviButtonType.Primary, size = IviButtonSize.Large)
        }
        if (footnote != null) {
            Spacer(Modifier.height(KagomSpace.s20))
            Text(footnote, style = KagomType.Caption14, color = KagomColor.Text.tertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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

    if (!p.opened) {
        // 상자 열기: 크게 흔들리다 부풀며 빛이 번진 뒤 공개 화면으로 (공개 화면은 그 빛이 걷히며 시작)
        val scope = rememberCoroutineScope()
        val opening = remember { Animatable(0f) }
        var busy by remember { mutableStateOf(false) }
        val open: () -> Unit = {
            if (!busy) {
                busy = true
                scope.launch { opening.animateTo(1f, tween(Motion.OPEN_MS, easing = LinearEasing)); onOpen() }
            }
        }
        ModalScaffold(heroH, "보상 도착", hero = {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.radialGradient(listOf(palette.heroGlow, palette.heroTint), center, size.height * 0.95f)) })
            Sparkles(Modifier.fillMaxSize(), listOf(0.33f to 0.15f, 0.5f to 0.08f, 0.66f to 0.22f, 0.3f to 0.83f, 0.68f to 0.8f))
            WobblingGift(Modifier.align(Alignment.Center).size(if (split) 160.dp else 220.dp, if (split) 150.dp else 200.dp).offset(y = 10.dp), opening.value)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = opening.value.let { it * it * it } }.background(KagomColor.Overlay.flash))
            KagomCharacter(profile.skin, Expression.IDLE, Modifier.align(Alignment.BottomEnd).padding(end = if (split) 16.dp else 80.dp)
                .size(if (split) 120.dp else 160.dp, if (split) 170.dp else 210.dp).offset(y = 20.dp), worn = equipped.layers())
        }) {
            Text("${q.rewardKind} 보상이 도착했어요", style = KagomType.Title32, color = KagomColor.Text.primary)
            Spacer(Modifier.height(KagomSpace.s8))
            Text("${q.title} 달성 · 정차한 지금 열어볼 수 있어요", style = KagomType.Body18, color = KagomColor.Text.secondary)
            Spacer(Modifier.height(KagomSpace.s20))
            SpeechBubble(profile.name, "안에 뭐가 들었을까요? 같이 열어봐요!")
            Spacer(Modifier.height(KagomSpace.s16))
            ModalActions("상자 열어보기", open, "나중에", onLater, "열기 전에는 보상이 ? 로 표시돼요. 넘기면 큐에 보관돼요")
        }
        return
    }

    ModalScaffold(heroH, "보상 획득!", enter = false, flash = true, hero = {
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Brush.radialGradient(listOf(palette.heroGlow, palette.heroTint), center, size.height * 0.95f)) })
        KagomCharacter(profile.skin, Expression.IDLE, Modifier.align(Alignment.BottomStart).padding(start = if (split) 60.dp else 120.dp)
            .size(if (split) 130.dp else 180.dp, if (split) 190.dp else 260.dp), worn = equipped.layersWith(item.id))
        Sparkles(Modifier.fillMaxSize(), listOf(0.6f to 0.2f, 0.75f to 0.08f, 0.88f to 0.18f, 0.58f to 0.72f, 0.9f to 0.75f))
        ItemShowcase(item.mark, if (split) 100.dp else 140.dp, Modifier.align(Alignment.CenterEnd).padding(end = if (split) 70.dp else 150.dp), item.id)
    }) {
        Text("${q.rewardKind} 보상을 받았어요!", style = KagomType.Title32, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s8))
        Text("${q.title} 달성 · ${q.flavor}", style = KagomType.Body18, color = KagomColor.Text.secondary)
        Spacer(Modifier.height(KagomSpace.s16))
        ItemTitleRow(item.name, listOf(item.category.label to false, "NEW" to true))
        Spacer(Modifier.height(KagomSpace.s16))
        ExpGain(p.exp, p.expBefore, p.expAfter)
        Spacer(Modifier.height(KagomSpace.s16))
        SpeechBubble(profile.name, item.line.ifEmpty { "멋진 보상이에요! 바로 해 볼까요?" })
        Spacer(Modifier.height(KagomSpace.s16))
        ModalActions("바로 장착하기", { onFinish(true) }, "나중에", { onFinish(false) }, "넘긴 보상은 치장 옷장에서 언제든 입을 수 있어요")
    }
}

@Composable
private fun ItemTitleRow(name: String, chips: List<Pair<String, Boolean>>, limited: Boolean = false) {
    FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
        Text(name, style = KagomType.Heading22, color = KagomColor.Text.primary, modifier = Modifier.align(Alignment.CenterVertically))
        chips.forEach { (t, isNew) ->
            if (isNew) Chip(t, KagomColor.Severity.bg(Severity.URGENT), KagomColor.Severity.fg(Severity.URGENT), Modifier.align(Alignment.CenterVertically), height = 32.dp)
            else Chip(t, KagomColor.Surface.subtle, KagomColor.Text.secondary, Modifier.align(Alignment.CenterVertically), height = 32.dp)
        }
        if (limited) Chip("랜드마크 한정", KagomColor.Severity.bg(Severity.RECOMMEND), KagomColor.Severity.fg(Severity.RECOMMEND),
            Modifier.align(Alignment.CenterVertically), height = 32.dp)
    }
}

// ───────────── POP-03 랜드마크 해금 ─────────────

@Composable
fun LandmarkUnlockModal(p: PendingPopup.Landmark, profile: Profile, equipped: Equipped, onFinish: (equip: Boolean) -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val lm = Landmarks.byId(p.landmarkId) ?: return
    val item = Items.byId(p.itemId) ?: return
    val date = LocalDate.ofEpochDay(p.day).format(DateTimeFormatter.ofPattern("yyyy. MM. dd"))

    ModalScaffold(if (split) 260.dp else 300.dp, "안개가 걷혔어요", hero = {
        // 컬러로 바뀐 지도 장면 + 오른쪽 바다에 보상 쇼케이스
        Canvas(Modifier.fillMaxSize()) {
            drawRect(KagomColor.Map.landUnlocked)
            val dark = KagomColor.Accent.positive.copy(alpha = 0.22f)
            drawOval(dark, Offset(size.width * 0.07f, size.height * 0.13f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.37f))
            drawOval(dark, Offset(size.width * 0.44f, size.height * 0.5f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.37f))
            drawLine(KagomColor.Surface.card, Offset(0f, size.height * 0.37f), Offset(size.width, size.height * 0.37f), 2.dp.toPx())
            val sea = Path().apply {
                moveTo(size.width * 0.72f, 0f)
                quadraticTo(size.width * 0.66f, size.height * 0.25f, size.width * 0.72f, size.height * 0.5f)
                quadraticTo(size.width * 0.78f, size.height * 0.75f, size.width * 0.70f, size.height)
                lineTo(size.width, size.height); lineTo(size.width, 0f); close()
            }
            drawPath(sea, KagomColor.Map.sea)
        }
        LandmarkGlyphView(lm.glyph, Modifier.align(Alignment.TopCenter).padding(top = 56.dp).offset(x = (-20).dp).size(80.dp, 60.dp))
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
                val pin = Offset(size.width / 2 + 54.dp.toPx(), size.height / 2 + 22.dp.toPx())
                // 핀에서 가장 먼 모서리(왼쪽 위)까지 닿으면 다 걷힌 것
                val radius = Offset(pin.x, pin.y).getDistance() * r
                clipPath(Path().apply { addOval(Rect(pin, radius)) }, ClipOp.Difference) { drawRect(KagomColor.Map.landFogged) }
                if (r > 0f) drawCircle(KagomColor.Surface.card, radius, pin, alpha = 0.7f * (1f - r), style = Stroke(8.dp.toPx()))
            }
        }
        LandmarkPin(PinState.Unlocked, Modifier.align(Alignment.Center).offset(x = 54.dp, y = 22.dp))
        Box(
            Modifier.align(Alignment.Center).offset(x = (-72).dp, y = 70.dp)
                // 도장: 크게 떠 있다가 쿵 찍힌다
                .graphicsLayer { val t = stamp.value; scaleX = 1.9f - 0.9f * t; scaleY = 1.9f - 0.9f * t; alpha = t.coerceIn(0f, 1f) }
                .rotate(-10f)
                .border(3.dp, KagomColor.Accent.stamp, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${lm.name} 방문", style = KagomType.Heading20, color = KagomColor.Accent.stamp)
                Text(date, style = KagomType.Caption14Strong, color = KagomColor.Accent.stamp)
            }
        }
        KagomCharacter(profile.skin, Expression.IDLE, Modifier.align(Alignment.BottomStart).padding(start = if (split) 30.dp else 120.dp)
            .size(if (split) 130.dp else 180.dp, if (split) 190.dp else 260.dp), worn = equipped.layersWith(item.id))
        Sparkles(Modifier.fillMaxSize(), listOf(0.72f to 0.25f, 0.85f to 0.12f, 0.93f to 0.3f, 0.75f to 0.75f, 0.92f to 0.8f))
        ItemShowcase(item.mark, if (split) 90.dp else 130.dp, Modifier.align(Alignment.CenterEnd).padding(end = if (split) 8.dp else 50.dp), item.id)
    }) {
        Text("${lm.name}에 도착했어요!", style = KagomType.Title32, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s8))
        Text("강릉 랜드마크 ${p.visitedPlaces} / ${Landmarks.all.size} 방문 · ${item.name.josa("을", "를")} 받았어요",
            style = KagomType.Body18, color = KagomColor.Text.secondary)
        Spacer(Modifier.height(KagomSpace.s16))
        ItemTitleRow(item.name, listOf(item.category.label to false, "NEW" to true), limited = true)
        Spacer(Modifier.height(KagomSpace.s16))
        ExpGain(p.exp, p.expBefore, p.expAfter)
        Spacer(Modifier.height(KagomSpace.s16))
        SpeechBubble(profile.name, item.line)
        Spacer(Modifier.height(KagomSpace.s16))
        ModalActions("바로 장착하기", { onFinish(true) }, "나중에", { onFinish(false) }, "지도에서 ${lm.name}의 안개가 걷히고 컬러로 바뀌어요")
    }
}

// ───────────── POP-04 티어 승급 ─────────────

@Composable
fun TierUpModal(p: PendingPopup.TierUp, profile: Profile, equipped: Equipped, onConfirm: () -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val threshold = Tiers.thresholds.getOrElse(p.tier - 1) { 0 }
    ModalScaffold(if (split) 260.dp else 300.dp, "TIER UP", hero = {
        // 햇살 무늬는 30°마다 되풀이되므로 0 → 30°를 이어 돌리면 끊김 없이 천천히 돈다
        val spin by rememberInfiniteTransition(label = "rays").animateFloat(
            0f, 30f, infiniteRepeatable(tween(Motion.RAYS_MS, easing = LinearEasing)), label = "spin",
        )
        val emblem = remember { Animatable(0f) }
        LaunchedEffect(Unit) { delay(Motion.POP_DELAY_MS); emblem.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(KagomColor.Surface.inverse)
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width
            rotate(spin, c) {
                for (i in 0 until 24 step 2) {
                    val a0 = Math.toRadians(i * 15.0); val a1 = Math.toRadians((i + 1) * 15.0)
                    drawPath(Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + (r * Math.cos(a0)).toFloat(), c.y + (r * Math.sin(a0)).toFloat())
                        lineTo(c.x + (r * Math.cos(a1)).toFloat(), c.y + (r * Math.sin(a1)).toFloat())
                        close()
                    }, KagomColor.Accent.tier)
                }
            }
            drawCircle(Brush.radialGradient(listOf(KagomColor.Surface.inverse.copy(alpha = 0.85f), KagomColor.Surface.inverse.copy(alpha = 0f)), c, size.height * 0.6f), size.height * 0.6f, c)
        }
        Sparkles(Modifier.fillMaxSize(), listOf(0.5f to 0.1f, 0.68f to 0.2f, 0.25f to 0.78f, 0.74f to 0.74f))
        TierEmblem(p.tier, Modifier.align(Alignment.Center).graphicsLayer {
            scaleX = emblem.value; scaleY = emblem.value; alpha = emblem.value.coerceIn(0f, 1f)
        }, large = true)
        KagomCharacter(profile.skin, Expression.IDLE, Modifier.align(Alignment.BottomEnd).padding(end = if (split) 12.dp else 50.dp)
            .size(if (split) 110.dp else 140.dp, if (split) 160.dp else 200.dp).offset(y = 12.dp), worn = equipped.layers())
    }) {
        Text("티어 ${p.tier}에 올랐어요!", style = KagomType.Title32, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s8))
        val nth = if (p.tier == 2) "첫" else "${p.tier - 1}번째"
        Text("경험치 ${"$threshold".josa("을", "를")} 넘겼어요 · ${profile.name.josa("과", "와")} 함께한 $nth 승급",
            style = KagomType.Body18, color = KagomColor.Text.secondary)
        Spacer(Modifier.height(KagomSpace.s16))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            Chip("블루멤버스 +${StateEngine.fmt(p.points)} P", KagomColor.Severity.bg(Severity.OBSERVE), KagomColor.Severity.fg(Severity.OBSERVE),
                height = 32.dp, style = KagomType.Body16Strong)
            Text("지급 완료 · 현대 앱에서 확인할 수 있어요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
        }
        Spacer(Modifier.height(KagomSpace.s16))
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            StatTile("${p.totalExp}", "누적 EXP", Modifier.weight(1f))
            StatTile("${p.ownedItems}", "획득 아이템", Modifier.weight(1f))
            StatTile("${p.visitedPlaces} / ${Landmarks.all.size}", "방문 랜드마크", Modifier.weight(1f))
        }
        Spacer(Modifier.height(KagomSpace.s16))
        SpeechBubble(profile.name, "우리 벌써 티어 ${"${p.tier}".josa("이에요", "예요")}! 다음엔 어디로 드라이브 갈까요?")
        Spacer(Modifier.height(KagomSpace.s16))
        ModalActions("확인", onConfirm, null, {}, null)
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.height(76.dp).background(KagomColor.Surface.subtle, RoundedCornerShape(KagomRadius.r16)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = KagomType.Heading24, color = KagomColor.Text.primary)
        Text(label, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
    }
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
            if (split) Modifier.align(Alignment.BottomCenter).padding(top = 138.dp) else Modifier.align(Alignment.CenterEnd),
            enter = if (split) slideInVertically(slideIn) { it } else slideInHorizontally(slideIn) { it },
            exit = if (split) slideOutVertically(slideOut) { it } else slideOutHorizontally(slideOut) { it },
        ) {
            Column(
                (if (split) Modifier.fillMaxWidth() else Modifier.width(640.dp).fillMaxHeight())
                    .background(KagomColor.Surface.card, shape)
                    .clickable(remember0(), null) {}
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (split) 20.dp else 28.dp, vertical = if (split) 24.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
            ) {
                Text("정비", style = KagomType.Title32, color = KagomColor.Text.primary)

                // SEN-04 / SEN-06: 정비소 안내 블록
                status.shopGuide?.let { g ->
                    Column(
                        Modifier.fillMaxWidth().background(KagomColor.Severity.bg(Severity.URGENT), RoundedCornerShape(KagomRadius.r24)).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(KagomSpace.s12),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s24)) {
                            Text(g.label, style = KagomType.Body16Strong, color = KagomColor.Severity.fg(Severity.URGENT))
                            Text("긴급", style = KagomType.Body16Strong, color = KagomColor.Severity.fg(Severity.URGENT))
                        }
                        Text(
                            if (g.id == com.example.roro.core.StatusId.SEN_04) "센서가 브레이크액 부족을 감지했어요. 보충으로 해결되지 않으니 가까운 정비소에서 바로 점검받으세요."
                            else "엔진 출력 저하 경고등이 켜졌어요. 가까운 정비소에서 바로 점검받으세요.",
                            style = KagomType.Body16, color = KagomColor.Text.secondary,
                        )
                        IviButton("가까운 정비소 찾기", onFindShop, enabled = gearP)
                    }
                }

                // SEN-03 / MNT-01 엔진오일
                if (status.oilChange != null) {
                    Text("엔진오일", style = KagomType.Heading20, color = KagomColor.Text.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (status.engineOilAlert && !data.maintenance.engineOilDismissed) IviButton("보충했어", onRefilled, enabled = gearP)
                        IviButton("오일 교체 완료", onOilChanged, type = IviButtonType.Primary, enabled = gearP)
                    }
                    Text("교체 완료를 누르면 주행거리·기간 기준이 0부터 다시 계산돼요 (MNT-01 초기화)", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                    ToggleRow("가혹 조건", data.maintenance.severe, gearP, onSevere)
                    ToggleRow("추천 오일 사용", data.maintenance.recommendedOil, gearP, onRecommendedOil)
                    Text("교체 주기 ${StateEngine.fmt(status.oilChange.intervalKm)} km · ${if (status.oilChange.intervalDays > 200) 12 else 6}개월 · 남은 거리 ${status.oilChange.display}",
                        style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                }

                // MNT-02 정기 점검 체크리스트
                Text(Checkup.roundLabel(status.checkup.round), style = KagomType.Heading20, color = KagomColor.Text.primary)
                Text(if (status.checkup.due) "점검할 차례예요. 항목을 확인하고 점검 완료를 눌러 주세요." else "다음 점검까지 ${status.checkup.display}",
                    style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                Column {
                    Checkup.items.forEach { item ->
                        IviCheckbox(item in data.checkupChecks, item, { onToggleCheck(item) }, Modifier.padding(start = 8.dp), enabled = gearP)
                    }
                }
                IviButton("점검 완료", onCompleteCheckup, type = IviButtonType.Primary,
                    enabled = gearP && Checkup.items.all { it in data.checkupChecks })
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KagomType.Body20, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
        IviSwitch(checked, onChange, enabled = enabled)
    }
}


/** 착용 레이어 (하의 → 상의 → 머리) */
private fun Equipped.layers() = wornLayers(head, top, bottom)

/** 새 아이템을 입혀 본 레이어 (보상 공개·해금 hero) */
private fun Equipped.layersWith(itemId: String): List<String> {
    val item = Items.byId(itemId) ?: return layers()
    return with(item.category, item.id).layers()
}
