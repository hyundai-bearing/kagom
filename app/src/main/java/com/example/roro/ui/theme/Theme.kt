package com.example.roro.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize

/** Figma 프레임 이름의 Full|Split (가이드 5.1) */
enum class LayoutMode(val designWidth: Float) {
    /** 16:9 단일 앱 전체 화면 (기준 1920×1080, 축소 1280×720) */
    FULL(1920f),
    /** 멀티태스킹 3분할 세로 카드 (기준 640×1080) */
    SPLIT(640f),
}

val LocalLayoutMode = staticCompositionLocalOf { LayoutMode.FULL }

/**
 * ViewMode 감지 + 디자인 스케일.
 * 가로가 세로보다 길면 Full, 아니면 Split. 밀도를 바꿔 1.dp = Figma 1px이 되게 맞춘다
 * (1920 폭 화면 = 1:1, 1280×720 = 0.667배). 글자 크기도 같은 비율로 (fontScale 1).
 */
@Composable
fun KagomTheme(content: @Composable () -> Unit) {
    // 시스템 바·컷아웃만 피한다 (IME는 화면별로 처리: 키보드가 떠도 Full/Split 판정이 바뀌지 않게)
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val mode = if (widthPx >= heightPx) LayoutMode.FULL else LayoutMode.SPLIT
        val density = Density(density = widthPx / mode.designWidth, fontScale = 1f)
        // 가이드 8.1: 터치 타깃 ≥ 64px(ivi 기준). Figma 크기가 그보다 작은 것(이전, 빵부스러기, 핀, 칩)은
        // 그림은 그대로 두고 누를 수 있는 범위만 64로 넓힌다 (Compose 기본은 48)
        val platform = LocalViewConfiguration.current
        val viewConfiguration = remember(platform) {
            object : ViewConfiguration by platform {
                override val minimumTouchTargetSize get() = DpSize(KagomSize.touchMin, KagomSize.touchMin)
            }
        }
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalLayoutMode provides mode,
            LocalViewConfiguration provides viewConfiguration,
        ) {
            content()
        }
    }
}
