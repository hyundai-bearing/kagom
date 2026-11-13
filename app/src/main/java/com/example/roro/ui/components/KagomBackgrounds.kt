package com.example.roro.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.example.roro.core.Items
import com.example.roro.core.Weather
import com.example.roro.ui.theme.KagomMotion

/**
 * 홈 배경 (배경 아이템 BG-0n)과 그 장면([BackgroundScene])을 잇는다. 디자인에 없어 직접 그린 장면이다 (가정).
 * 3D 렌더(assets/kagom)와 달리 그림 파일이 없고 KagomBackgroundScene.kt가 그린다.
 */
object KagomBackgrounds {
    /** [weather]는 "오늘 날씨"(BG-04)에만 쓴다. 날씨를 모르면(위치·통신 없음) 맑음을 그린다 */
    fun scene(itemId: String?, weather: Weather? = null): SceneKind? = when (itemId) {
        Items.WEATHER_BACKGROUND -> SceneKind.of(weather ?: Weather.CLEAR)
        "BG-01" -> SceneKind.CLEAR
        "BG-02" -> SceneKind.SUNSET
        "BG-03" -> SceneKind.COAST
        else -> null
    }

    fun has(itemId: String?): Boolean = scene(itemId) != null
}

/**
 * 지금 보여 줄 날씨 (KagomController.weather). 배경 그림은 홈·옷장·팝업·썸네일 여러 곳에서 그려서,
 * 화면마다 인자로 넘기지 않고 LocalLayoutMode처럼 앱 맨 위에서 한 번 준다.
 */
val LocalWeather = staticCompositionLocalOf<Weather?> { null }

/**
 * 배경 장면으로 칸을 꽉 채운다. 장면은 Full 홈 캔버스(1280×1080) 기준이고,
 * 비율이 다른 칸(Split 세로, 옷장 미리보기, 썸네일)에서는 땅이 남도록 아래쪽을 기준으로 자른다.
 * 배경 아이템이 아니면 아무것도 그리지 않는다.
 *
 * [animated]: 홈과 옷장 미리보기에서 기어 P일 때만 켠다 (가이드 5.2). 카드·팝업·썸네일은 멈춘 그림이다.
 */
@Composable
fun BackgroundArt(itemId: String?, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null, animated: Boolean = false) {
    val scene = KagomBackgrounds.scene(itemId, LocalWeather.current) ?: return
    // 날씨나 고른 배경이 바뀔 때 그림이 툭 바뀌지 않게 겹쳐서 넘긴다 (주행 중에는 날씨가 바뀌지 않는다, KagomController.weather)
    Crossfade(scene, modifier, tween(if (animated) KagomMotion.PROGRESS else 0), label = "background") { kind ->
        BackgroundScene(kind, animated, Modifier.fillMaxSize(), colorFilter)
    }
}
