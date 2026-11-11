package com.example.roro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roro.core.Answer
import com.example.roro.core.Expression
import com.example.roro.core.Items
import com.example.roro.core.Landmarks
import com.example.roro.core.Progress
import com.example.roro.core.Questions
import com.example.roro.core.Skin
import com.example.roro.core.StateEngine
import com.example.roro.core.Tiers
import com.example.roro.data.AppData
import com.example.roro.data.Profile
import com.example.roro.ui.components.DriveLockBanner
import com.example.roro.ui.components.HeaderProfile
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.IviTextButton
import com.example.roro.ui.components.IviTextField
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.KagomTurntable
import com.example.roro.ui.components.QuestionCard
import com.example.roro.ui.components.StepDots
import com.example.roro.ui.components.TierEmblem
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.josa
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSkinPalette
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 온보딩 진행 상태. 주행 중 잠금 화면으로 바뀌어도 이어서 하도록 상위에서 들고 있는다 */
class OnboardingState {
    var answers by mutableStateOf(listOf<Answer>())
    /** 방금 고른 카드 (선택 표시 후 다음 문항으로) */
    var picked by mutableStateOf<Answer?>(null)
    var name by mutableStateOf("")
    val step get() = answers.size + 1
}

/** SCR-00 주행 중 설정 제한 (gear=D). [body] = 정차하면 무엇을 이어서 하는지 */
@Composable
fun DriveLockScreen(body: String = "기어를 P에 두면 성향 테스트를 이어서 할 수 있어요.") {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page).padding(horizontal = if (split) 20.dp else KagomSpace.s40),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("초기 설정", style = KagomType.Title36, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s24))
        DriveLockBanner("주행 중에는 설정할 수 없어요", body)
    }
}

/**
 * SCR-00 Carry-over (Figma 218:4551 Full / 218:4672 Split): 새 차에서 이전 카곰의 기록을 찾았을 때,
 * 데려갈지(A) 새 카곰과 처음부터 시작할지(B) 고른다. 처음에는 A가 골라져 있다.
 * [drivenKm] = 지금까지 함께 달린 거리
 */
