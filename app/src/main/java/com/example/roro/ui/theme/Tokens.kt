package com.example.roro.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.roro.core.Severity as Sev

/**
 * Kagom 디자인 토큰 (Figma 변수 컬렉션 "Kagom Tokens" / "Kagom Primitives").
 * 이름은 Figma 경로를 그대로 옮겼다: color/surface/page → KagomColor.Surface.page,
 * CSS로는 var(--kagom-color-surface-page).
 *
 * 앱 코드에서 색 HEX를 쓰는 곳은 이 파일뿐이다 (가이드 8.1).
 * 단위: 1.dp = Figma 1px (DesignScale이 화면 크기에 맞춰 밀도를 바꾼다).
 */
object KagomColor {

    object Surface {
        val page = Color(0xFFF6F7F8)
        val card = Color(0xFFFFFFFF)
        val subtle = Color(0xFFF6F7F8)
        val canvas = Color(0xFFF6F7F8)
        val mintSoft = Color(0xFFE8F9F6)
        val mint = Color(0xFFD1F5ED)
        val inverse = Color(0xFF131417)
        val dark = Color(0xFF44464E)
        val disabled = Color(0x0D131417)
    }

    object Overlay {
        val dim = Color(0x66131417)
        /** 보상 상자가 열릴 때 번지는 흰 빛 (surface/card 90%) */
        val flash = Color(0xE6FFFFFF)
    }

    object Text {
        val primary = Color(0xFF131417)
        val secondary = Color(0xFF44464E)
        val tertiary = Color(0xFF70737C)
        val inverse = Color(0xFFFFFFFF)
        val accent = Color(0xFF0F6661)
        val link = Color(0xFF2E5BFF)
        /** Pleos text/dark/secondary */
        val darkSecondary = Color(0xD6131417)
        val disabled = Color(0x47131417)
        /** 어두운 배경 위 보조 글자 */
        val inverseSecondary = Color(0xB3FFFFFF)
    }

    object Border {
        val default = Color(0xFFE4E6EA)
        val divider = Color(0xFFD5DEE3)
        val disabled = Color(0x14131417)
    }

    object Severity {
        private val observeBg = Color(0xFFE5EDF2); private val observeFg = Color(0xFF24556F)
        private val recommendBg = Color(0xFFF8F1D4); private val recommendFg = Color(0xFF7A5B00)
        private val urgentBg = Color(0xFFF8E4E4); private val urgentFg = Color(0xFF9E2424)
        private val okBg = Color(0xFFE5F2E4); private val okFg = Color(0xFF2F6F4E)

        fun bg(s: Sev): Color = when (s) { Sev.OBSERVE -> observeBg; Sev.RECOMMEND -> recommendBg; Sev.URGENT -> urgentBg; Sev.OK -> okBg }
        fun fg(s: Sev): Color = when (s) { Sev.OBSERVE -> observeFg; Sev.RECOMMEND -> recommendFg; Sev.URGENT -> urgentFg; Sev.OK -> okFg }
    }

    object Map {
        val sea = Color(0xFFC3DDE2)
        val seaEdge = Color(0xFF6E97A3)
        val landLocked = Color(0xFFE4E6EA)
        val landUnlocked = Color(0xFF7DBF78)
        /** 지도 구획 일부 해금 (1~2/3, Figma green/200, 가이드 v1.2 land-partial) */
        val landPartial = Color(0xFFC3E2C0)
        val fog = Color(0x59131417)
        val road = Color(0xFF44464E)
        val boundary = Color(0xFFF6F7F8)
        /** 남한 지도: 랜드마크가 있는데 아직 안 간 시 (landLocked 위에 fog를 덮은 색) */
        val landFogged = Color(0xFF9B9DA0)
        /** 남한 지도: 시·군 경계 */
        val unitBorder = Color(0xFFB9BCC3)
        /** 남한 지도: 시·도 경계·해안선 */
        val provinceBorder = Color(0xFF70737C)
    }

    object Progress {
        val track = Color(0xFFE4E6EA)
        val fill = Color(0xFF0F6661)
        /** 보상 모달 EXP 상승분 (mint/300) */
        val gain = Color(0xFF6FDCC4)
    }

    object Accent {
        val positive = Color(0xFF2F6F4E)
        val tier = Color(0xFFF3E6C8)
        /** 랜드마크 방문 스탬프 */
        val stamp = Color(0xFFC0392B)
    }

    /** 도전과제 달성률 카드의 곰 얼굴 (Figma avatarBear 194:4585, 벡터에 직접 들어 있는 색) */
    object Avatar {
        val ear = Color(0xFFB07A45)
        val earInner = Color(0xFFE7B98A)
        val head = Color(0xFFC8955A)
        val muzzle = Color(0xFFF1D9B5)
        val face = Color(0xFF2B1B10)
    }

