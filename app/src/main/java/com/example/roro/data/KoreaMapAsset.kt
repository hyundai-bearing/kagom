package com.example.roro.data

import android.content.Context
import com.example.roro.core.KoreaMap
import com.example.roro.core.MapRegion

/** 지도 데이터를 한 번만 읽어 둔다. 메인 스레드에서 부르지 말 것 */
object KoreaMapAsset {
    const val PATH = "regions/korea_map.txt"
    const val L3_DIR = "regions/l3"

    @Volatile private var cached: KoreaMap? = null
    private val areas = HashMap<String, List<MapRegion>>()

    /** L1 시·도 + L2 시·군·구 */
    fun load(context: Context): KoreaMap = cached ?: synchronized(this) {
        cached ?: context.assets.open(PATH).bufferedReader().useLines { KoreaMap.parse(it) }.also { cached = it }
    }

    /** L3 읍·면·동: 시·도 하나의 파일 ([file] = P 줄의 L3 파일 이름). 없으면 빈 목록 */
    fun loadAreas(context: Context, file: String?): List<MapRegion> {
        if (file == null) return emptyList()
        synchronized(areas) { areas[file]?.let { return it } }
        val list = runCatching {
            context.assets.open("$L3_DIR/$file.txt").bufferedReader().useLines { KoreaMap.parseAreas(it) }
        }.getOrDefault(emptyList())
        synchronized(areas) { areas[file] = list }
        return list
    }
}
