package com.example.roro.data

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 여부 (NFR-01, Q2): Android CarUxRestrictionsManager가 "주의 분산 최적화 필요"라고 하면 주행 중으로 본다.
 * 신호 대기처럼 멈춰 있으면 시스템 판단에 따라 정차로 본다 (Q3).
 * 차량 서비스에 연결하지 못하면 정차로 두고 앱은 계속 동작한다.
 */
class DrivingStateSource(private val context: Context) {

    private val state = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = state.asStateFlow()

    /** 디버그 메뉴에서 주행 상태를 강제로 넣을 때 (null = 시스템 값 사용) */
    val override = MutableStateFlow<Boolean?>(null)

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        runCatching {
            val c = Car.createCar(context) ?: error("Car.createCar null")
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            state.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            m.registerListener { r -> state.value = r.isRequiresDistractionOptimization }
            car = c
            manager = m
        }.onFailure { Log.w(TAG, "CarUxRestrictionsManager 연결 실패 → 정차로 간주", it) }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private companion object {
        const val TAG = "KagomDriving"
    }
}