    /** 컨페티·반짝이 장식 (confettiBurst / sparkle) */
    val confetti = listOf(
        Color(0xFF6FDCC4), Color(0xFF0F6661), Color(0xFFD49C8A), Color(0xFFB9A1E0),
        Color(0xFF7DBF78), Color(0xFFF3E6C8), Color(0xFF3F8C80),
    )
    val sparkle = Color(0xFFFFF6D8)
}

/** Pleos ivi_* 공용 UI 키트 값 (Components 페이지, 수정 금지 원본의 치수·색) */
object PleosKit {
    /** ivi_basic_button Type=Primary (밝음) */
    val buttonPrimaryBg = Color(0x0D131417)
    val buttonPrimaryPressed = Color(0x1F131417)
    /** ivi_basic_button Type=Secondary (어두움) — 이 키트에서 "Secondary"가 어두운 강조 버튼 */
    val buttonSecondaryBg = Color(0xFF44464E)
    val buttonSecondaryPressed = Color(0xFF2C2E33)
    val switchOn = Color(0xFF02C265)
    val switchOff = Color(0x4D131417)
    val switchKnob = Color(0xFFFFFFFF)
    val checkboxBorder = Color(0x33131417)
    val toastBg = Color(0xFF44464E)
    val shadow = Color(0x14000000)
    val keyboard = Color(0xFF131417)
}

/** space/4 … space/40 */
object KagomSpace {
    val s4 = 4.dp; val s8 = 8.dp; val s12 = 12.dp; val s16 = 16.dp; val s20 = 20.dp
    val s24 = 24.dp; val s28 = 28.dp; val s32 = 32.dp; val s36 = 36.dp; val s40 = 40.dp
}

/** radius/8 … radius/full */
object KagomRadius {
    val r8 = 8.dp; val r12 = 12.dp; val r16 = 16.dp; val r20 = 20.dp; val r24 = 24.dp
    val r32 = 32.dp
    val full = 999.dp
}

/** size 토큰 (nav-rail-width, tab-bar-height, chip-height, touch-min, nav-item) */
object KagomSize {
    val navRailWidth = 88.dp
    val tabBarHeight = 64.dp
    val chipHeight = 40.dp
    val touchMin = 64.dp
    val navItem = 72.dp
    val navItemHeight = 58.dp
}

/**
 * Primitives skin/P0n/{body, belly, ear, outline} (spec 3장 표).
 * 가이드 4.1: Primitives 직접 참조 금지 — 스킨 팔레트만 예외.
 */
data class SkinPalette(val body: Color, val belly: Color, val ear: Color, val outline: Color) {
    /** 홈·온보딩·치장 캔버스 배경: 스킨 몸통색 22% 틴트 (페이지 배경 위) */
    val canvasTint: Color get() = lerp(KagomColor.Surface.page, body, 0.22f)
    /** 보상 모달 hero 배경 */
    val heroTint: Color get() = lerp(KagomColor.Surface.card, body, 0.75f)
    val heroGlow: Color get() = lerp(KagomColor.Surface.card, body, 0.25f)
}

object KagomSkinPalette {
    private val palettes = mapOf(
        "P01" to SkinPalette(Color(0xFFD3BC9C), Color(0xFFF1E6D0), Color(0xFFEADDC6), Color(0xFFC2AB8B)),
        "P02" to SkinPalette(Color(0xFFA8DCC6), Color(0xFFEAF6F6), Color(0xFFCFEDE2), Color(0xFFA0CDBE)),
        "P03" to SkinPalette(Color(0xFFB9A1E0), Color(0xFFEEDDBF), Color(0xFFEEDDBF), Color(0xFFAA93D2)),
        "P04" to SkinPalette(Color(0xFF34363C), Color(0xFF4A4D55), Color(0xFF4E5159), Color(0xFF2C2E33)),
        "P05" to SkinPalette(Color(0xFFE9EBEF), Color(0xFFD6DAE0), Color(0xFFDDE1E7), Color(0xFFCDD1D7)),
        "P06" to SkinPalette(Color(0xFF7B4A2D), Color(0xFFCFA67C), Color(0xFFC49A70), Color(0xFF6A3F26)),
        "P07" to SkinPalette(Color(0xFF72DCC4), Color(0xFFB4F2E4), Color(0xFFA8EEDD), Color(0xFF63C9B2)),
        "P08" to SkinPalette(Color(0xFFD49C8A), Color(0xFFEFCDBF), Color(0xFFEBC6B8), Color(0xFFC08878)),
    )

    fun of(code: String?): SkinPalette = palettes[code] ?: palettes.getValue("P03")
}
