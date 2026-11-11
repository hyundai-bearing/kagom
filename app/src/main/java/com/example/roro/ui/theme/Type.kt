@file:OptIn(ExperimentalTextApi::class)

package com.example.roro.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.roro.R

/**
 * 텍스트 스타일 램프 (가이드 4.2, Figma 텍스트 스타일 이름 그대로).
 * 폰트: Asta Sans 가변 폰트 (OFL, res/font/asta_sans.ttf, 라이선스 assets/licenses).
 */
val AstaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun style(size: Int, line: Int, bold: Boolean, tracking: Float = 0f) = TextStyle(
    fontFamily = AstaSans,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    // 줄은 어절(띄어쓰기) 단위로 넘긴다. 기본값은 한글을 글자마다 끊어서 "향할까 / 요?"처럼 한 글자만 다음 줄로 떨어진다.
    // 어절 단위 규칙은 한국어 로캘에서만 적용되므로 시스템 언어와 상관없이 ko로 고정한다 (UI 글자가 전부 한국어다)
    lineBreak = LineBreak(LineBreak.Strategy.Simple, LineBreak.Strictness.Default, LineBreak.WordBreak.Phrase),
    localeList = LocaleList("ko-KR"),
)

/**
 * Pleos Connect 글자 단계 (크기/줄 높이 px, 1920 기준). ivi_* 키트 typo 컬렉션과 같은 값이다.
 * 출처: Pleos Connect Developer Guide > Designing the UI > Typography (document.pleos.ai, 2026-11-10 확인).
 * 같은 문서가 실제 화면 18px 이상을 권하므로 가장 작은 단계(Label/Small 20)보다 작은 글자는 쓰지 않는다.
 */
private enum class Pleos(val size: Int, val line: Int) {
    HeadlineMedium(56, 72),
    HeadlineSmall(48, 64),
    TitleLarge(40, 52),
    TitleMedium(36, 44),
    TitleSmall(32, 40),
    BodyLarge(30, 38),
    BodyMedium(28, 36),
    BodySmall(26, 34),
    LabelMedium(24, 28),
    LabelSmall(20, 24),
}

private fun style(p: Pleos, bold: Boolean, tracking: Float = 0f) = style(p.size, p.line, bold, tracking)

/**
 * 이름은 Figma Kagom 텍스트 스타일(괄호 안 크기/줄 높이)이고, 값은 같은 순서의 Pleos 단계다.
 * Figma 원본은 14~22px 본문이 많아 차 화면에서 너무 작다 (Pleos Full 창 1682px에서 14px → 실제 12px).
 * 작은 쪽부터 한 단계씩 Pleos 단계에 대응시켜 위계는 그대로 두고 모두 Pleos 최소 이상으로 올렸다.
 * Figma를 보고 스타일을 고르면 그대로 Pleos 크기가 나온다.
 */
object KagomType {
    /** 치장 팝업 이름·설명·상태 (Figma 2021:5094). */
    val ClosetPopupTitle = style(42, 50, true)
    val ClosetPopupBody = style(30, 38, false)
    val ClosetPopupBadge = style(28, 34, true)
    val ClosetItemName = style(30, 36, true)
    val ClosetSmallText = style(28, 34, false)
    val ClosetSmallStrong = style(28, 34, true)
    // Figma POP-04 levelUpTitle / levelNumber 전용. 이미 Pleos Headline보다 커서 Figma 값 그대로 둔다
    val LevelTitle = style(64, 70, true)
    val LevelTitleCompact = style(44, 48, true)
    val LevelNumber = style(144, 144, true)
    val LevelNumberCompact = style(104, 104, true)
    /** Figma Display40 (40/48) */
    val Display40 = style(Pleos.HeadlineMedium, true, -0.5f)
    /** Figma Title36 (36/44) */
    val Title36 = style(Pleos.HeadlineSmall, true, -0.5f)
    /** Figma Title32 (32/40) */
    val Title32 = style(Pleos.TitleLarge, true, -0.5f)
    /** Figma Title28 (28/36) */
    val Title28 = style(Pleos.TitleMedium, true, -0.25f)
    /** Figma Heading24 (24/32) */
    val Heading24 = style(Pleos.TitleSmall, true)
    /** Figma Heading22 (22/30) */
    val Heading22 = style(Pleos.BodyLarge, true)
    /** Figma Heading20 (20/28) */
    val Heading20 = style(Pleos.BodyMedium, true)
    /** Figma Body20 (20/28) */
    val Body20 = style(Pleos.BodyMedium, false)
    /** Figma Body18 (18/26) */
    val Body18 = style(Pleos.BodySmall, false)
    val Body18Strong = style(Pleos.BodySmall, true)
    /** Figma Body16 (16/24) */
    val Body16 = style(Pleos.LabelMedium, false)
    val Body16Strong = style(Pleos.LabelMedium, true)
    /** Figma Caption14 (14/20) */
    val Caption14 = style(Pleos.LabelSmall, false)
    val Caption14Strong = style(Pleos.LabelSmall, true)

    // Pleos ivi_* 키트 타이포 (typo 컬렉션)
    /** Title/Small/Strong 32/40 — ivi_basic_button Large, 탭 */
    val PleosTitleSmallStrong = style(Pleos.TitleSmall, true)
    /** Body/Large/Strong 30/38 — ivi_basic_button Medium */
    val PleosBodyLargeStrong = style(Pleos.BodyLarge, true)
    /** Body/Small/Strong 26/34 — ivi_toast_popup */
    val PleosBodySmallStrong = style(Pleos.BodySmall, true)
    /** Title/Medium 36/44 — ivi_text_field 입력 글자 */
    val PleosField = style(Pleos.TitleMedium, false)
}
