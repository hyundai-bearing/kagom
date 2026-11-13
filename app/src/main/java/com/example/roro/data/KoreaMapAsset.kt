package com.example.roro.data

import android.content.Context
import com.example.roro.core.KoreaMap

/**
 * assets/regions/korea_map.txt를 한 번만 읽어 둔다. 메인 스레드에서 부르지 말 것 (≈1MB 텍스트).
 * 강릉 읍면 구획은 읽지 않는다: 시 단계 지도는 Figma SCR-05 layer=3처럼
 * 읍면 경계 없이 시 한 덩어리로 그린다 (2026-11-13). 구획 파일은 테스트 자료(src/test/resources/regions)로 옮겨 APK에 넣지 않는다.
 */
object KoreaMapAsset {
    const val PATH = "regions/korea_map.txt"

    @Volatile private var cached: KoreaMap? = null

    fun load(context: Context): KoreaMap = cached ?: synchronized(this) {
        cached ?: context.assets.open(PATH).bufferedReader().useLines { KoreaMap.parse(it) }.also { cached = it }
    }
}
