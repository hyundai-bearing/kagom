@file:OptIn(ExperimentalLayoutApi::class)

package com.example.roro.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.roro.core.Expression
import com.example.roro.core.Items
import com.example.roro.core.Severity
import com.example.roro.core.Tiers
import com.example.roro.core.VehicleStatus
import com.example.roro.data.AppData
import com.example.roro.data.Profile
import com.example.roro.ui.components.DriveLockBanner
import com.example.roro.ui.components.HeaderProfile
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.QuickMaintenanceCard
import com.example.roro.ui.components.StatusBadge
import com.example.roro.ui.components.VehicleStatusRow
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomSkinPalette
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode

/**
 * SCR-03 홈. 상태 엔진 출력(badges, expression, rows)을 받는 순수 화면 (가이드 7.2).
 * gear≠P: 상태 행은 50% 불투명도로 유지, 조작만 막는다 (가이드 2.1).
 */
@Composable
fun HomeScreen(
    profile: Profile,
    data: AppData,
    status: VehicleStatus,
    gearP: Boolean,
    /** 시연 패널에서 고른 캐릭터 동작 (TouchAct.key). 틀고 나면 [onDemoActShown] */
    demoAct: String? = null,
    onDemoActShown: () -> Unit = {},
    onOpenSheet: () -> Unit,
) {
    val palette = KagomSkinPalette.of(profile.skin)
    val title = Items.byId(data.equipped.title)?.name
    val header: @Composable (Modifier) -> Unit = { m ->
        HeaderProfile(profile.name, title, Tiers.tierFor(data.exp), data.exp, Tiers.nextThreshold(data.exp), m)
    }
    val character: @Composable (Modifier) -> Unit = { m ->
        KagomCharacter(
            profile.skin, status.expression, m,
            worn = wornLayers(data.equipped.head, data.equipped.top, data.equipped.bottom),
            onTouch = if (gearP) ({}) else null,
            turnable = gearP && status.expression == Expression.IDLE,
            alive = gearP,
            demoAct = demoAct,
            onDemoActShown = onDemoActShown,
        )
    }
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    // 이 화면이 뜬 뒤에 새로 생긴 배지만 톡 튀어나온다 (처음부터 있던 배지와 주행 중에는 움직이지 않는다)
    val known = remember { status.badges.map { it.id }.toMutableSet() }
    known.retainAll(status.badges.map { it.id }.toSet())
    val badges: @Composable (Modifier) -> Unit = { m ->
        FlowRow(m, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            // Split에는 빠른 정비 카드가 없어 배지가 정비 시트로 가는 하나뿐인 길이다 → 이상이 없을 때도 열 수 있게 한다
            if (status.badges.isEmpty()) StatusBadge(Severity.OK, "이상 없음", onClick = if (gearP && split) onOpenSheet else null)
            else status.badges.forEach { b ->
                key(b.id) {
                    val pop = remember { Animatable(if (known.add(b.id) && gearP) 0f else 1f) }
                    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                    StatusBadge(b.severity, b.label, Modifier.graphicsLayer {
                        scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f)
                    }, onClick = if (gearP) onOpenSheet else null)
                }
            }
        }
    }

    if (split) {
        // 헤더 / 캔버스 / 하단 배지 그룹 세로 스택. 배지 터치 → 바텀시트
        Column(Modifier.fillMaxSize().background(palette.canvasTint)) {
            header(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp))
            if (!gearP) DriveLockBanner("정비 시트는 정차 후", "주행 중에는 배지 터치가 무시돼요.",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 74.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                character(Modifier.size(470.dp, 690.dp))
            }
            badges(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp))
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        // kagomMainCanvas (65%): 헤더·캐릭터·배지 세로 가운데
        Column(
            Modifier.weight(1f).fillMaxHeight().background(palette.canvasTint),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header(Modifier)
            // 가이드 v1.2: 캐릭터 아래 상태 글자가 없어졌다. 캐릭터 크기는 그대로 두고(660) 글자 자리만큼 위아래로 나눠 내린다
            Spacer(Modifier.height(40.dp))
            character(Modifier.size(450.dp, 660.dp))
            Spacer(Modifier.height(40.dp))
            badges(Modifier.width(1000.dp))
        }
        // vehicleStatusPanel (35%, 640 고정)
        Column(
            Modifier.width(640.dp).fillMaxHeight().background(KagomColor.Surface.card)
                .verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 32.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s12),
        ) {
            Text("차량 상태", style = KagomType.Heading24, color = KagomColor.Text.primary)
            if (!gearP) DriveLockBanner("정비 시트는 정차 후", null)
            Column(Modifier.alpha(if (gearP) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
                status.rows.forEach { VehicleStatusRow(it) }
            }
            QuickMaintenanceCard(
                oilRemaining = status.oilChange?.display,
                oilRatio = status.oilChange?.remainingRatio ?: 0f,
                inspection = status.checkup.display,
                hint = if (gearP) "기어 P에서만 조작" else "주행 중에는 조작할 수 없어요",
                ctaEnabled = gearP,
                onCta = onOpenSheet,
            )
        }
    }
}
