@file:OptIn(ExperimentalLayoutApi::class)

package com.example.roro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.roro.core.Gear
import com.example.roro.core.Examples
import com.example.roro.core.Landmarks
import com.example.roro.core.OilLevel
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomType

/**
 * 시연 패널 (디버그 빌드 전용). 차 없이 상태 엔진·보상·팝업 큐를 시연한다.
 * 레일/탭바 빈 곳을 길게 누르면 열린다. adb 명령은 MainActivity 주석 참고.
 */
@Composable
fun DebugPanel(c: KagomController, gear: Gear, onClose: () -> Unit) {
    val o by c.signals.overrides.collectAsState()
    val s by c.signals.snapshot.collectAsState()
    Box(Modifier.fillMaxSize().background(KagomColor.Overlay.dim).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.CenterEnd).widthIn(max = 560.dp).fillMaxHeight()
                .background(KagomColor.Surface.card).clickable(remember { MutableInteractionSource() }, null) {}
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("시연", style = KagomType.Heading24, color = KagomColor.Text.primary)
            Text("기어 $gear · 연료 ${s.fuelPercent ?: "-"}% · 배터리 ${s.socPercent ?: "-"}% · ${s.odometerKm?.toInt() ?: "-"} km",
                style = KagomType.Caption14, color = KagomColor.Text.tertiary)

            Group("기어") {
                Btn("P", gear == Gear.P) { c.demoSetGear(Gear.P) }
                Btn("D", gear == Gear.D) { c.demoSetGear(Gear.D) }
                Btn("센서 값", o.gear == null) { c.demoSetGear(null) }
            }
            Group("연료·배터리 %") {
                listOf(72, 33, 28, 18, 12, 8, 4).forEach { p ->
                    Btn("$p", o.fuelPercent == p) { c.signals.edit { it.copy(fuelPercent = p) } }
                }
                Btn(if (o.ev == true) "전기차 ✓" else "전기차", o.ev == true) { c.signals.edit { it.copy(ev = if (it.ev == true) null else true) } }
            }
            Group("엔진오일 (SEN-03)") {
                OilButtons(o.engineOil) { v -> c.signals.edit { it.copy(engineOil = v) } }
            }
            Group("브레이크액 (SEN-04)") {
                OilButtons(o.brakeFluid, withHigh = false) { v -> c.signals.edit { it.copy(brakeFluid = v) } }
            }
            Group("미션오일 (SEN-05)") {
                OilButtons(o.transmissionOil, withHigh = false) { v -> c.signals.edit { it.copy(transmissionOil = v) } }
            }
            Group("경고") {
                Btn("엔진 경고등", o.engineWarning == true) { c.signals.edit { it.copy(engineWarning = if (it.engineWarning == true) false else true) } }
                Btn("워셔액 부족", o.washerLow == true) { c.signals.edit { it.copy(washerLow = if (it.washerLow == true) false else true) } }
            }
            Group("주행거리 (도전과제·MNT)") {
                Btn("+10 km", false) { c.demoAddKm(10f) }
                Btn("+50 km", false) { c.demoAddKm(50f) }
                Btn("+1,000 km", false) { c.demoAddKm(1_000f) }
                Btn("9,840", false) { c.signals.edit { it.copy(odometerKm = 9_840f) } }
                Btn("10,020", false) { c.signals.edit { it.copy(odometerKm = 10_020f) } }
            }
            Group("랜드마크 방문 (위치 고정)") {
                Landmarks.all.forEach { lm -> Btn(lm.name, false) { c.demoVisit(lm.id) } }
                Btn("위치 해제", false) { c.demoVisit(null) }
            }
            Group("예시 데이터 (Figma 예시, 저장 안 함)") {
                val examples by c.demoExamples.collectAsState()
                Btn(if (examples) "켜짐 · 치장 예시 아이템 03~12" else "꺼짐", examples) { c.demoExamples.value = !examples }
            }
            Group("새 차량 (SCR-00 이어가기)") {
                val data by c.store.data.collectAsState()
                Btn(if (data.newVehicle) "이전 카곰 기록 발견 ✓" else "이전 카곰 기록 발견", data.newVehicle) { c.demoNewVehicle(!data.newVehicle); onClose() }
            }
            Group("데이터") {
                Btn("신호 덮어쓰기 해제", false) { c.signals.clear() }
                Btn("전체 초기화", false) { c.demoResetAll(); onClose() }
            }
            Group("팝업 미리보기 (저장 안 함)") {
                Examples.popups.forEach { example ->
                    Btn(example.label, false) { c.demoPopup.value = example.key; onClose() }
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = KagomType.Body16Strong, color = KagomColor.Text.secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OilButtons(current: OilLevel?, withHigh: Boolean = true, set: (OilLevel?) -> Unit) {
    Btn("정상", current == OilLevel.NORMAL) { set(OilLevel.NORMAL) }
    Btn("LOW", current == OilLevel.LOW) { set(OilLevel.LOW) }
    if (withHigh) Btn("HIGH", current == OilLevel.HIGH) { set(OilLevel.HIGH) }
    Btn("거의 없음", current == OilLevel.CRITICALLY_LOW) { set(OilLevel.CRITICALLY_LOW) }
    Btn("센서 값", current == null) { set(null) }
}

@Composable
private fun Btn(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(KagomRadius.r12))
            .background(if (on) KagomColor.Surface.inverse else KagomColor.Surface.subtle)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = KagomType.Body16Strong, color = if (on) KagomColor.Text.inverse else KagomColor.Text.primary) }
}
