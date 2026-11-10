@file:OptIn(ExperimentalTextApi::class)

package com.example.roro.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
)

object KagomType {
    // Figma POP-04 levelUpTitle / levelNumber 전용이며 다른 화면 타이포는 유지한다.
    val LevelTitle = style(64, 70, true)
    val LevelTitleCompact = style(44, 48, true)
    val LevelNumber = style(144, 144, true)
    val LevelNumberCompact = style(104, 104, true)
    val Display40 = style(40, 48, true, -0.5f)
    val Title36 = style(36, 44, true, -0.5f)
    val Title32 = style(32, 40, true, -0.5f)
    val Title28 = style(28, 36, true, -0.25f)
    val Heading24 = style(24, 32, true)
    val Heading22 = style(22, 30, true)
    val Heading20 = style(20, 28, true)
    val Body20 = style(20, 28, false)
    val Body18 = style(18, 26, false)
    val Body18Strong = style(18, 26, true)
    val Body16 = style(16, 24, false)
    val Body16Strong = style(16, 24, true)
    val Caption14 = style(14, 20, false)
    val Caption14Strong = style(14, 20, true)

    // Pleos ivi_* 키트 타이포 (typo 컬렉션)
    /** Title/Small/Strong 32/40 — ivi_basic_button Large, 탭 */
    val PleosTitleSmallStrong = style(32, 40, true)
    /** Body/Large/Strong 30/38 — ivi_basic_button Medium */
    val PleosBodyLargeStrong = style(30, 38, true)
    /** Body/Small/Strong 26/34 — ivi_toast_popup */
    val PleosBodySmallStrong = style(26, 34, true)
    /** ivi_text_field 입력 글자 */
    val PleosField = style(36, 44, false)
}
