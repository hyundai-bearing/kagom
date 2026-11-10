package com.example.roro.ui

import android.content.Context
import com.example.roro.core.Answer
import com.example.roro.core.Checkup
import com.example.roro.core.Gear
import com.example.roro.core.Items
import com.example.roro.core.Landmarks
import com.example.roro.core.MaintenanceState
import com.example.roro.core.OilLevel
import com.example.roro.core.PendingPopup
import com.example.roro.core.Progress
import com.example.roro.core.ProgressContext
import com.example.roro.core.Quest
import com.example.roro.core.Skins
import com.example.roro.core.StateEngine
import com.example.roro.core.Tiers
import com.example.roro.core.VehicleSnapshot
import com.example.roro.core.VehicleStatus
import com.example.roro.data.AppData
import com.example.roro.data.DrivingStateSource
import com.example.roro.data.Equipped
import com.example.roro.data.GeoPoint
import com.example.roro.data.KagomStore
import com.example.roro.data.LocationSource
import com.example.roro.data.MockVehicleSignalSource
import com.example.roro.data.OverridableSignalSource
import com.example.roro.data.PleosVehicleSignalSource
import com.example.roro.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** POP-01 주행 중 토스트 내용. 문구는 화면 모드(Full/Split)에 맞춰 UI가 만든다 */
data class DrivingToast(val headline: String, val tail: TailKind, val id: Long = System.nanoTime()) {
    enum class TailKind { GIFT, FOG }
}

/**
 * 앱 상태를 한곳에 모은다: 차 신호 → 상태 엔진(4장), 도전과제·랜드마크 보상(5장), 팝업 큐(2장).
 * 기어 P 잠금(가이드 5.2)은 여기서도 한 번 더 막는다 (UI가 놓쳐도 저장되지 않게).
 */
class KagomController(context: Context, private val scope: CoroutineScope, useMock: Boolean, val debug: Boolean) {

    val store = KagomStore(context)
    val signals = OverridableSignalSource(
        if (useMock) MockVehicleSignalSource() else PleosVehicleSignalSource(context), scope,
    )
    val drivingSource = DrivingStateSource(context)
    val locationSource = LocationSource(context)

    /** 기어. Pleos 기어 신호가 없으면 CarUxRestrictions(주행 중 여부)로 대신한다 */
    val gear: StateFlow<Gear> = combine(signals.snapshot, drivingSource.driving) { s, driving ->
        s.gear ?: if (driving) Gear.D else Gear.P
    }.stateIn(scope, SharingStarted.Eagerly, Gear.P)

    private var energyLevel = 0

    val status: StateFlow<VehicleStatus> = combine(signals.snapshot, store.data) { s, d -> evaluate(s, d) }
        .stateIn(scope, SharingStarted.Eagerly, evaluate(signals.snapshot.value, store.data.value))

    private fun evaluate(s: VehicleSnapshot, d: AppData): VehicleStatus {
        val today = today()
        val r = StateEngine.evaluate(s, d.maintenance, energyLevel, today, d.profile?.createdDay ?: today)
        energyLevel = r.energyLevel
        return r
    }

    // ───────────── 팝업 큐 ─────────────

    /** 이번 정차에서 "나중에"로 넘긴 팝업. 기어가 P를 벗어나면 비운다 (다음 정차 때 다시 재생) */
    private val deferred = MutableStateFlow<Set<String>>(emptySet())

