package com.example.roro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.roro.core.Checkup
import com.example.roro.core.Expression
import com.example.roro.core.Examples
import com.example.roro.core.Items
import com.example.roro.core.Landmarks
import com.example.roro.core.MaintenanceState
import com.example.roro.core.OilLevel
import com.example.roro.core.StateEngine
import com.example.roro.core.VehicleSnapshot
import com.example.roro.data.AppData
import com.example.roro.data.Profile
import com.example.roro.ui.screens.HomeScreen
import com.example.roro.ui.screens.LandmarkUnlockModal
import com.example.roro.ui.screens.MaintenanceSheet
import com.example.roro.ui.screens.RewardModal
import com.example.roro.ui.screens.TierUpModal
import com.example.roro.ui.theme.KagomColor
import java.time.LocalDate

/** 디버그 전용 EX- 시연 데이터. 실제 프로필·보상·정비 기록을 저장하지 않고 네 팝업을 비교한다. */
@Composable
fun PopupPreview(kind: String, gearP: Boolean, onClose: () -> Unit) {
    val day = remember { LocalDate.now().toEpochDay() }
    val profile = remember { Profile(Examples.popupName, Examples.popupSkin, "AAB", day) }
    var opened by rememberSaveable(kind) { mutableStateOf(kind == "reveal") }
    var checks by rememberSaveable(kind) { mutableStateOf(listOf(Checkup.items.first())) }
    var severe by rememberSaveable(kind) { mutableStateOf(false) }
    var recommended by rememberSaveable(kind) { mutableStateOf(true) }
    var refilled by rememberSaveable(kind) { mutableStateOf(false) }
    var oilChanged by rememberSaveable(kind) { mutableStateOf(false) }
    var sheetOpen by rememberSaveable(kind) { mutableStateOf(kind.startsWith("maintenance")) }
    var completed by rememberSaveable(kind) { mutableStateOf(kind == "maintenance-done") }
    val maintenance = MaintenanceState(severe = severe, recommendedOil = recommended,
        checkupRoundsDone = if (completed) 1 else 0,
        oilChangedKm = if (oilChanged) 7_840f else 0f, oilChangedDay = if (oilChanged) day else day - 90, engineOilDismissed = refilled)
    val data = AppData(profile = profile, exp = 120, maintenance = maintenance,
        checkupChecks = checks.toSet(), owned = Items.all.map { it.id }.toSet())
    val status = StateEngine.evaluate(VehicleSnapshot(fuelPercent = if (kind == "maintenance-ev") null else 72,
        socPercent = if (kind == "maintenance-ev") 72 else null, engineOil = OilLevel.LOW,
        brakeFluid = OilLevel.LOW, odometerKm = 10_000f), maintenance, 0, day, day)
    // 진행 중 주행 전환은 샘플을 소비하지 않고 보이지 않게만 한다.
    BackHandler { if (sheetOpen) sheetOpen = false }
    Box(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        HomeScreen(
            profile, data, status.copy(expression = Expression.IDLE), gearP,
            onOpenSheet = { sheetOpen = true },
            onBlockedSheet = {},
        )
        if (gearP) {
            MaintenanceSheet(sheetOpen, status, data, true, { sheetOpen = false }, { refilled = true },
                { oilChanged = true }, { severe = it }, { recommended = it },
                { item -> checks = if (item in checks) checks - item else checks + item },
                { completed = true; checks = emptyList() }, {})
            if (!sheetOpen) when {
                kind == "reward" || kind == "reveal" -> RewardModal(
                    Examples.popupReward(opened), profile, data.equipped,
                    { opened = true }, onClose, { onClose() },
                )
                kind.startsWith("landmark") -> {
                    val lm = Landmarks.byId(kind.substringAfter(':', "LM-GN-01")) ?: Landmarks.all.last()
                    LandmarkUnlockModal(Examples.popupLandmark(lm, day),
                        profile, data.equipped) { onClose() }
                }
                kind == "level" -> TierUpModal(Examples.popupLevel, profile, data.equipped, onClose)
            }
        }
    }
}
