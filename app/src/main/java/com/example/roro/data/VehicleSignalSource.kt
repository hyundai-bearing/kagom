package com.example.roro.data

import com.example.roro.core.Gear
import com.example.roro.core.OilLevel
import com.example.roro.core.Signal
import com.example.roro.core.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 차 신호를 받는 단일 창구 (가이드 9장: 센서 API는 어댑터 인터페이스로 추상화).
 * Pleos 구현과 Mock 구현을 바꿔 끼울 수 있다. 리스너 콜백에서는 값만 저장한다.
 */
interface VehicleSignalSource {
    val snapshot: StateFlow<VehicleSnapshot>
    fun start()
    /** 등록한 리스너를 모두 해제하고 SDK를 놓는다 */
    fun stop()
}

/** SDK 없이 값을 직접 넣는 구현 (시연·테스트용). 기본값 = Figma 홈 normal 상태 */
class MockVehicleSignalSource(initial: VehicleSnapshot = DEFAULT) : VehicleSignalSource {
    private val state = MutableStateFlow(initial)
    override val snapshot: StateFlow<VehicleSnapshot> = state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    fun set(transform: (VehicleSnapshot) -> VehicleSnapshot) = state.update(transform)

    companion object {
        val DEFAULT = VehicleSnapshot(
            fuelPercent = 72,
            engineOil = OilLevel.NORMAL, brakeFluid = OilLevel.NORMAL, transmissionOil = OilLevel.NORMAL,
            engineWarning = false, washerLow = false, odometerKm = 1_240f, gear = Gear.P,
            unsupported = setOf(Signal.SOC),
        )
    }
}

/**
 * 시연 메뉴·adb로 넣은 값으로 일부 신호를 덮어쓴다.
 * Virtual Console에 없는 오일·브레이크액·미션오일·엔진 이상·워셔액을 시연할 때 쓴다.
 */
class OverridableSignalSource(private val base: VehicleSignalSource, scope: CoroutineScope) : VehicleSignalSource {

    /** null = 덮어쓰지 않음 */
    data class Overrides(
        val fuelPercent: Int? = null,
        val socPercent: Int? = null,
        val engineOil: OilLevel? = null,
        val brakeFluid: OilLevel? = null,
        val transmissionOil: OilLevel? = null,
        val engineWarning: Boolean? = null,
        val washerLow: Boolean? = null,
        val odometerKm: Float? = null,
        val gear: Gear? = null,
        /** 전기차처럼 보이게 (연료 숨기고 배터리 사용) */
        val ev: Boolean? = null,
    )

    val overrides = MutableStateFlow(Overrides())

    override val snapshot: StateFlow<VehicleSnapshot> = combine(base.snapshot, overrides) { s, o ->
        val forced = buildSet {
            if (o.fuelPercent != null) add(Signal.FUEL)
            if (o.socPercent != null) add(Signal.SOC)
            if (o.engineOil != null) add(Signal.ENGINE_OIL)
            if (o.brakeFluid != null) add(Signal.BRAKE_FLUID)
            if (o.transmissionOil != null) add(Signal.TRANSMISSION_OIL)
            if (o.engineWarning != null) add(Signal.ENGINE_WARNING)
            if (o.washerLow != null) add(Signal.WASHER)
            if (o.odometerKm != null) add(Signal.ODOMETER)
            if (o.gear != null) add(Signal.GEAR)
        }
        var out = s.copy(
            fuelPercent = o.fuelPercent ?: s.fuelPercent,
            socPercent = o.socPercent ?: s.socPercent,
            engineOil = o.engineOil ?: s.engineOil,
            brakeFluid = o.brakeFluid ?: s.brakeFluid,
            transmissionOil = o.transmissionOil ?: s.transmissionOil,
            engineWarning = o.engineWarning ?: s.engineWarning,
            washerLow = o.washerLow ?: s.washerLow,
            odometerKm = o.odometerKm ?: s.odometerKm,
            gear = o.gear ?: s.gear,
            unsupported = s.unsupported - forced,
        )
        when (o.ev) {
            // 전기차 시연: 넣은 연료 % 값을 배터리 SOC로 쓴다
            true -> out = out.copy(socPercent = o.socPercent ?: o.fuelPercent ?: out.socPercent ?: out.fuelPercent,
                unsupported = out.unsupported + Signal.FUEL - Signal.SOC)
            false -> out = out.copy(unsupported = out.unsupported + Signal.SOC)
            null -> Unit
        }
        out
    }.stateIn(scope, SharingStarted.Eagerly, base.snapshot.value)

    fun edit(transform: (Overrides) -> Overrides) = overrides.update(transform)
    fun clear() { overrides.value = Overrides() }
    override fun start() = base.start()
    override fun stop() = base.stop()
}
