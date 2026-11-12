package com.example.roro.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.roro.R

/**
 * 홈 배경 그림 (배경 아이템 BG-0n). 디자인에 없어 직접 그린 벡터다 (가정).
 * 3D 렌더(assets/kagom)와 달리 스크립트로 만들지 않고 res/drawable/bg_*.xml을 바로 고친다.
 */
object KagomBackgrounds {
    fun drawable(itemId: String?): Int? = when (itemId) {
        "BG-01" -> R.drawable.bg_meadow
        "BG-02" -> R.drawable.bg_sunset
        "BG-03" -> R.drawable.bg_coast
        else -> null
    }
}

/**
 * 배경 그림으로 칸을 꽉 채운다. 그림은 Full 홈 캔버스(1280×1080) 기준이고,
 * 비율이 다른 칸(Split 세로, 옷장 미리보기, 썸네일)에서는 땅이 남도록 아래쪽을 기준으로 자른다.
 * 배경 아이템이 아니면 아무것도 그리지 않는다.
 */
@Composable
fun BackgroundArt(itemId: String?, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val res = KagomBackgrounds.drawable(itemId) ?: return
    Image(
        painterResource(res), contentDescription = null, modifier = modifier,
        alignment = Alignment.BottomCenter, contentScale = ContentScale.Crop, colorFilter = colorFilter,
    )
}
