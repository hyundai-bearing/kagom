package com.example.roro.data

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

data class GeoPoint(val lat: Double, val lon: Double)

/**
 * 차 위치 (Pleos FusedLocation SDK). 지도 내 위치 표시와 랜드마크 방문 판정(SCR-05)에 쓰고 저장하지 않는다.
 * 저장하는 것은 방문한 랜드마크 id와 날짜뿐. 실패하거나 미지원이면 위치가 null로 남는다.
 */
class LocationSource(private val context: Context) {

    private val state = MutableStateFlow<GeoPoint?>(null)
    val location: StateFlow<GeoPoint?> = state.asStateFlow()

    /** 시연용 고정 위치 (시연 메뉴·adb) */
    val override = MutableStateFlow<GeoPoint?>(null)

    val current: Flow<GeoPoint?> = combine(state, override) { l, o -> o ?: l }

    private var fused: FusedLocation? = null

    fun hasPermission() = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private val listener = object : FusedLocationListener {
        override fun onChanged(p0: LocationData?) {
            if (p0 != null) state.value = GeoPoint(p0.latitude, p0.longitude)
        }
        override fun onError(exception: Exception) { Log.w(TAG, "위치 오류: ${exception.message}") }
    }

    fun start() {
        if (fused != null) return
        if (!hasPermission()) { Log.i(TAG, "위치 권한 없음 → 허용되면 시작"); return }
        val f = runCatching { FusedLocation(context).also { it.initialize() } }.getOrElse {
            Log.w(TAG, "FusedLocation initialize 실패 → 랜드마크 방문 판정 멈춤", it); return
        }
        fused = f
        runCatching {
            f.getLastKnownLocation(listener)
            f.registerFusedLocationCallback(listener)
        }.onFailure { Log.w(TAG, "위치 구독 실패", it) }
    }

    fun stop() {
        runCatching { fused?.unregisterFusedLocationCallback(listener) }
        runCatching { fused?.release() }
        fused = null
    }

    fun restart() { stop(); start() }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "KagomLocation"
    }
}
