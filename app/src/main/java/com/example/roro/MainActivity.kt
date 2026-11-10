package com.example.roro

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.roro.core.Gear
import com.example.roro.core.Examples
import com.example.roro.core.OilLevel
import com.example.roro.data.LocationSource
import com.example.roro.ui.KagomApp
import com.example.roro.ui.KagomController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 카곰(Kagom): Pleos Connect 인포테인먼트용 반려 캐릭터 앱.
 * 기획: spec.pdf · 디자인: Figma "Kagom App" · 구현 가이드: docs/design/<날짜>/Kagom_Agent_Handoff_Guide*.pdf (최신은 docs/design/README.md)
 *
 * 디버그 빌드 시연 명령 (차 없이 값 넣기). 앱 안에서는 레일/탭바 빈 곳을 길게 누르면 시연 패널이 열린다.
 *   adb shell am start -n com.example.roro/.MainActivity --es source mock                    # Mock 신호로 시작
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_gear D        # 기어 P/D
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ei demo_fuel 12       # 연료·배터리 %
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_engine_oil LOW # NORMAL/LOW/HIGH/CRITICALLY_LOW
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_brake LOW
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_washer true
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_ev true      # 전기차 신호
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ei demo_odo 10020
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_visit LM-GN-01  # 랜드마크 방문
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_reset true     # 전체 초기화
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_examples true  # Figma 예시 데이터 켜기/끄기
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --ez demo_new_vehicle true # 새 차에서 이전 카곰 기록 발견 (SCR-00 Carry-over)
 *   adb shell am start --activity-single-top -n com.example.roro/.MainActivity --es demo_popup reward  # reward/reveal/landmark:LM-GN-01/level/maintenance/maintenance-ev/maintenance-done/off (저장 안 함)
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: KagomController

    /** 위치는 런타임 권한 (랜드마크 방문 판정). 거절해도 나머지 기능은 그대로 */
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.locationSource.restart()
    }

    private val debuggable get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = KagomController(
            applicationContext, scope,
            useMock = debuggable && intent?.getStringExtra("source") == "mock",
            debug = debuggable,
        )
        controller.start()
        if (!controller.locationSource.hasPermission()) runCatching { locationPermission.launch(LocationSource.PERMISSION) }
        readDemoExtras(intent)
        setContent { KagomApp(controller) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDemoExtras(intent)
    }

    private fun readDemoExtras(intent: Intent?) {
        if (!debuggable || intent == null) return
        val c = controller
        intent.getStringExtra("demo_popup")?.let { c.demoPopup.value = it.takeIf { name ->
            Examples.popups.any { it.key == name }
        } }
        if (intent.getBooleanExtra("demo_reset", false)) c.demoResetAll()
        intent.getStringExtra("demo_gear")?.let { g -> c.demoSetGear(Gear.entries.firstOrNull { it.name == g.uppercase() }) }
        if (intent.hasExtra("demo_fuel")) intent.getIntExtra("demo_fuel", -1).takeIf { it in 0..100 }?.let { p ->
            c.signals.edit { it.copy(fuelPercent = p) }
        }
        intent.getStringExtra("demo_engine_oil")?.let { v -> c.signals.edit { it.copy(engineOil = OilLevel.entries.firstOrNull { o -> o.name == v }) } }
        intent.getStringExtra("demo_brake")?.let { v -> c.signals.edit { it.copy(brakeFluid = OilLevel.entries.firstOrNull { o -> o.name == v }) } }
        if (intent.hasExtra("demo_washer")) c.signals.edit { it.copy(washerLow = intent.getBooleanExtra("demo_washer", false)) }
        if (intent.hasExtra("demo_ev")) c.signals.edit { it.copy(ev = intent.getBooleanExtra("demo_ev", false)) }
        if (intent.hasExtra("demo_odo")) c.signals.edit { it.copy(odometerKm = intent.getIntExtra("demo_odo", 0).toFloat()) }
        intent.getStringExtra("demo_visit")?.let { c.demoVisit(it.takeIf { id -> id.isNotBlank() }) }
        if (intent.hasExtra("demo_examples")) c.demoExamples.value = intent.getBooleanExtra("demo_examples", false)
        if (intent.hasExtra("demo_new_vehicle")) c.demoNewVehicle(intent.getBooleanExtra("demo_new_vehicle", false))
    }

    override fun onDestroy() {
        controller.stop()   // 리스너 해제 + release
        scope.cancel()
        super.onDestroy()
    }
}
