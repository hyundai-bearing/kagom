package com.example.roro.data

import android.content.Context
import com.example.roro.core.Category
import com.example.roro.core.Items
import com.example.roro.core.MaintenanceState
import com.example.roro.core.PendingPopup
import com.example.roro.core.Visit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** 카곰 프로필 (SCR-01/02에서 만든다) */
data class Profile(
    val name: String,
    val skin: String,
    val answers: String,
    val createdDay: Long,
    /** 이 차에서 처음 받은 주행거리. 이 차에서 달린 거리 = 지금 - 이 값 */
    val baseOdoKm: Float? = null,
    /** 이전 차에서 함께 달린 거리 (SCR-00 Carry-over에서 데려올 때 더한다) */
    val carriedKm: Float = 0f,
)

/** 착용 중인 치장 (머리·상의·하의·칭호·홈 배경) */
data class Equipped(
    val head: String? = null,
    val top: String? = null,
    val bottom: String? = null,
    val title: String? = Items.DEFAULT_TITLE,
    val background: String? = Items.DEFAULT_BACKGROUND,
) {
    operator fun get(c: Category): String? = when (c) {
        Category.HEAD -> head; Category.TOP -> top; Category.BOTTOM -> bottom; Category.TITLE -> title
        Category.BACKGROUND -> background
    }

    fun with(c: Category, id: String?): Equipped = when (c) {
        Category.HEAD -> copy(head = id); Category.TOP -> copy(top = id)
        Category.BOTTOM -> copy(bottom = id); Category.TITLE -> copy(title = id)
        Category.BACKGROUND -> copy(background = id)
    }
}

/** 앱이 저장하는 전부. 위치는 저장하지 않는다 (방문한 랜드마크 id·날짜만) */
data class AppData(
    val profile: Profile? = null,
    val equipped: Equipped = Equipped(),
    /** 저장 순서대로 최대 세 착장. 기존 저장 데이터에는 없을 수 있다. */
    val savedOutfits: List<Equipped> = emptyList(),
    val owned: Set<String> = Items.defaults,
    val exp: Int = 0,
    val points: Int = 0,
    val visits: List<Visit> = emptyList(),
    val completedQuests: Set<String> = emptySet(),
    val maintenance: MaintenanceState = MaintenanceState(),
    /** 이번 회차 정기 점검 체크리스트에서 체크한 항목 */
    val checkupChecks: Set<String> = emptySet(),
    /** gear → P 때 재생할 팝업 (pendingPopupQueue) */
    val pending: List<PendingPopup> = emptyList(),
    /** 도전과제 id → 달성 시각 (ms). SCR-04 카드의 "2026.11.08 달성 · 14:20". 예전 설치의 기록에는 없다 */
    val questDoneAt: Map<String, Long> = emptyMap(),
    /** 마지막으로 계산한 함께 달린 거리 (km). 차가 바뀌면 주행거리 신호로는 다시 계산할 수 없어서 적어 둔다 */
    val lastDrivenKm: Int = 0,
    /** 새 차에서 이전 카곰 기록을 찾았다 → SCR-00 Carry-over에서 고를 때까지 true */
    val newVehicle: Boolean = false,
) {
    val visitedPlaces: Set<String> get() = visits.map { it.landmarkId }.toSet()
}

/** SharedPreferences에 JSON 한 덩어리로 저장한다 */
class KagomStore(context: Context) {

    private val prefs = context.getSharedPreferences("kagom", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val data: StateFlow<AppData> = state.asStateFlow()

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(state.value)
        if (next == state.value) return
        state.value = next
        prefs.edit().putString(KEY, encode(next).toString()).apply()
    }

    fun reset() = update { AppData() }

    /**
     * 지금 기록을 보관함에 넣고 새로 시작한다 (SCR-00 "새 카곰과 처음부터 시작하기": 기록은 삭제되지 않고 보관돼요).
     * 보관한 기록을 다시 여는 화면은 아직 디자인에 없다.
     */
    @Synchronized
    fun archiveAndReset() {
        val current = state.value
        if (current.profile != null) {
            val archived = runCatching { JSONArray(prefs.getString(KEY_ARCHIVE, "[]")) }.getOrDefault(JSONArray())
            archived.put(encode(current.copy(newVehicle = false, pending = emptyList())))
            prefs.edit().putString(KEY_ARCHIVE, archived.toString()).apply()
        }
        update { AppData() }
    }

