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
import com.example.roro.core.Progress
import com.example.roro.core.Questions
import com.example.roro.core.Skin
import com.example.roro.ui.components.DriveLockBanner
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.IviTextButton
import com.example.roro.ui.components.IviTextField
import com.example.roro.ui.components.KagomTurntable
import com.example.roro.ui.components.QuestionCard
import com.example.roro.ui.components.StepDots
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

/** SCR-00 주행 중 설정 제한 (gear=D) */
@Composable
fun DriveLockScreen() {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page).padding(horizontal = if (split) 20.dp else KagomSpace.s40),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("초기 설정", style = KagomType.Title36, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s24))
        DriveLockBanner("주행 중에는 설정할 수 없어요", "기어를 P에 두면 성향 테스트를 이어서 할 수 있어요.")
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
                                { state.picked = c.key }, Modifier.fillMaxWidth().height(220.dp))
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
