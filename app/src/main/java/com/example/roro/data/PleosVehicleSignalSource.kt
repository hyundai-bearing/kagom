package com.example.roro.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import android.content.Context
import android.util.Log
import com.example.roro.core.Gear
import com.example.roro.core.OilLevel
import com.example.roro.core.Signal
import com.example.roro.core.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK 구현 (SEN-01~07 + 주행거리 + 기어).
 * 호출 순서: Vehicle(context) → initialize() → check…Capabilities → get… → register… → unregister… → release()
 *
 * - 항목마다 지원 여부를 먼저 확인하고, 미지원이면 [VehicleSnapshot.unsupported]에 넣어 "확인 불가"로 보인다
 * - initialize() 실패, 미지원, 읽기 실패에도 앱은 멈추지 않는다
 * - 리스너 콜백에서는 값만 저장한다
 */
class PleosVehicleSignalSource(private val context: Context) : VehicleSignalSource {

    private val state = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = state.asStateFlow()

    private var vehicle: Vehicle? = null
    private val unregisters = mutableListOf<() -> Unit>()

    private fun set(transform: (VehicleSnapshot) -> VehicleSnapshot) = state.update(transform)
    private fun markUnsupported(signal: Signal) = set { it.copy(unsupported = it.unsupported + signal) }
    private fun failed(what: String): (Exception) -> Unit = { e -> Log.w(TAG, "$what 실패: ${e.message}") }

    /** 지원하면 [subscribe]를 실행. 확인 자체가 실패하면 미지원으로 본다 */
    private fun whenSupported(signal: Signal, check: (ok: (CapabilityConfig) -> Unit, fail: (Exception) -> Unit) -> Unit, subscribe: () -> Unit) {
        runCatching {
            check({ cfg ->
                if (cfg.accessType == AccessType.NONE) markUnsupported(signal)
                else runCatching(subscribe).onFailure { Log.w(TAG, "$signal 구독 실패", it) }
            }, { e ->
                Log.w(TAG, "$signal 지원 확인 실패: ${e.message}")
                markUnsupported(signal)
            })
        }.onFailure {
            Log.w(TAG, "$signal 지원 확인 예외", it)
            markUnsupported(signal)
        }
    }

    override fun start() {
        val v = runCatching { Vehicle(context).also { it.initialize() } }.getOrElse {
            Log.e(TAG, "Vehicle initialize 실패 → 모든 항목 확인 불가", it)
            set { it.copy(unsupported = Signal.entries.toSet()) }
            return
        }
        vehicle = v

        // SEN-02 연료
        whenSupported(Signal.FUEL, { ok, fail -> v.getFuel().checkFuelPercentCapabilities(ok, fail) }) {
            v.getFuel().getFuelPercent({ p -> set { it.copy(fuelPercent = p) } }, failed("getFuelPercent"))
            val l = object : FuelPercentListener {
                override fun onFuelPercentUpdated(p0: Int?) = set { it.copy(fuelPercent = p0) }
                override fun onFailed(p0: Exception) { failed("FuelPercent")(p0) }
            }
            v.getFuel().registerFuelPercent(l); unregisters += { v.getFuel().unregisterFuelPercent(l) }
        }

        // SEN-01 배터리 SOC
        whenSupported(Signal.SOC, { ok, fail -> v.getEvBattery().checkEvBatteryStateOfChargeCapabilities(ok, fail) }) {
            v.getEvBattery().getEvBatteryStateOfCharge({ p -> set { it.copy(socPercent = p) } }, failed("getEvBatteryStateOfCharge"))
            val l = object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(p0: Int?) = set { it.copy(socPercent = p0) }
                override fun onFailed(p0: Exception) { failed("EvBatteryStateOfCharge")(p0) }
            }
            v.getEvBattery().registerEvBatteryStateOfCharge(l); unregisters += { v.getEvBattery().unregisterEvBatteryStateOfCharge(l) }
        }

        // SEN-03/04/05 오일·액 레벨
        val safety = v.getSafety()
        fun oil(state: OilLevelState?): OilLevel? = state?.let { s -> OilLevel.entries.firstOrNull { it.name == s.name } }

