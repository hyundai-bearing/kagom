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
    /** 카곰을 만든 뒤 처음 받은 주행거리. 도전과제 거리 = 지금 - 이 값 */
    val baseOdoKm: Float? = null,
)

/** 착용 중인 치장 (머리·상의·하의·칭호) */
data class Equipped(
    val head: String? = null,
    val top: String? = null,
    val bottom: String? = null,
    val title: String? = Items.DEFAULT_TITLE,
) {
    operator fun get(c: Category): String? = when (c) {
        Category.HEAD -> head; Category.TOP -> top; Category.BOTTOM -> bottom; Category.TITLE -> title
    }

    fun with(c: Category, id: String?): Equipped = when (c) {
        Category.HEAD -> copy(head = id); Category.TOP -> copy(top = id)
        Category.BOTTOM -> copy(bottom = id); Category.TITLE -> copy(title = id)
    }
}

/** 앱이 저장하는 전부. 위치는 저장하지 않는다 (방문한 랜드마크 id·날짜만) */
data class AppData(
    val profile: Profile? = null,
    val equipped: Equipped = Equipped(),
    val owned: Set<String> = setOf(Items.DEFAULT_TITLE),
    val exp: Int = 0,
    val points: Int = 0,
    val visits: List<Visit> = emptyList(),
    val completedQuests: Set<String> = emptySet(),
    val maintenance: MaintenanceState = MaintenanceState(),
    /** 이번 회차 정기 점검 체크리스트에서 체크한 항목 */
    val checkupChecks: Set<String> = emptySet(),
    /** gear → P 때 재생할 팝업 (pendingPopupQueue) */
    val pending: List<PendingPopup> = emptyList(),
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

    private fun load(): AppData = runCatching {
        prefs.getString(KEY, null)?.let { decode(JSONObject(it)) }
    }.getOrNull() ?: AppData()

    private companion object {
        const val KEY = "data_v2"

        fun encode(d: AppData) = JSONObject().apply {
            d.profile?.let { p ->
                put("profile", JSONObject().apply {
                    put("name", p.name); put("skin", p.skin); put("answers", p.answers); put("createdDay", p.createdDay)
                    p.baseOdoKm?.let { put("baseOdoKm", it.toDouble()) }
                })
            }
            put("equipped", JSONObject().apply {
                d.equipped.head?.let { put("head", it) }; d.equipped.top?.let { put("top", it) }
                d.equipped.bottom?.let { put("bottom", it) }; d.equipped.title?.let { put("title", it) }
            })
            put("owned", JSONArray(d.owned.toList()))
            put("exp", d.exp); put("points", d.points)
            put("visits", JSONArray(d.visits.map { JSONObject().put("id", it.landmarkId).put("day", it.day) }))
            put("completed", JSONArray(d.completedQuests.toList()))
            put("maintenance", JSONObject().apply {
                val m = d.maintenance
                put("severe", m.severe); put("recommendedOil", m.recommendedOil)
                m.oilChangedKm?.let { put("oilKm", it.toDouble()) }; m.oilChangedDay?.let { put("oilDay", it) }
                put("rounds", m.checkupRoundsDone); m.checkupDoneDay?.let { put("checkupDay", it) }
                put("dismissed", m.engineOilDismissed)
            })
            put("checks", JSONArray(d.checkupChecks.toList()))
            put("pending", JSONArray(d.pending.map { encodePopup(it) }))
        }

        fun decode(o: JSONObject) = AppData(
            profile = o.optJSONObject("profile")?.let {
                Profile(it.getString("name"), it.getString("skin"), it.optString("answers"), it.getLong("createdDay"),
                    if (it.has("baseOdoKm")) it.getDouble("baseOdoKm").toFloat() else null)
            },
            equipped = o.optJSONObject("equipped")?.let {
                Equipped(it.optStringOrNull("head"), it.optStringOrNull("top"), it.optStringOrNull("bottom"), it.optStringOrNull("title"))
            } ?: Equipped(),
            owned = o.optJSONArray("owned").strings().toSet().ifEmpty { setOf(Items.DEFAULT_TITLE) },
            exp = o.optInt("exp"), points = o.optInt("points"),
            visits = o.optJSONArray("visits").objects().map { Visit(it.getString("id"), it.getLong("day")) },
            completedQuests = o.optJSONArray("completed").strings().toSet(),
            maintenance = o.optJSONObject("maintenance")?.let {
                MaintenanceState(
                    severe = it.optBoolean("severe"), recommendedOil = it.optBoolean("recommendedOil", true),
                    oilChangedKm = if (it.has("oilKm")) it.getDouble("oilKm").toFloat() else null,
                    oilChangedDay = if (it.has("oilDay")) it.getLong("oilDay") else null,
                    checkupRoundsDone = it.optInt("rounds"),
                    checkupDoneDay = if (it.has("checkupDay")) it.getLong("checkupDay") else null,
                    engineOilDismissed = it.optBoolean("dismissed"),
                )
            } ?: MaintenanceState(),
            checkupChecks = o.optJSONArray("checks").strings().toSet(),
            pending = o.optJSONArray("pending").objects().mapNotNull { decodePopup(it) },
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