@Composable
fun CarryOverScreen(profile: Profile, data: AppData, drivenKm: Int, onKeep: () -> Unit, onRestart: () -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    var keep by rememberSaveable { mutableStateOf(true) }
    val name = profile.name
    val title = Items.byId(data.equipped.title)?.name
    val level = Tiers.tierFor(data.exp)
    val km = "${StateEngine.fmt(drivenKm)} km"
    val stats = listOfNotNull(
        "${data.exp}" to "누적 EXP",
        "${(data.owned - Items.DEFAULT_TITLE).size}" to "획득 아이템",
        "${data.visitedPlaces.size} / ${Landmarks.all.size}" to "방문 랜드마크",
        if (split) null else km to "함께 달린 거리",       // Split은 칸이 셋이고 거리는 요약 줄에 쓴다
    )
    val portrait: @Composable (Modifier) -> Unit = { m ->
        KagomCharacter(profile.skin, Expression.IDLE, m, worn = wornLayers(data.equipped.head, data.equipped.top, data.equipped.bottom))
    }
    val statRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            stats.forEach { (value, label) ->
                Column(
                    Modifier.weight(1f).height(76.dp).background(KagomColor.Surface.subtle, RoundedCornerShape(KagomRadius.r16)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(value, style = KagomType.Heading22, color = KagomColor.Text.primary, maxLines = 1)
                    Text(label, style = KagomType.Caption14, color = KagomColor.Text.tertiary, maxLines = 1)
                }
            }
        }
    }
    val cardShape = RoundedCornerShape(KagomRadius.r24)
    val choiceA: @Composable (Modifier) -> Unit = { m ->
        QuestionCard("A", "${name.josa("과", "와")} 계속 함께하기", "칭호 · 아이템 · EXP가 그대로 이어져요", keep, true, { keep = true }, m)
    }
    val choiceB: @Composable (Modifier) -> Unit = { m ->
        QuestionCard("B", "새 카곰과 처음부터 시작하기", "성향 테스트부터 다시 해요 · $name 기록은 보관돼요", !keep, true, { keep = false }, m)
    }

    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page).verticalScroll(rememberScrollState())
            .padding(horizontal = if (split) 20.dp else KagomSpace.s40)
            .padding(top = if (split) 20.dp else 32.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(if (split) 20.dp else 24.dp),
    ) {
        Text("초기 설정 · 새 차량 등록", style = if (split) KagomType.Body16 else KagomType.Body20, color = KagomColor.Text.tertiary)
        Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            Text(
                if (split) "새 차예요! ${name}도 같이 갈까요?" else "새 차와 함께 왔네요! ${name}도 같이 갈까요?",
                style = if (split) KagomType.Title28 else KagomType.Title36, color = KagomColor.Text.primary,
            )
            Text(
                if (split) "이전 차에서 함께한 ${name}의 기록이 있어요. 데려갈지, 새 카곰과 다시 시작할지 골라 주세요."
                else "이전 차에서 함께한 ${name}의 기록이 있어요. ${name.josa("을", "를")} 데려갈지, 새 카곰과 다시 시작할지 골라 주세요.",
                style = if (split) KagomType.Body16 else KagomType.Body18, color = KagomColor.Text.secondary,
            )
        }
        // kagomSummaryCard
        if (split) {
            Row(
                Modifier.fillMaxWidth().background(KagomColor.Surface.card, cardShape).padding(horizontal = KagomSpace.s20, vertical = KagomSpace.s16),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s20),
            ) {
                portrait(Modifier.size(84.dp, 132.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(name, style = KagomType.Heading24, color = KagomColor.Text.primary, maxLines = 1)
                        TierEmblem(level)
                    }
                    Text(listOfNotNull(title, "함께 ${km}를 달렸어요").joinToString(" · "),
                        style = KagomType.Caption14, color = KagomColor.Text.secondary)
                    statRow()
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(208.dp).background(KagomColor.Surface.card, cardShape).padding(horizontal = KagomSpace.s28),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s28),
            ) {
                portrait(Modifier.size(108.dp, 168.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KagomSpace.s16)) {
                    HeaderProfile(name, title, level, data.exp, Tiers.nextThreshold(data.exp), onCard = true)
                    statRow()
                }
            }
        }
        // choiceCards
        if (split) {
            choiceA(Modifier.fillMaxWidth().heightIn(min = 200.dp))
            choiceB(Modifier.fillMaxWidth().heightIn(min = 200.dp))
        } else {
            Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(KagomSpace.s24)) {
                choiceA(Modifier.weight(1f).fillMaxHeight())
                choiceB(Modifier.weight(1f).fillMaxHeight())
            }
        }
        Text("새 카곰으로 시작해도 ${name}의 기록은 삭제되지 않고 보관돼요.", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
        IviButton("이 선택으로 계속하기", { if (keep) onKeep() else onRestart() }, Modifier.fillMaxWidth(),
            type = IviButtonType.Secondary, size = if (split) IviButtonSize.Medium else IviButtonSize.Large)
    }
}