        whenSupported(Signal.ENGINE_OIL, { ok, fail -> safety.checkEngineOilLevelStateCapabilities({ cfg, _ -> ok(cfg) }, fail) }) {
            safety.getEngineOilLevelState({ s -> set { it.copy(engineOil = oil(s)) } }, failed("getEngineOilLevelState"))
            val l = oilListener("EngineOil") { s -> set { it.copy(engineOil = oil(s)) } }
            safety.registerEngineOilLevelState(l); unregisters += { safety.unregisterEngineOilLevelState(l) }
        }
        whenSupported(Signal.BRAKE_FLUID, { ok, fail -> safety.checkBrakeFluidLevelStateCapabilities({ cfg, _ -> ok(cfg) }, fail) }) {
            safety.getBrakeFluidLevelState({ s -> set { it.copy(brakeFluid = oil(s)) } }, failed("getBrakeFluidLevelState"))
            val l = oilListener("BrakeFluid") { s -> set { it.copy(brakeFluid = oil(s)) } }
            safety.registerBrakeFluidLevelState(l); unregisters += { safety.unregisterBrakeFluidLevelState(l) }
        }
        whenSupported(Signal.TRANSMISSION_OIL, { ok, fail -> safety.checkTransmissionOilLevelStateCapabilities({ cfg, _ -> ok(cfg) }, fail) }) {
            safety.getTransmissionOilLevelState({ s -> set { it.copy(transmissionOil = oil(s)) } }, failed("getTransmissionOilLevelState"))
            val l = oilListener("TransmissionOil") { s -> set { it.copy(transmissionOil = oil(s)) } }
            safety.registerTransmissionOilLevelState(l); unregisters += { safety.unregisterTransmissionOilLevelState(l) }
        }

        // SEN-07 워셔액 부족
        whenSupported(Signal.WASHER, { ok, fail -> safety.checkLowWasherFluidWarningStateCapabilities(ok, fail) }) {
            safety.hasLowWasherFluidWarning({ b -> set { it.copy(washerLow = b) } }, failed("hasLowWasherFluidWarning"))
            val l = object : LowWasherFluidWaringListener {
                override fun onLowWasherFluidWaringUpdated(p0: Boolean?) = set { it.copy(washerLow = p0) }
                override fun onFailed(p0: Exception) { failed("LowWasherFluid")(p0) }
            }
            safety.registerLowWasherFluidWaringState(l); unregisters += { safety.unregisterLowWasherFluidWaringState(l) }
        }

        // 누적 주행거리 (MNT-01/02, 도전과제)
        whenSupported(Signal.ODOMETER, { ok, fail -> v.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, fail) }) {
            v.getOdometer().getCurrentDistanceDriven({ km -> set { it.copy(odometerKm = km) } }, failed("getCurrentDistanceDriven"))
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(p0: Float?) = set { it.copy(odometerKm = p0) }
                override fun onFailed(p0: Exception) { failed("DistanceDriven")(p0) }
            }
            v.getOdometer().registerDistanceDriven(l); unregisters += { v.getOdometer().unregisterDistanceDriven(l) }
        }

        // 기어 (P 잠금 규칙). Gear에는 지원 확인 API가 없어, 첫 읽기가 실패하면 미지원으로 본다
        runCatching {
            val gear = v.getGear()
            gear.getCurrentGearSelectionState({ g -> set { it.copy(gear = gearOf(g)) } }, { e ->
                Log.w(TAG, "기어 읽기 실패 → 미지원: ${e.message}"); markUnsupported(Signal.GEAR)
            })
            val l = object : GearSelectionListener {
                override fun onSelected(p0: VehicleGear?) = set { it.copy(gear = gearOf(p0)) }
                override fun onFailed(p0: Exception) { failed("GearSelection")(p0) }
            }
            gear.registerGearSelection(l); unregisters += { gear.unregisterGearSelection(l) }
        }.onFailure { Log.w(TAG, "기어 구독 실패", it); markUnsupported(Signal.GEAR) }
    }

    private fun gearOf(g: VehicleGear?): Gear? = when (g) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        null, VehicleGear.GEAR_UNKNOWN -> null
        else -> Gear.D
    }

    private fun oilListener(name: String, onValue: (OilLevelState?) -> Unit) = object : OilLevelStateListener {
        override fun onOilLevelUpdated(p0: OilLevelState?) = onValue(p0)
        override fun onFailed(p0: Exception) { failed(name)(p0) }
    }

    override fun stop() {
        unregisters.forEach { runCatching(it).onFailure { e -> Log.w(TAG, "unregister 실패", e) } }
        unregisters.clear()
        runCatching { vehicle?.release() }
        vehicle = null
    }

    private companion object {
        const val TAG = "KagomVehicle"
    }
}
