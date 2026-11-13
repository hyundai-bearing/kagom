package com.example.roro.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.roro.core.Category
import com.example.roro.core.Checkup
import com.example.roro.core.PendingPopup
import com.example.roro.core.Progress
import com.example.roro.ui.components.BottomTabBar
import com.example.roro.ui.components.IviToast
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.LocalWeather
import com.example.roro.ui.components.NavRail
import com.example.roro.ui.components.NavTab
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.screens.CarryOverScreen
import com.example.roro.ui.screens.ClosetScreen
import com.example.roro.ui.screens.DetailAvatar
import com.example.roro.ui.screens.DriveLockScreen
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
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomTheme
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import kotlinx.coroutines.delay

/** 정차 중 사용자 동작의 결과 알림 (정비 시트 버튼). [id]가 달라야 같은 문구도 다시 뜬다 */
private data class Notice(val text: String, val id: Long = System.nanoTime())

/**
 * 앱 루트 (spec 2장 유저 플로우).
 * ViewMode 감지 → 카곰 없으면 온보딩(SCR-00/01/02) → 홈(SCR-03) ↔ 도전(04)·지도(05)·치장(06)
 * 레이어: 정비 시트(POP-05) → 팝업 큐(POP-02/03/04) → 주행 중 토스트(POP-01) → 시연 패널
 */
@Composable
fun KagomApp(c: KagomController) = KagomTheme {
    val weather by c.weather.collectAsState()
    CompositionLocalProvider(LocalWeather provides weather) { KagomAppContent(c) }
}