/** SCR-01 성향 테스트 (step=1/3 ~ 3/3) */
@Composable
fun PersonalityScreen(state: OnboardingState) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val index = (state.step - 1).coerceIn(0, 2)

    // 카드를 고르면 선택 상태를 잠깐 보여 주고 다음 문항으로
    LaunchedEffect(state.picked) {
        val p = state.picked ?: return@LaunchedEffect
        delay(280)
        state.answers = state.answers + p
        state.picked = null
    }

    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page)
            .padding(start = if (split) 20.dp else KagomSpace.s40, end = if (split) 20.dp else KagomSpace.s40, top = if (split) 20.dp else 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviTextButton("이전", { state.answers = state.answers.dropLast(1) }, enabled = state.answers.isNotEmpty() && state.picked == null)
            // 세 문항을 다 고르고 결과 화면으로 넘어가는 동안에는 3 / 3으로 남는다 (state.step은 4가 된다)
            StepDots(index + 1)
            Text("${index + 1} / 3", style = KagomType.Heading20, color = KagomColor.Text.primary)
            Text("성향 테스트", style = KagomType.Body18, color = KagomColor.Text.tertiary)
        }
        Spacer(Modifier.height(if (split) 20.dp else 24.dp))
        // 문항이 넘어가면 다음 문항이 가는 쪽에서 밀려 들어온다 ("이전"은 반대쪽에서)
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                val slide = tween<IntOffset>(KagomMotion.SCREEN, easing = FastOutSlowInEasing)
                (fadeIn(tween(KagomMotion.SCREEN)) + slideInHorizontally(slide) { dir * it / 10 } togetherWith
                    fadeOut(tween(KagomMotion.SCREEN / 2)) + slideOutHorizontally(slide) { -dir * it / 10 }) using null
            },
            label = "question",
        ) { shown ->
            val q = Questions.all[shown]
            // 나가는 문항은 방금 고른 답을 선택 표시로 남긴 채 사라진다
            fun selected(key: Answer) = state.answers.getOrNull(shown) == key || (shown == index && state.picked == key)
            val enabled = shown == index && state.picked == null
            Column {
                Text(q.text, style = if (split) KagomType.Heading24 else KagomType.Title36, color = KagomColor.Text.primary)
                Spacer(Modifier.height(if (split) 20.dp else 24.dp))
                val choices = listOf(q.a, q.b)
                if (split) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        choices.forEach { c ->
                            QuestionCard(c.key.name, c.text, c.tag, selected(c.key), enabled,
                                { state.picked = c.key }, Modifier.fillMaxWidth().heightIn(min = 220.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(600.dp), horizontalArrangement = Arrangement.spacedBy(KagomSpace.s24)) {
                        choices.forEach { c ->
                            QuestionCard(c.key.name, c.text, c.tag, selected(c.key), enabled,
                                { state.picked = c.key }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/** SCR-02 스킨 결과 · 이름 등록 (skin=P01~P08, input=focused) */
@Composable
fun NamingScreen(state: OnboardingState, skin: Skin, gearP: Boolean, onComplete: () -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val palette = KagomSkinPalette.of(skin.code)
    var focused by remember { mutableStateOf(false) }
    val valid = Progress.isValidName(state.name)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit = {
        if (valid && gearP) { keyboard?.hide(); focus.clearFocus(); onComplete() }
    }
    // 결과 공개: 카곰이 발끝을 축으로 통 튀어 오르며 나타나고, 설명이 뒤따라 올라온다. 주행 중에 다시 보일 때는 움직이지 않는다
    val appear = remember { Animatable(if (gearP) 0f else 1f) }
    val follow = remember { Animatable(if (gearP) 0f else 1f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        delay(140)
        follow.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val reveal = Modifier.graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 0.95f)
        val s = 0.84f + 0.16f * appear.value
        scaleX = s; scaleY = s
        alpha = (appear.value * 2f).coerceIn(0f, 1f)
    }

    val info: @Composable (Modifier) -> Unit = { m ->
        Column(m.graphicsLayer { alpha = follow.value; translationY = (1f - follow.value) * 24.dp.toPx() }) {
            Text(skin.personality, style = if (split) KagomType.Heading24 else KagomType.Title36, color = KagomColor.Text.primary)
            Spacer(Modifier.height(if (split) 16.dp else 20.dp))
            Text(skin.intro, style = if (split) KagomType.Body16 else KagomType.Body20, color = KagomColor.Text.secondary)
            if (!split) {
                Spacer(Modifier.height(20.dp))
                Text("${skin.code} · ${skin.answers.toCharArray().joinToString("-")}", style = KagomType.Caption14Strong, color = KagomColor.Text.tertiary)
            }
            Spacer(Modifier.height(if (split) 16.dp else 20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(if (split) 76.dp else 104.dp).height(if (split) 22.dp else 32.dp)) {
                    listOf(palette.body, palette.belly, palette.ear, palette.outline).forEachIndexed { i, c ->
                        val d = if (split) 22.dp else 32.dp
                        Box(Modifier.offset(x = (if (split) 18 else 24).dp * i).size(d).border(2.dp, KagomColor.Surface.card, CircleShape).padding(2.dp)
                            .background(c, CircleShape))
                    }
                }
                Spacer(Modifier.width(if (split) 8.dp else 12.dp))
                Text(if (split) "${skin.skinName} · ${skin.code}" else "${skin.skinName} · ${skin.material}",
                    style = if (split) KagomType.Caption14Strong else KagomType.Body16Strong,
                    color = if (split) KagomColor.Text.secondary else KagomColor.Text.primary)
            }
            Spacer(Modifier.height(20.dp))
            IviTextField(
                value = state.name,
                onValueChange = { if (it.length <= 10) state.name = it.replace("\n", "") },
                placeholder = if (split) "이름 2~10자" else "카곰 이름, 2~10자",
                enabled = gearP,
                error = state.name.length >= 2 && !valid && !focused,
                height = if (split) 80.dp else 100.dp,
                modifier = Modifier.fillMaxWidth(),
                onFocusChange = { focused = it },
                onDone = submit,
            )
            Spacer(Modifier.height(KagomSpace.s16))
            Text(
                when {
                    !gearP -> "주행 중에는 이름을 입력할 수 없어요."
                    focused -> "2~10자 · 한글/영문/숫자"
                    else -> "기어 P에서만 키보드가 열립니다."
                },
                style = KagomType.Caption14, color = KagomColor.Text.tertiary,
            )
            Spacer(Modifier.height(KagomSpace.s16))
            IviButton("완료", submit, enabled = valid && gearP, type = IviButtonType.Secondary, size = IviButtonSize.Large)
        }
    }

    if (split) {
        Column(
            Modifier.fillMaxSize().background(KagomColor.Surface.page).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(440.dp).background(palette.canvasTint, RoundedCornerShape(KagomRadius.r24)).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                KagomTurntable(skin.code, emptyList(), Modifier.size(260.dp, 408.dp).then(reveal), enabled = gearP)
            }
            Spacer(Modifier.height(20.dp))
            info(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(824.dp).fillMaxHeight().background(palette.canvasTint), contentAlignment = Alignment.Center) {
                KagomTurntable(skin.code, emptyList(), Modifier.size(500.dp, 785.dp).then(reveal), enabled = gearP)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().background(KagomColor.Surface.page).imePadding().padding(horizontal = KagomSpace.s40),
                contentAlignment = if (focused) Alignment.TopStart else Alignment.CenterStart,
            ) {
                info(Modifier.widthIn(max = 928.dp).padding(top = if (focused) 36.dp else 0.dp).verticalScroll(rememberScrollState()))
            }
        }
    }
}

/** 온보딩 흐름: gear≠P면 SCR-00, 3문항 → SCR-02 */
@Composable
fun OnboardingFlow(state: OnboardingState, gearP: Boolean, onComplete: (String, List<Answer>) -> Unit) {
    val skin = com.example.roro.core.Skins.forAnswers(state.answers)
    val stage = when {
        !gearP && skin == null -> 0
        skin == null -> 1
        else -> 2
    }
    // 단계가 바뀌면 겹쳐 넘어간다 (주행 중 잠금 화면으로는 바로)
    Crossfade(stage, animationSpec = tween(KagomMotion.ms(KagomMotion.SCREEN, gearP)), label = "onboarding") { shown ->
        when {
            shown == 0 -> DriveLockScreen()
            shown == 1 || skin == null -> PersonalityScreen(state)
            else -> NamingScreen(state, skin, gearP) { onComplete(state.name, state.answers) }
        }
    }
}
