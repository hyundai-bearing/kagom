package com.example.roro.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roro.core.PendingPopup
import com.example.roro.core.Progress
import com.example.roro.ui.components.BottomTabBar
import com.example.roro.ui.components.NavRail
import com.example.roro.ui.components.NavTab
import com.example.roro.ui.screens.ClosetScreen
import com.example.roro.ui.screens.DrivingToastView
import com.example.roro.ui.screens.HomeScreen
import com.example.roro.ui.screens.LandmarkUnlockModal
import com.example.roro.ui.screens.MaintenanceSheet
import com.example.roro.ui.screens.MapScreen
import com.example.roro.ui.screens.OnboardingFlow
import com.example.roro.ui.screens.OnboardingState
import com.example.roro.ui.screens.QuestsScreen
import com.example.roro.ui.screens.RewardModal
import com.example.roro.ui.screens.TierUpModal
import com.example.roro.ui.screens.landmarkViews
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomTheme
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode

/**
 * 앱 루트 (spec 2장 유저 플로우).
 * ViewMode 감지 → 카곰 없으면 온보딩(SCR-00/01/02) → 홈(SCR-03) ↔ 도전(04)·지도(05)·치장(06)
 * 레이어: 정비 시트(POP-05) → 팝업 큐(POP-02/03/04) → 주행 중 토스트(POP-01) → 시연 패널
 */
@Composable
fun KagomApp(c: KagomController) = KagomTheme {
    val mode = LocalLayoutMode.current
    val context = LocalContext.current
    val data by c.store.data.collectAsState()
    val gear by c.gear.collectAsState()
    val status by c.status.collectAsState()
    val popup by c.activePopup.collectAsState()
    val toast by c.toast.collectAsState()
    val examples by c.demoExamples.collectAsState()
    val popupPreview by c.demoPopup.collectAsState()
    val gearP = gear.isPark

    var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var debugOpen by remember { mutableStateOf(false) }
    val onboarding = remember { OnboardingState() }
    val profile = data.profile
    val current = if (profile == null) NavTab.HOME else tab

    // gear≠P: 정비 시트 조작 차단 → 닫는다
    LaunchedEffect(gearP) { if (!gearP) sheetOpen = false }
    BackHandler(enabled = sheetOpen || debugOpen) { if (debugOpen) debugOpen = false else sheetOpen = false }
    LaunchedEffect(profile == null) { if (profile == null) tab = NavTab.HOME }

    val debugGesture = if (c.debug) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { debugOpen = true }) } else Modifier
    val select: (NavTab) -> Unit = { if (profile != null) tab = it }

    val screen: @Composable () -> Unit = {
        if (profile == null) {
            OnboardingFlow(onboarding, gearP) { name, answers -> c.createProfile(name, answers) }
        } else when (current) {
            NavTab.HOME -> HomeScreen(profile, data, status, gearP) { if (gearP) sheetOpen = true }
            NavTab.QUESTS -> QuestsScreen(Progress.all(c.progressContext(data), data.completedQuests), data.exp)
            NavTab.MAP -> MapScreen(
                landmarkViews(data.visitedPlaces, data.pending.filterIsInstance<PendingPopup.Landmark>().map { it.landmarkId }.toSet()),
                gearP, c::equip,
            )
            NavTab.CLOSET -> ClosetScreen(profile, data, c.progressContext(data), gearP, c::saveEquipped, examples) { target, _ -> tab = target }
        }
    }

    Box(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        if (mode == LayoutMode.FULL) {
            Row(Modifier.fillMaxSize()) {
                NavRail(current, select, debugGesture)
                Box(Modifier.weight(1f).fillMaxHeight()) { screen() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { screen() }
                BottomTabBar(current, select, debugGesture)
            }
        }

        if (sheetOpen && profile != null) MaintenanceSheet(
            status = status, data = data, gearP = gearP,
            onClose = { sheetOpen = false },
            onRefilled = c::refilledEngineOil,
            onOilChanged = c::oilChanged,
            onSevere = c::setSevere,
            onRecommendedOil = c::setRecommendedOil,
            onToggleCheck = c::toggleCheck,
            onCompleteCheckup = c::completeCheckup,
            onFindShop = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("자동차 정비소")))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { Log.w("Kagom", "지도 앱 없음", it) }
            },
        )

        // 팝업 큐 (기어 P에서만 activePopup이 나온다)
        // 정비 조작이 끝나기 전에는 큐를 소비하지 않고 시트 뒤에서 기다린다.
        if (profile != null && !sheetOpen) key(popup?.key) { when (val p = popup) {
            is PendingPopup.Reward -> RewardModal(p, profile, data.equipped, { c.openReward(p.questId) }, { c.deferPopup(p) }) { equip -> c.finishPopup(p, equip) }
            is PendingPopup.Landmark -> LandmarkUnlockModal(p, profile, data.equipped) { equip -> c.finishPopup(p, equip) }
            is PendingPopup.TierUp -> TierUpModal(p, profile, data.equipped) { c.finishPopup(p, false) }
            null -> Unit
        } }

        // POP-01 주행 중 토스트
        var lastToast by remember { mutableStateOf<DrivingToast?>(null) }
        if (toast != null) lastToast = toast
        AnimatedVisibility(
            toast != null,
            Modifier.align(Alignment.TopCenter).padding(top = if (mode == LayoutMode.SPLIT) 20.dp else 24.dp),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
        ) {
            lastToast?.let { DrivingToastView(it, profile?.name ?: "카곰") }
        }

        if (debugOpen) DebugPanel(c, gear) { debugOpen = false }
        if (c.debug && popupPreview != null) key(popupPreview) {
            PopupPreview(popupPreview!!, gearP) { c.demoPopup.value = null }
        }
    }
}