@Composable
private fun KagomAppContent(c: KagomController) {
    val mode = LocalLayoutMode.current
    val context = LocalContext.current
    val data by c.store.data.collectAsState()
    val gear by c.gear.collectAsState()
    val status by c.status.collectAsState()
    val popup by c.activePopup.collectAsState()
    val toast by c.toast.collectAsState()
    val examples by c.demoExamples.collectAsState()
    val popupPreview by c.demoPopup.collectAsState()
    val demoAct by c.demoAct.collectAsState()
    val demoBackground by c.demoBackground.collectAsState()
    val gearP = gear.isPark

    var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var debugOpen by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    /** 치장 [지도에서 보기]로 넘어올 때 지도가 바로 보여 줄 랜드마크 */
    var mapFocus by remember { mutableStateOf<String?>(null) }
    val onboarding = remember { OnboardingState() }
    val profile = data.profile
    val current = if (profile == null || data.newVehicle) NavTab.HOME else tab

    // gear≠P: 정비 시트 조작 차단 → 닫는다
    LaunchedEffect(gearP) { if (!gearP) sheetOpen = false }
    LaunchedEffect(profile == null) { if (profile == null) tab = NavTab.HOME }
    LaunchedEffect(notice) { if (notice != null) { delay(2_500); notice = null } }
    // 뒤로 키: 다른 탭에서는 홈으로. 화면 안의 뒤로 처리(지도 단계, 치장 패널)가 먼저다 (나중에 등록한 쪽이 먼저 받는다)
    BackHandler(enabled = profile != null && current != NavTab.HOME) { tab = NavTab.HOME }

    val debugGesture = if (c.debug) Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { debugOpen = true }) } else Modifier
    // 새 차에서 이전 기록을 찾았으면(SCR-00 Carry-over) 고를 때까지 온보딩처럼 다른 탭으로 못 간다
    val carryOver = profile != null && data.newVehicle
    val select: (NavTab) -> Unit = { if (profile != null && !carryOver) tab = it }
    LaunchedEffect(demoAct) { if (demoAct != null) select(NavTab.HOME) }      // 시연에서 고른 동작은 홈에서 튼다

    val screen: @Composable () -> Unit = {
        // 탭 전환·카곰 생성 직후: 앞 화면이 먼저 옅어지고 다음 화면이 나타난다. 주행 중에는 바로 바꾼다
        AnimatedContent(
            targetState = if (profile == null || carryOver) null else current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val ms = KagomMotion.ms(KagomMotion.SCREEN, gearP)
                (fadeIn(tween(ms, delayMillis = ms / 3)) togetherWith fadeOut(tween(ms / 3))) using null
            },
            label = "screen",
        ) { target ->
            if (profile != null && carryOver) {
                // 초기 설정이라 주행 중에는 잠근다 (가이드 5.2)
                if (gearP) CarryOverScreen(profile, data, c.progressContext(data).drivenKm, c::carryOverKeep, c::carryOverRestart)
                else DriveLockScreen("기어를 P에 두면 이어서 고를 수 있어요.")
            } else if (profile == null || target == null) {
                OnboardingFlow(onboarding, gearP) { name, answers -> c.createProfile(name, answers) }
            } else when (target) {
                NavTab.HOME -> {
                    // 시연 패널의 배경 미리보기는 홈에만 깔고 저장된 장착은 건드리지 않는다
                    val shown = demoBackground?.let { data.copy(equipped = data.equipped.with(Category.BACKGROUND, it)) } ?: data
                    HomeScreen(profile, shown, status, gearP, demoAct, { c.demoAct.value = null }) { if (gearP) sheetOpen = true }
                }
                NavTab.QUESTS -> QuestsScreen(
                    Progress.all(c.progressContext(data), data.completedQuests), data.questDoneAt, gearP,
                    DetailAvatar(profile.skin, status.expression, wornLayers(data.equipped.head, data.equipped.top, data.equipped.bottom)),
                )
                NavTab.MAP -> {
                    val location by c.locationSource.current.collectAsState(initial = null)
                    val worn = wornLayers(data.equipped.head, data.equipped.top, data.equipped.bottom)
                    val views = remember(data.visits, data.pending) {
                        landmarkViews(data.visits, data.pending.filterIsInstance<PendingPopup.Landmark>().map { it.landmarkId }.toSet())
                    }
                    MapScreen(
                        views = views,
                        gearP = gearP,
                        onEquip = c::equip,
                        onNavigate = { landmark ->
                            if (gearP) {
                                val destination = "${landmark.lat},${landmark.lon}"
                                val route = Uri.parse("google.navigation:q=$destination")
                                val directions = Uri.parse("https://www.google.com/maps/dir/")
                                    .buildUpon().appendQueryParameter("api", "1")
                                    .appendQueryParameter("destination", destination)
                                    .appendQueryParameter("travelmode", "driving").appendQueryParameter("dir_action", "navigate")
                                    .build()
                                val opened = listOf(route, directions).any { uri ->
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
                                }
                                if (!opened) notice = Notice("길 안내를 열 수 없어요")
                            }
                        },
                        location = location,
                        detailAvatar = DetailAvatar(profile.skin, status.expression, worn),
                        focusLandmarkId = mapFocus,
                        onFocusShown = { mapFocus = null },
                    ) { modifier ->
                        KagomCharacter(profile.skin, status.expression, modifier, worn = worn)
                    }
                }
                NavTab.CLOSET -> ClosetScreen(profile, data, c.progressContext(data), gearP, status.expression, c::saveEquipped, c::saveOutfit, examples) { next, landmarkId ->
                    mapFocus = landmarkId.takeIf { next == NavTab.MAP }
                    tab = next
                }
            }
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

        if (profile != null) MaintenanceSheet(
            visible = sheetOpen,
            status = status, data = data, gearP = gearP,
            onClose = { sheetOpen = false },
            onRefilled = { c.refilledEngineOil(); notice = Notice("엔진오일 경고 배지를 숨겼어요") },
            onOilChanged = { c.oilChanged(); notice = Notice("오일 교체를 기록했어요") },
            onSevere = c::setSevere,
            onRecommendedOil = c::setRecommendedOil,
            onToggleCheck = c::toggleCheck,
            onCompleteCheckup = {
                val round = status.checkup.round
                c.completeCheckup()
                notice = Notice("${Checkup.roundLabel(round)}을 마쳤어요")
            },
            onFindShop = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("자동차 정비소")))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    Log.w("Kagom", "지도 앱 없음", it)
                    notice = Notice("지도 앱을 열 수 없어요")
                }
            },
        )

        // 팝업 큐 (기어 P에서만 activePopup이 나온다). 들어올 때는 모달이 스스로 튀어 오르고(ModalScaffold),
        // 닫히거나 다음 팝업으로 넘어갈 때는 앞의 것이 옅어지며 사라진다. 언박싱 → 공개는 key가 같아 그 자리에서 바뀐다.
        // 정비 조작이 끝나기 전에는 큐를 소비하지 않고 시트 뒤에서 기다린다
        AnimatedContent(
            targetState = popup.takeIf { profile != null && !sheetOpen },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { (EnterTransition.None togetherWith fadeOut(tween(KagomMotion.SHEET_OUT))) using null },
            contentKey = { it?.key },
            label = "popup",
        ) { p ->
            if (profile != null) when (p) {
                is PendingPopup.Reward -> RewardModal(p, profile, data.equipped, { c.openReward(p.questId) }, { c.deferPopup(p) }) { equip -> c.finishPopup(p, equip) }
                is PendingPopup.Landmark -> LandmarkUnlockModal(p, profile, data.equipped) { equip -> c.finishPopup(p, equip) }
                is PendingPopup.TierUp -> TierUpModal(p, profile, data.equipped) { c.finishPopup(p, false) }
                null -> Unit
            }
        }

        // POP-01 주행 중 토스트
        var lastToast by remember { mutableStateOf<DrivingToast?>(null) }
        if (toast != null) lastToast = toast
        val toastTop = Modifier.align(Alignment.TopCenter).padding(top = if (mode == LayoutMode.SPLIT) 20.dp else 24.dp)
        AnimatedVisibility(
            toast != null, toastTop,
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
        ) {
            lastToast?.let { DrivingToastView(it, profile?.name ?: "카곰") }
        }
        // 정비 시트 버튼의 결과 알림 (같은 자리. 정차 중에만 생긴다)
        var lastNotice by remember { mutableStateOf("") }
        notice?.let { lastNotice = it.text }
        AnimatedVisibility(
            notice != null && toast == null, toastTop,
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
        ) {
            IviToast(lastNotice, Modifier.padding(horizontal = 20.dp))
        }

        if (debugOpen) DebugPanel(c, gear) { debugOpen = false }

        // 뒤로 키: 시연 패널 → 정비 시트 순으로 닫는다. 보상 팝업은 버튼으로만 닫는다 (뒤로 키에 앱이 닫히지 않게 받기만 한다).
        // 팝업은 시트가 닫힐 때까지 뒤에서 기다리므로 시트가 먼저다
        BackHandler(enabled = sheetOpen || debugOpen || popup != null) {
            when {
                debugOpen -> debugOpen = false
                sheetOpen -> sheetOpen = false
                else -> Unit
            }
        }
        // 팝업 미리보기는 맨 위 레이어이고 뒤로 키도 자기가 받는다 (나중에 등록한 쪽이 먼저 받는다)
        if (c.debug && popupPreview != null) key(popupPreview) {
            PopupPreview(popupPreview!!, gearP) { c.demoPopup.value = null }
        }
    }
}