    /** 지금 띄울 팝업: 기어 P + 카곰 있음 + 큐 순서대로 */
    val activePopup: StateFlow<PendingPopup?> = combine(store.data, gear, deferred) { d, g, skip ->
        if (!g.isPark || d.profile == null || d.newVehicle) null
        else Progress.ordered(d.pending).firstOrNull { it.key !in skip }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val toastState = MutableStateFlow<DrivingToast?>(null)
    val toast: StateFlow<DrivingToast?> = toastState.asStateFlow()

    /** 시연 패널 '예시 데이터': Figma 예시(치장 예시 아이템 03~12 등)를 보여 준다. 저장하지 않는다 */
    val demoExamples = MutableStateFlow(false)
    /** POP-02~05 시연은 저장소를 건드리지 않는 별도 프리뷰로 연다. */
    val demoPopup = MutableStateFlow<String?>(null)
    private var toastJob: Job? = null

    private fun showToast(t: DrivingToast) {
        toastState.value = t
        toastJob?.cancel()
        toastJob = scope.launch { delay(3_000); toastState.value = null }   // 3초 후 자동 소멸 (POP-01)
    }

    // ───────────── 시작 ─────────────

    private var insideLandmark: String? = null

    fun start() {
        signals.start()
        drivingSource.start()
        locationSource.start()

        // 기어가 P를 벗어나면 "나중에" 목록을 비운다
        scope.launch {
            gear.map { it.isPark }.distinctUntilChanged().collect { parked -> if (!parked) deferred.value = emptySet() }
        }
        // 주행거리·센서 변화 → 기준값 채우기, 보충 표시 해제, 도전과제 판정
        scope.launch {
            combine(signals.snapshot, store.data.map { it.profile != null }.distinctUntilChanged()) { s, _ -> s }
                .collect { s -> onSignals(s) }
        }
        // 위치 → 랜드마크 방문
        scope.launch { locationSource.current.collect { p -> if (p != null) onLocation(p) } }
    }

    fun stop() {
        signals.stop()
        drivingSource.stop()
        locationSource.stop()
    }

    private fun onSignals(s: VehicleSnapshot) {
        val odo = s.odometerKm
        store.update { d ->
            var next = d
            val p = d.profile
            if (p != null && p.baseOdoKm == null && odo != null) next = next.copy(profile = p.copy(baseOdoKm = odo))
            if (p != null && d.maintenance.oilChangedKm == null && odo != null)
                next = next.copy(maintenance = next.maintenance.copy(oilChangedKm = odo, oilChangedDay = next.maintenance.oilChangedDay ?: today()))
            // [보충했어]로 숨긴 배지: 센서가 NORMAL로 돌아오면 다시 감시
            if (d.maintenance.engineOilDismissed && s.engineOil == OilLevel.NORMAL)
                next = next.copy(maintenance = next.maintenance.copy(engineOilDismissed = false))
            // 함께 달린 거리를 km 단위로 적어 둔다 (차가 바뀌어도 이어 가려고). 새 차를 고르기 전에는 건드리지 않는다
            val driven = progressContext(next).drivenKm
            if (next.profile != null && !next.newVehicle && driven != next.lastDrivenKm) next = next.copy(lastDrivenKm = driven)
            next
        }
        checkQuests()
    }

    fun progressContext(d: AppData): ProgressContext {
        val driven = Progress.drivenKm(signals.snapshot.value.odometerKm, d.profile?.baseOdoKm, d.profile?.carriedKm ?: 0f)
        return ProgressContext(driven, d.visits, d.maintenance.checkupRoundsDone)
    }

    /** 새로 달성한 도전과제가 있으면 보상 지급 + 큐에 넣기 */
    private fun checkQuests() {
        val d = store.data.value
        if (d.profile == null || d.newVehicle) return
        Progress.newlyAchieved(progressContext(d), d.completedQuests).forEach { grantQuest(it) }
    }

    private fun grantQuest(q: Quest) {
        var granted = false
        store.update { d ->
            if (q.id in d.completedQuests) return@update d
            granted = true
            val after = d.exp + q.exp
            withExp(
                d.copy(
                    owned = d.owned + q.rewardItemId, completedQuests = d.completedQuests + q.id,
                    questDoneAt = d.questDoneAt + (q.id to System.currentTimeMillis()),
                ),
                d.exp, after,
                PendingPopup.Reward(q.id, q.rewardItemId, q.exp, d.exp, after),
            )
        }
        if (granted && !gear.value.isPark) showToast(DrivingToast(q.toast, DrivingToast.TailKind.GIFT))
    }

    /** EXP 반영 + 큐 추가 + 티어 승급 판정 */
    private fun withExp(d: AppData, before: Int, after: Int, popup: PendingPopup): AppData {
        var pending = Progress.enqueue(d.pending, popup)
        var points = d.points
        val from = Tiers.tierFor(before); val to = Tiers.tierFor(after)
        for (t in (from + 1)..to) {
            points += Tiers.POINTS_PER_TIER
            pending = Progress.enqueue(pending, PendingPopup.TierUp(t, Tiers.POINTS_PER_TIER, after,
                (d.owned - Items.DEFAULT_TITLE).size, d.visitedPlaces.size))
        }
        return d.copy(exp = after, points = points, pending = pending)
    }

    private fun onLocation(p: GeoPoint) {
        val lm = Progress.landmarkAt(p.lat, p.lon)
        if (lm?.id == insideLandmark) return
        insideLandmark = lm?.id
        if (lm == null || store.data.value.profile == null) return
        val day = today()
        var firstVisit = false
        store.update { d ->
            val visits = Progress.recordVisit(d.visits, lm.id, day)
            if (lm.id in d.visitedPlaces) return@update d.copy(visits = visits)
            firstVisit = true
            val after = d.exp + lm.exp
            val withVisit = d.copy(visits = visits, owned = d.owned + lm.rewardItemId)
            withExp(withVisit, d.exp, after,
                PendingPopup.Landmark(lm.id, lm.rewardItemId, lm.exp, d.exp, after, withVisit.visitedPlaces.size, day))
        }
        if (firstVisit && !gear.value.isPark) showToast(DrivingToast("${lm.name} 도착!", DrivingToast.TailKind.FOG))
        checkQuests()
    }

    // ───────────── 사용자 동작 (모두 기어 P에서만) ─────────────

    private inline fun parked(block: () -> Unit) { if (gear.value.isPark) block() }

    /** SCR-02 완료: 카곰 생성 */
    fun createProfile(name: String, answers: List<Answer>) = parked {
        val skin = Skins.forAnswers(answers) ?: return@parked
        if (!Progress.isValidName(name)) return@parked
        val odo = signals.snapshot.value.odometerKm
        store.update {
            AppData(
                profile = Profile(name, skin.code, answers.joinToString("") { a -> a.name }, today(), odo),
                maintenance = it.maintenance.copy(oilChangedKm = it.maintenance.oilChangedKm ?: odo,
                    oilChangedDay = it.maintenance.oilChangedDay ?: today()),
            )
        }
    }

    /**
     * SCR-00 Carry-over "계속 함께하기": 칭호·아이템·EXP·방문 기록은 그대로 두고, 차에 묶인 값만 새 차에 맞춘다.
     * 주행거리 기준값은 비워 두면 새 차의 첫 신호로 다시 잡히고(onSignals), 이전 차에서 달린 거리는 carriedKm으로 넘긴다.
     * 정비 기록(오일 교체·정기 점검)은 차마다 다르므로 새로 시작한다.
     */
    fun carryOverKeep() = parked {
        store.update { d ->
            val p = d.profile ?: return@update d.copy(newVehicle = false)
            d.copy(
                profile = p.copy(baseOdoKm = null, carriedKm = d.lastDrivenKm.toFloat()),
                maintenance = MaintenanceState(), checkupChecks = emptySet(), newVehicle = false,
            )
        }
        onSignals(signals.snapshot.value)
    }

    /** SCR-00 Carry-over "처음부터 시작하기": 기록을 보관함에 넣고 성향 테스트부터 다시 한다 */
    fun carryOverRestart() = parked {
        deferred.value = emptySet()
        energyLevel = 0
        store.archiveAndReset()
    }

    /** POP-02 언박싱 → 공개 */
    fun openReward(questId: String) = store.update { d ->
        if (!gear.value.isPark) d else d.copy(pending = d.pending.map { if (it is PendingPopup.Reward && it.questId == questId) it.copy(opened = true) else it })
    }

    /** 언박싱 단계 "나중에": 큐에 남기고 이번 정차에서는 넘긴다 */
    fun deferPopup(p: PendingPopup) { deferred.value = deferred.value + p.key }

    /** 공개·해금·승급 확인: 큐에서 지운다. [equip]이면 바로 장착 */
    fun finishPopup(p: PendingPopup, equip: Boolean) = store.update { d ->
        if (!gear.value.isPark) return@update d
        val itemId = when (p) { is PendingPopup.Reward -> p.itemId; is PendingPopup.Landmark -> p.itemId; else -> null }
        val item = Items.byId(itemId)
        val equipped = if (equip && item != null && gear.value.isPark) d.equipped.with(item.category, item.id) else d.equipped
        d.copy(pending = d.pending.filterNot { it.key == p.key }, equipped = equipped)
    }

    /** SCR-06 저장하기 */
    fun saveEquipped(e: Equipped) = parked { store.update { d -> d.copy(equipped = e.sanitized(d.owned)) } }

    /** 지도 팝업에서 바로 장착 */
    fun equip(itemId: String) = parked {
        val item = Items.byId(itemId) ?: return@parked
        store.update { d -> if (itemId in d.owned) d.copy(equipped = d.equipped.with(item.category, itemId)) else d }
    }

    // POP-05 정비 시트
    fun refilledEngineOil() = parked { store.update { it.copy(maintenance = it.maintenance.copy(engineOilDismissed = true)) } }

    fun oilChanged() = parked {
        val odo = signals.snapshot.value.odometerKm
        store.update { it.copy(maintenance = it.maintenance.copy(oilChangedKm = odo ?: it.maintenance.oilChangedKm, oilChangedDay = today())) }
    }

    fun setSevere(on: Boolean) = parked { store.update { it.copy(maintenance = it.maintenance.copy(severe = on)) } }
    fun setRecommendedOil(on: Boolean) = parked { store.update { it.copy(maintenance = it.maintenance.copy(recommendedOil = on)) } }

    fun toggleCheck(item: String) = parked {
        store.update { d ->
            val snapshot = signals.snapshot.value
            val checkup = StateEngine.checkup(d.maintenance, snapshot.odometerKm)
            if (!checkup.due || Checkup.itemsFor(snapshot.engineType).none { it.key == item }) d
            else d.copy(checkupChecks = if (item in d.checkupChecks) d.checkupChecks - item else d.checkupChecks + item)
        }
    }

    fun completeCheckup() = parked {
        store.update { d ->
            // 연타·이전 화면 콜백도 최신 거리·차종·체크 상태를 확인한다.
            val snapshot = signals.snapshot.value
            val checkup = StateEngine.checkup(d.maintenance, snapshot.odometerKm)
            if (!Checkup.canComplete(checkup, snapshot.engineType, d.checkupChecks)) d
            else d.copy(maintenance = d.maintenance.copy(checkupRoundsDone = checkup.round, checkupDoneDay = today()), checkupChecks = emptySet())
        }
        checkQuests()
    }

    // ───────────── 시연 (디버그 빌드) ─────────────

    fun demoSetGear(g: Gear?) = signals.edit { it.copy(gear = g) }
    fun demoVisit(landmarkId: String?) {
        val lm = Landmarks.byId(landmarkId)
        locationSource.override.value = lm?.let { GeoPoint(it.lat, it.lon) }
    }
    fun demoAddKm(km: Float) {
        val cur = signals.snapshot.value.odometerKm ?: 0f
        signals.edit { it.copy(odometerKm = cur + km) }
    }
    /** 새 차로 옮겨 탄 상황 (SCR-00 Carry-over). 실제 감지 방법은 아직 정해지지 않아 시연 훅으로만 켠다 */
    fun demoNewVehicle(on: Boolean) = store.update { d -> if (d.profile == null) d else d.copy(newVehicle = on) }

    fun demoResetAll() {
        signals.clear()
        locationSource.override.value = null
        insideLandmark = null
        deferred.value = emptySet()
        energyLevel = 0
        store.reset()
    }

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}

/** 갖고 있지 않은 아이템은 착용할 수 없다 */
private fun Equipped.sanitized(owned: Set<String>) = Equipped(
    head = head?.takeIf { it in owned }, top = top?.takeIf { it in owned },
    bottom = bottom?.takeIf { it in owned }, title = title?.takeIf { it in owned },
)
