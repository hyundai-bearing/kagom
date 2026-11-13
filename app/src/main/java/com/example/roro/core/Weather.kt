package com.example.roro.core

import kotlin.math.roundToInt

/**
 * 홈 배경 "오늘 날씨"(BG-04)가 그리는 날씨. 디자인에 없어 대표 다섯 가지로 정했다 (가정).
 * 안개·구름 많음처럼 그림이 따로 없는 날씨는 가장 가까운 것으로 묶는다.
 */
enum class Weather(val label: String) {
    CLEAR("맑음"), CLOUDY("흐림"), RAIN("비"), SNOW("눈"), THUNDER("천둥번개");

    companion object {
        /**
         * MET Norway Locationforecast의 `symbol_code` (예: `partlycloudy_day`, `lightrainshowersandthunder_night`).
         * 낮·밤 꼬리표는 버린다 (밤 그림은 홈 글자색을 같이 바꿔야 해서 두지 않았다).
         * 모르는 값이면 null → 배경은 기본 그림으로 남는다.
         */
        fun fromSymbol(code: String?): Weather? {
            val name = code?.substringBefore('_')?.lowercase().orEmpty()
            return when {
                name.isEmpty() -> null
                "thunder" in name -> THUNDER
                // 진눈깨비(sleet)는 눈 그림으로 보인다
                "snow" in name || "sleet" in name -> SNOW
                "rain" in name -> RAIN
                name == "cloudy" || name == "fog" -> CLOUDY
                name == "clearsky" || name == "fair" || name == "partlycloudy" -> CLEAR
                else -> null
            }
        }

        /**
         * 날씨를 물을 때 쓰는 좌표. 소수 첫째 자리(약 11 km)로 줄인다:
         * 날씨는 그보다 촘촘할 필요가 없고, 차의 정확한 위치를 밖으로 보내지 않으려는 것이다.
         */
        fun coarse(degrees: Double): Double = (degrees * 10).roundToInt() / 10.0
    }
}
