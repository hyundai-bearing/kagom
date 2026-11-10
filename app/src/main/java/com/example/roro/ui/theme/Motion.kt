package com.example.roro.ui.theme

/**
 * 움직임 시간 (ms). Figma에는 모션 값이 없어서 정한 가정값이다 (docs/IMPLEMENTATION.md "디자인에 없던 값").
 * 주행 중(gear≠P)에는 운전자의 눈을 끌지 않게 화면 전환을 움직이지 않고 바로 바꾼다 → [ms].
 */
object KagomMotion {
    /** 선택 색 바뀜 (탭, 셀 테두리) */
    const val FAST = 140
    /** 화면·단계 전환 (탭 이동, 성향 테스트 문항, 치장 페이지) */
    const val SCREEN = 220
    /** 시트·패널이 들어오는 시간. 나갈 때는 [SHEET_OUT] */
    const val SHEET = 280
    const val SHEET_OUT = 200
    /** 지도 단계 이동 (전국 ↔ 시·도 ↔ 시·군) */
    const val MAP = 450
    /** 진행 바가 새 값까지 차오르는 시간 */
    const val PROGRESS = 600

    /** 주행 중이면 0 (바로 바뀐다) */
    fun ms(base: Int, gearP: Boolean): Int = if (gearP) base else 0
}