    private fun load(): AppData = runCatching {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) }
    }.getOrNull() ?: AppData()

    private companion object {
        const val KEY = "data_v2"
        const val KEY_ARCHIVE = "archive_v2"

        fun encode(d: AppData) = JSONObject().apply {
            d.profile?.let { p ->
                put("profile", JSONObject().apply {
                    put("name", p.name); put("skin", p.skin); put("answers", p.answers); put("createdDay", p.createdDay)
                    p.baseOdoKm?.let { put("baseOdoKm", it.toDouble()) }
                    put("carriedKm", p.carriedKm.toDouble())
                })
            }
            put("equipped", encodeEquipped(d.equipped))
            put("savedOutfits", JSONArray(d.savedOutfits.take(3).map { encodeEquipped(it) }))
            put("owned", JSONArray(d.owned.toList()))
            put("exp", d.exp); put("points", d.points)
            put("visits", JSONArray(d.visits.map { JSONObject().put("id", it.landmarkId).put("day", it.day) }))
            put("completed", JSONArray(d.completedQuests.toList()))
            put("maintenance", JSONObject().apply {
                val m = d.maintenance
                put("severe", m.severe); put("recommendedOil", m.recommendedOil)
                m.oilChangedKm?.let { put("oilKm", it.toDouble()) }; m.oilChangedDay?.let { put("oilDay", it) }
                put("rounds", m.checkupRoundsDone); m.checkupDoneDay?.let { put("checkupDay", it) }
                m.checkupDoneKm?.let { put("checkupKm", it.toDouble()) }
                put("dismissed", m.engineOilDismissed)
            })
            put("checks", JSONArray(d.checkupChecks.toList()))
            put("pending", JSONArray(d.pending.map { encodePopup(it) }))
            put("questDoneAt", JSONObject().apply { d.questDoneAt.forEach { (id, at) -> put(id, at) } })
            put("lastDrivenKm", d.lastDrivenKm)
            put("newVehicle", d.newVehicle)
        }

        fun decode(o: JSONObject) = AppData(
            profile = o.optJSONObject("profile")?.let {
                Profile(it.getString("name"), it.getString("skin"), it.optString("answers"), it.getLong("createdDay"),
                    if (it.has("baseOdoKm")) it.getDouble("baseOdoKm").toFloat() else null,
                    it.optDouble("carriedKm", 0.0).toFloat())
            },
            equipped = o.optJSONObject("equipped")?.let { decodeEquipped(it) } ?: Equipped(),
            savedOutfits = o.optJSONArray("savedOutfits").objects().take(3).map { decodeEquipped(it) },
            owned = o.optJSONArray("owned").strings().toSet() + Items.defaults,
            exp = o.optInt("exp"), points = o.optInt("points"),
            visits = o.optJSONArray("visits").objects().map { Visit(it.getString("id"), it.getLong("day")) },
            completedQuests = o.optJSONArray("completed").strings().toSet(),
            maintenance = o.optJSONObject("maintenance")?.let {
                MaintenanceState(
                    severe = it.optBoolean("severe"), recommendedOil = it.optBoolean("recommendedOil", true),
                    oilChangedKm = if (it.has("oilKm")) it.getDouble("oilKm").toFloat() else null,
                    oilChangedDay = if (it.has("oilDay")) it.getLong("oilDay") else null,
                    checkupRoundsDone = it.optInt("rounds"),
                    checkupDoneKm = if (it.has("checkupKm")) it.getDouble("checkupKm").toFloat() else null,
                    checkupDoneDay = if (it.has("checkupDay")) it.getLong("checkupDay") else null,
                    engineOilDismissed = it.optBoolean("dismissed"),
                )
            } ?: MaintenanceState(),
            checkupChecks = o.optJSONArray("checks").strings().toSet(),
            pending = o.optJSONArray("pending").objects().mapNotNull { decodePopup(it) },
            questDoneAt = o.optJSONObject("questDoneAt")?.let { m -> m.keys().asSequence().associateWith { m.optLong(it) } }.orEmpty(),
            lastDrivenKm = o.optInt("lastDrivenKm"),
            newVehicle = o.optBoolean("newVehicle"),
        )

        fun encodeEquipped(e: Equipped) = JSONObject().apply {
            e.head?.let { put("head", it) }; e.top?.let { put("top", it) }
            e.bottom?.let { put("bottom", it) }; e.title?.let { put("title", it) }
            e.background?.let { put("background", it) }
        }

        /** 배경이 생기기 전에 저장한 착장에는 background가 없다 → 기본 배경으로 읽는다 */
        fun decodeEquipped(o: JSONObject) = Equipped(
            o.optStringOrNull("head"), o.optStringOrNull("top"), o.optStringOrNull("bottom"), o.optStringOrNull("title"),
            o.optStringOrNull("background") ?: Items.DEFAULT_BACKGROUND,
        )

        fun encodePopup(p: PendingPopup) = when (p) {
            is PendingPopup.Reward -> JSONObject().put("t", "reward").put("quest", p.questId).put("item", p.itemId)
                .put("exp", p.exp).put("before", p.expBefore).put("after", p.expAfter).put("opened", p.opened)
            is PendingPopup.Landmark -> JSONObject().put("t", "landmark").put("lm", p.landmarkId).put("item", p.itemId)
                .put("exp", p.exp).put("before", p.expBefore).put("after", p.expAfter).put("places", p.visitedPlaces).put("day", p.day)
            is PendingPopup.TierUp -> JSONObject().put("t", "tier").put("tier", p.tier).put("points", p.points)
                .put("total", p.totalExp).put("items", p.ownedItems).put("places", p.visitedPlaces)
        }

        fun decodePopup(o: JSONObject): PendingPopup? = runCatching {
            when (o.getString("t")) {
                "reward" -> PendingPopup.Reward(o.getString("quest"), o.getString("item"), o.getInt("exp"),
                    o.getInt("before"), o.getInt("after"), o.optBoolean("opened"))
                "landmark" -> PendingPopup.Landmark(o.getString("lm"), o.getString("item"), o.getInt("exp"),
                    o.getInt("before"), o.getInt("after"), o.getInt("places"), o.getLong("day"))
                "tier" -> PendingPopup.TierUp(o.getInt("tier"), o.getInt("points"), o.getInt("total"), o.getInt("items"), o.getInt("places"))
                else -> null
            }
        }.getOrNull()

        fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
        fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
        fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    }
}
