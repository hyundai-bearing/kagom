package com.example.roro.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.roro.core.Severity as Sev
import com.example.roro.core.Weather

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

    /** 옷장 라벤더 스튜디오 (Figma 2027:10015). */
    object Closet {
        val studio = Color(0xFFF7F3FB)
        val halo = Color(0xFFE8E0F7)
        val shadow = Color(0x217A6999)
        val selected = Color(0xFF7658B2)
        val equipped = Color(0xFFEDE7F9)
        val equippedText = Color(0xFF5D3E98)
        val owned = Color(0xFFEAF1F4)
        val ownedText = Color(0xFF426172)
        val locked = Color(0xFFE5E5EB)
        val lockedText = Color(0xFF52525F)
        val slotBorder = Color(0xFFE0E0E5)
        val slotEmpty = Color(0xFFEDEDF0)
        val slotSelected = Color(0xFFEDE7F9)
        val slotNumber = Color(0xFF565465)
        val slotPlus = Color(0xFF9494A0)
        val slotSave = Color(0xFF8A73B4)
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

    /** POP-04 level/emblem gradient · level/number · brand/blue (Figma 7:2542 / 64:4135). */
    object Level {
        val glow = Color(0xFF5C5445)
        val mid = Color(0xFF42403D)
        val edge = Color(0xFF2B2B30)
        val gold = Color(0xFFE6BE4D)
        val blue = Color(0xFF0066FF)
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
    val s4 = 4.dp; val s8 = 8.dp; val s10 = 10.dp; val s12 = 12.dp; val s16 = 16.dp; val s20 = 20.dp
    val s24 = 24.dp; val s28 = 28.dp; val s32 = 32.dp; val s36 = 36.dp; val s40 = 40.dp
}

/** radius/8 … radius/full */
object KagomRadius {
    val r8 = 8.dp; val r12 = 12.dp; val r14 = 14.dp; val r16 = 16.dp; val r20 = 20.dp; val r24 = 24.dp
    val r32 = 32.dp
    val r28 = 28.dp
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

/** 옷장 전용 스킨 색상. 초록·보라 비교 시안을 바탕으로 나머지는 스킨에 맞게 정했다. */
data class ClosetPalette(
    val studio: Color, val halo: Color, val accent: Color, val button: Color,
    val selectedBackground: Color, val equippedText: Color, val ownedBackground: Color, val ownedText: Color,
)

object KagomClosetPalette {
    private fun palette(accent: Color, button: Color, base: Color, dark: Color) = ClosetPalette(
        studio = lerp(Color.White, base, 0.08f),
        halo = lerp(Color.White, base, 0.30f),
        accent = accent, button = button,
        selectedBackground = lerp(Color.White, base, 0.24f), equippedText = dark,
        ownedBackground = lerp(Color.White, base, 0.17f), ownedText = dark,
    )
    private val palettes = mapOf(
        "P01" to palette(Color(0xFF806341), Color(0xFF8A714F), Color(0xFFD3BC9C), Color(0xFF624C31)),
        "P02" to palette(Color(0xFF0A665E), Color(0xFF408C7F), Color(0xFFA8DCC6), Color(0xFF0A665E)),
        "P03" to ClosetPalette(KagomColor.Closet.studio, KagomColor.Closet.halo, KagomColor.Closet.selected,
            KagomColor.Closet.slotSave, KagomColor.Closet.slotSelected, KagomColor.Closet.equippedText,
            KagomColor.Closet.owned, KagomColor.Closet.ownedText),
        "P04" to palette(Color(0xFF535B70), Color(0xFF596276), Color(0xFF9CA4B3), Color(0xFF414A5E)),
        "P05" to palette(Color(0xFF586A7C), Color(0xFF6E8193), Color(0xFFC5D1DC), Color(0xFF46596D)),
        "P06" to palette(Color(0xFF835339), Color(0xFF987052), Color(0xFFC49A70), Color(0xFF69402B)),
        "P07" to palette(Color(0xFF147968), Color(0xFF438F82), Color(0xFF72DCC4), Color(0xFF0B6456)),
        "P08" to palette(Color(0xFF985F62), Color(0xFFAB7776), Color(0xFFD49C8A), Color(0xFF794649)),
    )
    fun of(code: String?): ClosetPalette = palettes[code] ?: palettes.getValue("P03")
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

/**
 * 홈 배경 장면(ui/components/KagomBackgroundScene.kt)의 색. 디자인에 없어 정한 값이다 (가정, docs/IMPLEMENTATION.md).
 * 홈 헤더 글자(Text.primary·secondary)가 하늘과 구름 위에서 그대로 읽히도록 [skyTop]과 [cloudShade]는 이보다 어둡게 두지 않는다.
 */
data class ScenePalette(
    val skyTop: Color, val skyBottom: Color,
    /** 해. 흐림에서는 구름 뒤의 옅은 원이고, 비·눈·천둥번개에는 없다 */
    val sun: Color?, val sunGlow: Color?,
    val cloudLight: Color, val cloudShade: Color,
    /** 먼 산 두 겹 (아래로 갈수록 하늘색에 섞여 옅어진다). 바다에서는 먼 섬과 곶 */
    val ridgeFar: Color, val ridgeNear: Color,
    /** 언덕 세 겹과 그 아래쪽 그늘. 바다에서는 [hillFar] = 젖은 모래, [hillNear] = 모래밭 */
    val hillFar: Color, val hillMid: Color, val hillNear: Color, val hillShade: Color,
    val tree: Color, val treeLight: Color, val trunk: Color,
    /** 빗줄기·눈송이·흰 꽃·물거품·갈매기 */
    val particle: Color,
    /** 노란 꽃·번개·등대 지붕 */
    val accent: Color,
    /** 물웅덩이 (비·천둥번개), 바닷가 얕은 물 */
    val water: Color = skyBottom,
    /** 하늘 가운데 색 (노을) */
    val skyMid: Color? = null,
    /** 바다: 수평선 쪽과 가까운 쪽 */
    val sea: Color = hillFar, val seaDeep: Color = hillMid,
)

object KagomScenePalette {
    private val clear = ScenePalette(
        skyTop = Color(0xFFA9DBF7), skyBottom = Color(0xFFE6F6FB),
        sun = Color(0xFFFFD86B), sunGlow = Color(0xFFFFF1B8),
        cloudLight = Color(0xFFFFFFFF), cloudShade = Color(0xFFE3F1FA),
        ridgeFar = Color(0xFFB9DDE4), ridgeNear = Color(0xFFA5D3C9),
        hillFar = Color(0xFFB8E3B4), hillMid = Color(0xFF9AD69A), hillNear = Color(0xFF7FC784), hillShade = Color(0xFF5FB06E),
        tree = Color(0xFF5FB37A), treeLight = Color(0xFF86CC92), trunk = Color(0xFF9A7B5B),
        particle = Color(0xFFFFFFFF), accent = Color(0xFFFFDF7E),
    )
    private val cloudy = ScenePalette(
        skyTop = Color(0xFFC3D0DB), skyBottom = Color(0xFFE9EEF2),
        sun = Color(0xFFF6F1DF), sunGlow = Color(0xFFF3F0E6),
        cloudLight = Color(0xFFF7F9FB), cloudShade = Color(0xFFCBD5DE),
        ridgeFar = Color(0xFFBFCFD3), ridgeNear = Color(0xFFAFC6C1),
        hillFar = Color(0xFFB7D3B9), hillMid = Color(0xFFA0C5A5), hillNear = Color(0xFF89B692), hillShade = Color(0xFF6E9F7C),
        tree = Color(0xFF6FA583), treeLight = Color(0xFF8DBB99), trunk = Color(0xFF8E7B67),
        particle = Color(0xFFFFFFFF), accent = Color(0xFFE9E3C4),
    )
    private val rain = ScenePalette(
        skyTop = Color(0xFFA8B9C9), skyBottom = Color(0xFFD8E1E8),
        sun = null, sunGlow = null,
        cloudLight = Color(0xFFD5DDE5), cloudShade = Color(0xFFA9B6C4),
        ridgeFar = Color(0xFFB1C2C8), ridgeNear = Color(0xFF9FB8B4),
        hillFar = Color(0xFFA6C6AD), hillMid = Color(0xFF8FB79A), hillNear = Color(0xFF78A786), hillShade = Color(0xFF5C8F70),
        tree = Color(0xFF5F9777), treeLight = Color(0xFF7DAE8C), trunk = Color(0xFF7D6C5C),
        particle = Color(0xFF7F9BB5), accent = Color(0xFFC7DAE6), water = Color(0xFFC7DAE6),
    )
    private val snow = ScenePalette(
        skyTop = Color(0xFFBDD3E6), skyBottom = Color(0xFFEDF3F8),
        sun = null, sunGlow = null,
        cloudLight = Color(0xFFFFFFFF), cloudShade = Color(0xFFDCE6EF),
        ridgeFar = Color(0xFFD5E1EC), ridgeNear = Color(0xFFC6D7E5),
        hillFar = Color(0xFFF3F7FA), hillMid = Color(0xFFE9F0F6), hillNear = Color(0xFFFBFDFE), hillShade = Color(0xFFD3E0EB),
        tree = Color(0xFF6E9C8C), treeLight = Color(0xFFFFFFFF), trunk = Color(0xFF8A7767),
        particle = Color(0xFFFFFFFF), accent = Color(0xFFC4D5E3),
    )
    private val thunder = ScenePalette(
        skyTop = Color(0xFFA2ABC2), skyBottom = Color(0xFFD0D4E0),
        sun = null, sunGlow = null,
        cloudLight = Color(0xFFC4C9D8), cloudShade = Color(0xFF9BA3B8),
        ridgeFar = Color(0xFFA9B3C2), ridgeNear = Color(0xFF98A8B0),
        hillFar = Color(0xFF98B5A3), hillMid = Color(0xFF83A690), hillNear = Color(0xFF6E977E), hillShade = Color(0xFF557F69),
        tree = Color(0xFF56876E), treeLight = Color(0xFF729F84), trunk = Color(0xFF6F6156),
        particle = Color(0xFF7C89A8), accent = Color(0xFFFFD95C), water = Color(0xFFBCC6D8),
    )
    /** BG-02 노을 언덕 */
    val sunset = ScenePalette(
        skyTop = Color(0xFFF7C2C4), skyBottom = Color(0xFFFFF2DA), skyMid = Color(0xFFFFD8B4),
        sun = Color(0xFFFFB25C), sunGlow = Color(0xFFFFE2A6),
        cloudLight = Color(0xFFFFF7F0), cloudShade = Color(0xFFFFCDBA),
        ridgeFar = Color(0xFFE6B3A6), ridgeNear = Color(0xFFD7A48E),
        hillFar = Color(0xFFCBD09C), hillMid = Color(0xFFB0C58E), hillNear = Color(0xFF93B884), hillShade = Color(0xFF6C9A6C),
        tree = Color(0xFF7FA872), treeLight = Color(0xFFBCCB8C), trunk = Color(0xFF94725A),
        particle = Color(0xFFFFFFFF), accent = Color(0xFFFFD98A),
    )
    /** BG-03 동해 바다 */
    val coast = ScenePalette(
        skyTop = Color(0xFFA6D8F5), skyBottom = Color(0xFFE6F6FA),
        sun = Color(0xFFFFBE64), sunGlow = Color(0xFFFFE6AE),
        cloudLight = Color(0xFFFFFFFF), cloudShade = Color(0xFFE3F1FA),
        ridgeFar = Color(0xFF9CC7CE), ridgeNear = Color(0xFF7DB79C),
        hillFar = Color(0xFFE3CFA0), hillMid = Color(0xFFEFDDB2), hillNear = Color(0xFFF8EACA), hillShade = Color(0xFFE7D2A2),
        tree = Color(0xFF4F9373), treeLight = Color(0xFF7DB88F), trunk = Color(0xFF8F7257),
        particle = Color(0xFFFFFFFF), accent = Color(0xFFE8705F), water = Color(0xFFA5E0EC),
        sea = Color(0xFF8CCFE9), seaDeep = Color(0xFF52A8D4),
    )

    fun of(weather: Weather): ScenePalette = when (weather) {
        Weather.CLEAR -> clear
        Weather.CLOUDY -> cloudy
        Weather.RAIN -> rain
        Weather.SNOW -> snow
        Weather.THUNDER -> thunder
    }
}
