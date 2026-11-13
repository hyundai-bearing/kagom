package com.example.roro.data

import android.util.Log
import com.example.roro.core.Weather
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import kotlin.random.Random

/**
 * 지금 있는 곳의 날씨 (홈 배경 "오늘 날씨" BG-04). 노르웨이 기상청 MET Norway의 Locationforecast 2.0을 쓴다:
 * 키 없이 쓸 수 있고 자료가 CC BY 4.0 / NLOD 2.0이라 출처만 밝히면 된다 (THIRD_PARTY_NOTICES.md).
 * 위치가 없거나 요청이 실패하면 null로 남고 배경은 기본 그림을 쓴다. 위치는 약 11 km 칸으로 줄여서만 보내고 저장하지 않는다.
 *
 * 이용 조건(api.met.no/doc/TermsOfService)에서 지키는 것: 앱을 알아볼 수 있는 User-Agent, 응답의 Expires 전에는 다시 묻지 않기,
 * If-Modified-Since, 앱을 쓰지 않는 동안에는 묻지 않기([active]).
 */
class WeatherSource {

    private val state = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = state.asStateFlow()

    /** 앱이 화면에 보이는 동안만 true (MainActivity onStart/onStop). 안 보일 때는 다음 요청을 미룬다 */
    val active = MutableStateFlow(true)

    private var job: Job? = null

    fun start(scope: CoroutineScope, location: Flow<GeoPoint?>) {
        if (job != null) return
        job = scope.launch {
            location.map { p -> p?.let { Weather.coarse(it.lat) to Weather.coarse(it.lon) } }
                .distinctUntilChanged()
                .collectLatest { cell ->
                    // 칸이 바뀌면 앞 칸의 날씨는 맞지 않으므로 새 값이 올 때까지 기본 그림으로 돌린다
                    state.value = null
                    if (cell == null) return@collectLatest
                    var lastModified: String? = null
                    var forecast: String? = null
                    while (true) {
                        active.first { it }
                        val reply = withContext(Dispatchers.IO) { fetch(cell.first, cell.second, lastModified) }
                        if (reply?.body != null) {
                            forecast = reply.body
                            lastModified = reply.lastModified
                        }
                        // 예보가 그대로여도(304) 시각이 넘어가면 지금 칸이 달라지므로 받아 둔 예보에서 다시 고른다
                        if (reply != null) forecast?.let { body ->
                            runCatching { Weather.fromSymbol(currentSymbol(body)) }.getOrNull()?.let { state.value = it }
                        }
                        val wait = if (reply == null) RETRY_MS else maxOf(REFRESH_MS, reply.expiresAt - System.currentTimeMillis())
                        // 여러 차가 같은 시각에 몰리지 않게 조금씩 흩는다
                        delay(wait + Random.nextLong(SPREAD_MS))
                    }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** [body]가 null이면 바뀐 것이 없다(304)는 뜻이다 */
    private class Reply(val body: String?, val lastModified: String?, val expiresAt: Long)

    private fun fetch(lat: Double, lon: Double, lastModified: String?): Reply? = runCatching {
        val url = URL(String.format(Locale.US, "%s?lat=%.1f&lon=%.1f", ENDPOINT, lat, lon))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (lastModified != null) connection.setRequestProperty("If-Modified-Since", lastModified)
            val code = connection.responseCode
            val expiresAt = connection.getHeaderFieldDate("Expires", 0L)
            when (code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Reply(null, lastModified, expiresAt)
                // 203 = 곧 없어질 판(MET Norway). 값은 그대로 쓸 수 있다
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_NOT_AUTHORITATIVE -> {
                    if (code != HttpURLConnection.HTTP_OK) Log.w(TAG, "날씨 API 판이 곧 바뀐다 (203)")
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    Reply(body, connection.getHeaderField("Last-Modified"), expiresAt)
                }
                else -> {
                    Log.w(TAG, "날씨 응답 $code")
                    null
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrElse {
        Log.w(TAG, "날씨 요청 실패: ${it.javaClass.simpleName}")
        null
    }

    /** 시간별 예보에서 지금 시각이 든 칸의 기호. 한 시간 예보가 없는 먼 시각이면 여섯 시간 예보를 쓴다 */
    private fun currentSymbol(body: String): String? {
        val series = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        val now = Instant.now()
        var current: JSONObject? = null
        for (i in 0 until series.length()) {
            val entry = series.getJSONObject(i)
            if (current != null && Instant.parse(entry.getString("time")).isAfter(now)) break
            current = entry
        }
        val data = current?.optJSONObject("data") ?: return null
        return listOf("next_1_hours", "next_6_hours").firstNotNullOfOrNull { key ->
            data.optJSONObject(key)?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() }
        }
    }

    companion object {
        private const val TAG = "KagomWeather"
        private const val ENDPOINT = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
        /** 이용 조건은 연락처(사이트·메일)도 넣기를 권한다. 외부에 배포하기 전에 팀 연락처를 더한다 (THIRD_PARTY_NOTICES.md) */
        private const val USER_AGENT = "Kagom/2.0"
        private const val TIMEOUT_MS = 10_000
        /** 예보는 한 시간 단위라 30분마다면 충분하다. 응답의 Expires가 더 뒤면 그때까지 기다린다 */
        private const val REFRESH_MS = 30 * 60_000L
        /** 이용 조건: 되풀이 요청은 10분에 한 번을 넘기지 않는다 */
        private const val RETRY_MS = 10 * 60_000L
        private const val SPREAD_MS = 5 * 60_000L
    }
}
