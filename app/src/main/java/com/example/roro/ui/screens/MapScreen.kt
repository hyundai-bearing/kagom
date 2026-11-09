package com.example.roro.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roro.core.AreaState
import com.example.roro.core.Examples
import com.example.roro.core.Items
import com.example.roro.core.KoreaMap
import com.example.roro.core.Landmarks
import com.example.roro.core.MapCamera
import com.example.roro.core.MapProjection
import com.example.roro.core.MapRegion
import com.example.roro.core.MapSpot
import com.example.roro.core.MapTally
import com.example.roro.core.Severity
import com.example.roro.core.Tally
import com.example.roro.data.KoreaMapAsset
import com.example.roro.ui.components.Chip
import com.example.roro.ui.components.LandmarkDetailPopup
import com.example.roro.ui.components.LandmarkPin
import com.example.roro.ui.components.LockGlyph
import com.example.roro.ui.components.NorthArrowGlyph
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.CheckGlyph
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.ln

/*
 * SCR-05 랜드마크 지도 (가이드 v1.2 2.1, Figma 149:4177 · 149:4378 · 149:4593 · 152:4519 · 152:4670 · 151:4348~4546)
 *
 *  L1 전국 (시·도)  →  L2 시·도 (시·군·구)  →  L3 시·군·구 (읍·면·동 = 구획)
 *  칸을 누르면 다음 단계, 뒤로 버튼·브레드크럼·시스템 뒤로·두 손가락 오므리기 = 위 단계.
 *  L3에서 구획을 누르면 선택(점선) + 그 구획 랜드마크 3곳의 핀, 핀을 누르면 상세 팝업 (기어 P에서만).
 *  칸 색: 미해금(안개 회색) · 일부 해금(연녹) · 모두 해금(녹). 상위 칸은 하위 해금 수를 합산 (MapTally).
 */

// ───────────── 데이터 ─────────────

/** 지도 랜드마크: 실제 랜드마크 + (시연 '예시 데이터'를 켜면) Figma 예시 */
fun mapSpots(visited: Set<String>, unlockPending: Set<String>, examples: Boolean): List<MapSpot> =
    Landmarks.all.map { lm ->
        MapSpot(lm.id, lm.name, lm.unitId, lm.areaName, lm.lat, lm.lon, lm.rewardItemId,
            visited = lm.id in visited, unlocking = lm.id in unlockPending)
    } + if (examples) Examples.mapSpots else emptyList()

private fun MapSpot.pin(): PinState = when {
    !visited -> PinState.Locked
    unlocking -> PinState.Unlocking
    else -> PinState.Unlocked
}

private fun rewardOf(id: String?) = Items.byId(id) ?: Examples.item(id)

/** 핀 이름 카드 둘째 줄 (Figma: "방문 완료 · 빨간 고무 앞치마" / "미방문 · 보상 예시 머리 아이템 03") */
private fun pinStatus(s: MapSpot): String {
    val reward = rewardOf(s.rewardItemId)?.name
    return if (s.visited) "방문 완료 · ${reward ?: ""}" else "미방문 · 보상 ${reward ?: "준비 중"}"
}

/** 지도 칸 하나의 Path (평면 좌표, MapProjection)와 평면 bbox */
private class RegionShape(val region: MapRegion) {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        for (r in region.rings) {
            moveTo(MapProjection.x(r[0]), MapProjection.y(r[1]))
            for (i in 2 until r.size step 2) lineTo(MapProjection.x(r[i]), MapProjection.y(r[i + 1]))
            close()
        }
    }
    val left = MapProjection.x(region.bounds.west)
    val right = MapProjection.x(region.bounds.east)
    val top = MapProjection.y(region.bounds.north)
    val bottom = MapProjection.y(region.bounds.south)
    val labelX = MapProjection.x(region.labelLon)
    val labelY = MapProjection.y(region.labelLat)
}

private class MapBase(val map: KoreaMap) {
    val provinces = map.provinces.map(::RegionShape)
    val units = map.units.map(::RegionShape)
}

@Volatile private var baseCache: MapBase? = null
private val areaShapeCache = HashMap<String, List<RegionShape>>()

/** 지도 탐색 상태 */
@Stable
private class MapNav {
    var provinceId by mutableStateOf<String?>(null)
    var unitId by mutableStateOf<String?>(null)
    var areaId by mutableStateOf<String?>(null)
    var popupSpot by mutableStateOf<String?>(null)

    /** 1 전국, 2 시·도, 3 시·군·구 (Figma layer=1/2/3) */
    val layer get() = when { unitId != null -> 3; provinceId != null -> 2; else -> 1 }

    fun toCountry() { provinceId = null; unitId = null; areaId = null; popupSpot = null }
    fun toProvince(id: String) { provinceId = id; unitId = null; areaId = null; popupSpot = null }
    fun toUnit(province: String?, unit: String) { provinceId = province; unitId = unit; areaId = null; popupSpot = null }

    /** 한 단계 위로 (팝업이 열려 있으면 팝업만 닫는다) */
    fun back() {
        when {
            popupSpot != null -> popupSpot = null
            unitId != null -> { unitId = null; areaId = null }
            provinceId != null -> provinceId = null
        }
    }
}

// ───────────── 화면 ─────────────

/**
 * [focusSpot]: 다른 화면(치장 '지도에서 보기')에서 넘어올 때 보여 줄 랜드마크. 처리하면 [onFocusDone]
 */
@Composable
fun MapScreen(spots: List<MapSpot>, gearP: Boolean, focusSpot: String? = null, onFocusDone: () -> Unit = {}) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val context = LocalContext.current
    val base by produceState(baseCache) {
        if (value == null) value = withContext(Dispatchers.Default) {
            runCatching { MapBase(KoreaMapAsset.load(context)).also { baseCache = it } }
                .onFailure { Log.e("Kagom", "지도 데이터를 읽지 못함", it) }.getOrNull()
        }
    }
    val nav = remember { MapNav() }
    BackHandler(enabled = nav.layer > 1 || nav.popupSpot != null) { nav.back() }

    val b = base
    val province = b?.map?.province(nav.provinceId)
    val unit = b?.map?.unit(nav.unitId)
    // L3 구획: 시·도 파일 하나를 들어갈 때 읽는다
    val areaFile = province?.file
    val provinceAreas by produceState<List<RegionShape>?>(areaFile?.let { synchronized(areaShapeCache) { areaShapeCache[it] } }, areaFile) {
        if (areaFile == null) { value = null; return@produceState }
        if (value == null) value = withContext(Dispatchers.Default) {
            KoreaMapAsset.loadAreas(context, areaFile).map(::RegionShape).also { synchronized(areaShapeCache) { areaShapeCache[areaFile] = it } }
        }
    }
    val areas = remember(provinceAreas, nav.unitId) { provinceAreas?.filter { it.region.parentId == nav.unitId }.orEmpty() }

    // 처음 들어간 시·군·구: 랜드마크가 있는 구획(방문한 곳 먼저)을 고른다
    LaunchedEffect(nav.unitId, areas.isNotEmpty()) {
        if (nav.unitId != null && nav.areaId == null && areas.isNotEmpty()) {
            val inUnit = spots.filter { it.unitId == nav.unitId }
            nav.areaId = (inUnit.firstOrNull { it.visited } ?: inUnit.firstOrNull())?.areaId?.takeIf { id -> areas.any { it.region.id == id } }
        }
    }
    // 다른 화면에서 랜드마크를 보여 달라고 했을 때
    LaunchedEffect(focusSpot, b) {
        val spot = spots.firstOrNull { it.id == focusSpot } ?: return@LaunchedEffect
        val u = b?.map?.unit(spot.unitId) ?: return@LaunchedEffect
        nav.toUnit(u.parentId, u.id)
        nav.areaId = spot.areaId
        if (gearP) nav.popupSpot = spot.id
        onFocusDone()
    }

    val tallyOf: (RegionShape) -> Tally = { s ->
        val m = b!!.map
        when {
            s.region.parentId == null -> MapTally.province(s.region, m, spots)
            m.unit(s.region.id) != null -> MapTally.unit(s.region, spots)
            else -> MapTally.area(s.region.id, spots)
        }
    }
    val headerTally = when {
        b == null -> Tally.ZERO
        unit != null -> MapTally.unit(unit, spots)
        province != null -> MapTally.province(province, b.map, spots)
        else -> MapTally.country(b.map, spots)
    }
    val title = unit?.name ?: province?.name ?: "전국"
    val crumbs = buildList {
        if (nav.layer > 1) add("전국" to { nav.toCountry() })
        if (nav.layer > 2 && province != null) add(province.name to { nav.toProvince(province.id) })
    }

    val canvas: @Composable (Modifier) -> Unit = { m ->
        if (b == null) {
            Box(m.clip(RoundedCornerShape(KagomRadius.r24)).background(KagomColor.Map.sea), contentAlignment = Alignment.Center) {
                Text("지도를 불러오는 중이에요", style = KagomType.Body16, color = KagomColor.Text.secondary)
            }
        } else {
            val shown = when (nav.layer) {
                1 -> b.provinces
                2 -> b.units.filter { it.region.parentId == nav.provinceId }
                else -> areas
            }
            MapCanvas(b, shown, tallyOf, nav, spots.filter { nav.areaId != null && it.areaId == nav.areaId }, gearP, split,
                province?.let { b.map.unitsOf(it.id).all { u -> u.name.endsWith("구") || u.name.endsWith("군") } } == true, m)
        }
    }
    val panel: @Composable (Modifier) -> Unit = { m ->
        if (b != null) MapPanel(b, nav, spots, areas, tallyOf, gearP, split, m)
    }

    Box(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        if (split) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                MapHeader(title, crumbs, headerTally, nav.layer > 1, nav::back, split = true)
                Spacer(Modifier.height(KagomSpace.s12))
                canvas(Modifier.fillMaxWidth().height(420.dp))
                Spacer(Modifier.height(KagomSpace.s12))
                panel(Modifier.fillMaxWidth().weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 28.dp)) {
                MapHeader(title, crumbs, headerTally, nav.layer > 1, nav::back, split = false)
                Spacer(Modifier.height(KagomSpace.s16))
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    canvas(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(KagomSpace.s16))
                    panel(Modifier.width(360.dp).fillMaxHeight())
                }
            }
        }
    }
}

// ───────────── 머리글 ─────────────

@Composable
private fun MapHeader(title: String, crumbs: List<Pair<String, () -> Unit>>, tally: Tally, canBack: Boolean, onBack: () -> Unit, split: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
        if (canBack) BackButton(if (split) 44.dp else 48.dp, onBack)
        if (split) {
            Column(Modifier.weight(1f)) {
                if (crumbs.isNotEmpty()) Text(crumbs.joinToString(" › ") { it.first }, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                Text(title, style = KagomType.Heading22, color = KagomColor.Text.primary)
            }
            UnlockChip(tally)
        } else {
            crumbs.forEach { (name, go) ->
                Text(name, style = KagomType.Heading20, color = KagomColor.Text.tertiary,
                    modifier = Modifier.clip(RoundedCornerShape(KagomRadius.r8)).clickable(role = Role.Button, onClick = go).padding(horizontal = 2.dp))
                Text(">", style = KagomType.Heading20, color = KagomColor.Text.tertiary)
            }
            Text(title, style = KagomType.Title28, color = KagomColor.Text.primary)
            UnlockChip(tally)
            Spacer(Modifier.weight(1f))
            Legend()
        }
    }
}

@Composable
private fun UnlockChip(t: Tally) {
    Chip("해금 %,d / %,d".format(t.unlocked, t.total), KagomColor.Severity.bg(Severity.OK), KagomColor.Severity.fg(Severity.OK), height = 40.dp)
}

@Composable
private fun BackButton(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(KagomColor.Surface.card).border(1.dp, KagomColor.Border.default, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "위 단계로" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val w = this.size.width; val h = this.size.height; val s = 2.5.dp.toPx()
            drawLine(KagomColor.Text.primary, Offset(w * 0.7f, h * 0.12f), Offset(w * 0.3f, h * 0.5f), s, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(KagomColor.Text.primary, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.7f, h * 0.88f), s, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
        LegendItem(stateColor(AreaState.LOCKED), "미해금 · 안개")
        LegendItem(stateColor(AreaState.PARTIAL), "일부 해금 (1~2/3)")
        LegendItem(stateColor(AreaState.COMPLETE), "모두 해금 (3/3)")
        Canvas(Modifier.size(14.dp)) {
            drawRoundRect(KagomColor.Text.primary, cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
        }
        Text("선택 중", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Box(Modifier.size(14.dp).background(color, RoundedCornerShape(3.dp)))
    Text(label, style = KagomType.Caption14, color = KagomColor.Text.tertiary, modifier = Modifier.padding(end = 6.dp))
}

private fun stateColor(s: AreaState) = when (s) {
    AreaState.LOCKED -> KagomColor.Map.landFogged
    AreaState.PARTIAL -> KagomColor.Map.landPartial
    AreaState.COMPLETE -> KagomColor.Map.landUnlocked
}

/** 시·도 라벨: "강원특별자치도" → "강원" */
private fun String.shortProvince(): String = when (this) {
    "충청북도" -> "충북"; "충청남도" -> "충남"; "경상북도" -> "경북"; "경상남도" -> "경남"; "전라남도" -> "전남"; "전라북도" -> "전북"
    "전남광주통합특별시" -> "전남광주"
    else -> removeSuffix("특별자치도").removeSuffix("특별자치시").removeSuffix("특별시").removeSuffix("광역시").removeSuffix("도")
}

// ───────────── 지도 캔버스 ─────────────

@Composable
private fun MapCanvas(
    base: MapBase,
    shown: List<RegionShape>,
    tallyOf: (RegionShape) -> Tally,
    nav: MapNav,
    pinSpots: List<MapSpot>,
    gearP: Boolean,
    split: Boolean,
    metro: Boolean,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(KagomRadius.r24)).background(KagomColor.Map.sea)) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val pad = with(density) { (if (split) 20.dp else 48.dp).toPx() }
        val bounds = when (nav.layer) {
            1 -> base.map.bounds
            2 -> base.map.province(nav.provinceId)?.bounds ?: base.map.bounds
            else -> base.map.unit(nav.unitId)?.bounds ?: base.map.bounds
        }
        val target = remember(w, h, bounds) { MapCamera.fit(bounds, w, h, pad) }
        val cx = remember { Animatable(target.cx) }
        val cy = remember { Animatable(target.cy) }
        val ls = remember { Animatable(ln(target.scale)) }
        LaunchedEffect(target) {
            val spec = tween<Float>(450, easing = FastOutSlowInEasing)
            launch { cx.animateTo(target.cx, spec) }
            launch { cy.animateTo(target.cy, spec) }
            ls.animateTo(ln(target.scale), spec)
        }
        fun camera() = MapCamera(cx.value, cy.value, exp(ls.value))

        // 핀끼리 너무 가까우면(실제 좌표가 수백 m 안) 화면에서만 부채꼴로 벌린다: 핀 id → 실제 위치에서 옮길 양(px)
        val nudge = remember(target, pinSpots, w, h) {
            with(density) {
                val minGap = 52.dp.toPx()
                val pts = pinSpots.associate { it.id to Offset(target.toScreenX(MapProjection.x(it.lon.toFloat()), w), target.toScreenY(MapProjection.y(it.lat.toFloat()), h)) }
                val close = pinSpots.filter { a -> pinSpots.any { b -> b !== a && (pts[a.id]!! - pts[b.id]!!).getDistance() < minGap } }
                val moved = HashMap(pts)
                if (close.size >= 2) {
                    val mx = close.map { pts[it.id]!!.x }.average().toFloat(); val my = close.map { pts[it.id]!!.y }.average().toFloat()
                    val r = minGap * 0.6f / kotlin.math.sin(Math.PI / close.size).toFloat().coerceAtLeast(0.5f)
                    close.withIndex().forEach { (i, s) ->
                        val a = Math.PI * 2 * i / close.size - Math.PI / 2
                        moved[s.id] = Offset(mx + r * kotlin.math.cos(a).toFloat(), my + r * kotlin.math.sin(a).toFloat())
                    }
                }
                // 지도 가장자리 밖으로 나간 핀은 안으로 들인다. 부채꼴로 벌린 무리는 모양을 지키게 통째로 옮긴다
                val edge = 30.dp.toPx()
                fun inward(ps: List<Offset>) = Offset(
                    when { ps.minOf { it.x } < edge -> edge - ps.minOf { it.x }; ps.maxOf { it.x } > w - edge -> w - edge - ps.maxOf { it.x }; else -> 0f },
                    when { ps.minOf { it.y } < edge -> edge - ps.minOf { it.y }; ps.maxOf { it.y } > h - edge -> h - edge - ps.maxOf { it.y }; else -> 0f },
                )
                val groupShift = if (close.size >= 2) inward(close.map { moved[it.id]!! }) else Offset.Zero
                pinSpots.associate { s ->
                    val p = moved[s.id]!!
                    s.id to (p + (if (close.size >= 2 && s in close) groupShift else inward(listOf(p))) - pts[s.id]!!)
                }.filterValues { it != Offset.Zero }
            }
        }
        fun pinAt(s: MapSpot, cam: MapCamera) =
            Offset(cam.toScreenX(MapProjection.x(s.lon.toFloat()), w), cam.toScreenY(MapProjection.y(s.lat.toFloat()), h)) + (nudge[s.id] ?: Offset.Zero)

        // 핀 이름 카드 자리: 도착할 시점(target) 기준으로 오른쪽 → 왼쪽 → 위 → 아래 중 다른 핀·카드와 안 겹치는 곳, 없으면 핀만.
        // 핀 id → 핀 가운데 기준 카드 영역(px). 구획 라벨도 이 자리를 피한다
        val cards = remember(target, pinSpots, w, h, split, nudge) {
            with(density) {
                val placed = ArrayList<Rect>()
                pinSpots.forEach { s -> val c = pinAt(s, target); placed += Rect(c.x - 26.dp.toPx(), c.y - 26.dp.toPx(), c.x + 26.dp.toPx(), c.y + 26.dp.toPx()) }
                pinSpots.associate { s ->
                    val status = pinStatus(s)
                    val cw = (if (split) s.name.length * 14 + 24 else maxOf(s.name.length * 16, status.length * 13) + 28).dp.toPx()
                    val ch = (if (split) 28 else 54).dp.toPx()
                    val gap = 30.dp.toPx()
                    val c = pinAt(s, target)
                    val options = listOf(
                        Rect(c.x + gap, c.y - ch / 2, c.x + gap + cw, c.y + ch / 2),
                        Rect(c.x - gap - cw, c.y - ch / 2, c.x - gap, c.y + ch / 2),
                        Rect(c.x - cw / 2, c.y - gap - ch, c.x + cw / 2, c.y - gap),
                        Rect(c.x - cw / 2, c.y + gap, c.x + cw / 2, c.y + gap + ch),
                    )
                    val pick = options.firstOrNull { r -> r.left >= 4 && r.top >= 4 && r.right <= w - 4 && r.bottom <= h - 4 && placed.none { it.overlaps(r) } }
                    if (pick != null) placed += pick
                    s.id to pick?.translate(-c.x, -c.y)
                }
            }
        }

        val measurer = rememberTextMeasurer(cacheSize = 256)
        val nameStyle = KagomType.Caption14Strong.copy(color = KagomColor.Text.primary)
        val countStyle = KagomType.Caption14.copy(color = KagomColor.Text.secondary)
        val countOn = KagomType.Caption14.copy(color = KagomColor.Severity.fg(Severity.OK))
        // 조작 안내 (Full만)
        val hint: String? = if (split) null else when (nav.layer) {
            1 -> "시·도를 누르면 그 지역으로 들어가요"
            2 -> if (metro) "구·군을 누르면 구획 지도로 들어가요" else "시·군을 누르면 구획 지도로 들어가요"
            else -> if (gearP) "핀을 누르면 상세를 볼 수 있어요 · 구획을 누르면 그 구획의 핀이 열려요"
            else "구획을 누르면 그 구획의 핀이 열려요 · 상세는 정차 중에 볼 수 있어요"
        }
        val latestShown by rememberUpdatedState(shown)

        Canvas(
            Modifier.fillMaxSize().mapGestures(
                onTap = { pos ->
                    if (nav.popupSpot != null) { nav.popupSpot = null; return@mapGestures }
                    val cam = camera()
                    val lon = MapProjection.lon(cam.toPlaneX(pos.x, w)); val lat = MapProjection.lat(cam.toPlaneY(pos.y, h))
                    val hit = latestShown.firstOrNull { it.region.contains(lon, lat) }?.region ?: return@mapGestures
                    when (nav.layer) {
                        1 -> nav.toProvince(hit.id)
                        2 -> nav.toUnit(nav.provinceId, hit.id)
                        else -> { nav.areaId = hit.id; nav.popupSpot = null }
                    }
                },
                onPinchIn = nav::back,
            ),
        ) {
            val cam = camera()
            val onePx = 1f / cam.scale
            withTransform({
                translate(size.width / 2 - cam.cx * cam.scale, size.height / 2 - cam.cy * cam.scale)
                scale(cam.scale, cam.scale, Offset.Zero)
            }) {
                shown.forEach { drawPath(it.path, stateColor(tallyOf(it).state)) }
                shown.forEach { drawPath(it.path, KagomColor.Surface.card, style = Stroke(1.5.dp.toPx() * onePx, join = StrokeJoin.Round)) }
                // 선택 중: 점선 외곽선 (다른 상태 위에 겹친다)
                shown.firstOrNull { it.region.id == nav.areaId }?.let { sel ->
                    drawPath(sel.path, KagomColor.Text.primary, style = Stroke(3.dp.toPx() * onePx, join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx() * onePx, 4.dp.toPx() * onePx))))
                }
            }

            // 라벨 칩 "이름 n/N": 큰 칸부터, 겹치면 위아래·좌우로 비켜 보고 안 되면 뺀다. L3의 작은 동들은 하나로 묶는다
            val placed = ArrayList<Rect>()
            // 모서리 표시(북쪽 · 출처 · 조작 안내) 자리는 비워 둔다. 크기는 아래 Text들과 같은 스타일·여백으로 잰다
            run {
                placed += Rect(size.width - 60.dp.toPx(), 0f, size.width, 64.dp.toPx())
                val credit = measurer.measure("© OpenStreetMap contributors", KagomType.Caption14)
                placed += Rect(size.width - credit.size.width - 36.dp.toPx(), size.height - credit.size.height - 28.dp.toPx(), size.width, size.height)
                if (hint != null) {
                    val hl = measurer.measure(hint, KagomType.Body16Strong)
                    placed += Rect(0f, size.height - hl.size.height - 48.dp.toPx(), hl.size.width + 60.dp.toPx(), size.height)
                }
            }
            // 핀과 핀 이름 카드 자리는 비워 둔다
            pinSpots.forEach { s ->
                val p = pinAt(s, cam)
                placed += Rect(p.x - 26.dp.toPx(), p.y - 26.dp.toPx(), p.x + 26.dp.toPx(), p.y + 26.dp.toPx())
                cards[s.id]?.let { placed += it.translate(p) }
            }
            fun chip(name: String, t: Tally, x: Float, y: Float, selected: Boolean) {
                val nl = measurer.measure(name, nameStyle)
                val cl = measurer.measure(" ${t.unlocked}/${"%,d".format(t.total)}" + if (t.state == AreaState.COMPLETE && nav.layer == 3) "" else "", if (t.unlocked > 0) countOn else countStyle)
                val padH = 10.dp.toPx(); val padV = 5.dp.toPx()
                val cw = nl.size.width + cl.size.width + 2 * padH; val ch = maxOf(nl.size.height, cl.size.height) + 2 * padV
                val sx = cam.toScreenX(x, size.width); val sy = cam.toScreenY(y, size.height)
                // 제자리 → 위·아래 → 좌우 반 칸. 고른 구획은 꼭 보여야 해서 두 줄 위·아래까지 더 찾아본다
                val dys = if (selected) floatArrayOf(0f, -0.9f, 0.9f, -1.8f, 1.8f) else floatArrayOf(0f, -0.9f, 0.9f)
                val tries = dys.flatMap { dy -> listOf(0f, -0.5f, 0.5f).map { dx -> Offset(dx * cw, dy * ch) } }
                val r = tries.map { o -> Rect(Offset(sx - cw / 2 + o.x, sy - ch / 2 + o.y), Size(cw, ch)) }.firstOrNull { c ->
                    c.left >= 4 && c.top >= 4 && c.right <= size.width - 4 && c.bottom <= size.height - 4 && placed.none { it.overlaps(c) }
                }
                if (r == null && !selected) return
                val rr = r ?: Rect(Offset(sx - cw / 2, sy - ch / 2), Size(cw, ch))
                placed += rr
                drawRoundRect(KagomColor.Surface.card, rr.topLeft, rr.size, CornerRadius(ch / 2))
                if (selected) drawRoundRect(KagomColor.Text.primary, rr.topLeft, rr.size, CornerRadius(ch / 2), style = Stroke(2.dp.toPx()))
                drawText(nl, topLeft = Offset(rr.left + padH, rr.top + (ch - nl.size.height) / 2))
                drawText(cl, topLeft = Offset(rr.left + padH + nl.size.width, rr.top + (ch - cl.size.height) / 2))
            }
            fun screenW(s: RegionShape) = (s.right - s.left) * cam.scale
            fun screenH(s: RegionShape) = (s.bottom - s.top) * cam.scale
            val byArea = shown.sortedByDescending { screenW(it) * screenH(it) }
            val small = if (nav.layer == 3) byArea.filter {
                it.region.id != nav.areaId && it.region.name.endsWith("동") && (screenW(it) < 90.dp.toPx() || screenH(it) < 40.dp.toPx())
            } else emptyList()
            val grouped = small.size >= 3
            // 선택 구획 먼저
            byArea.firstOrNull { it.region.id == nav.areaId }?.let { chip(it.region.name, tallyOf(it), it.labelX, it.labelY, true) }
            if (grouped) {
                val t = small.fold(Tally.ZERO) { acc, s -> acc + tallyOf(s) }
                chip("시내 ${small.size}개 동", t, small.map { it.labelX }.average().toFloat(), small.map { it.labelY }.average().toFloat(), false)
            }
            // L1은 오른쪽 목록과 같은 순서(해금 많은 순 → 전체 큰 순)로 자리를 잡는다. 넓이 순이면 작은 서울이 경기·인천에 밀려 빠진다
            val order = if (nav.layer == 1) shown.sortedWith(
                compareByDescending<RegionShape> { tallyOf(it).unlocked }.thenByDescending { tallyOf(it).total },
            ) else byArea
            order.forEach { s ->
                if (s.region.id == nav.areaId || (grouped && s in small)) return@forEach
                val name = if (nav.layer == 1) s.region.name.shortProvince() else s.region.name
                chip(name, tallyOf(s), s.labelX, s.labelY, false)
            }
        }

        // L3: 선택한 구획의 랜드마크 핀 + 이름 카드 (카드 자리는 위 `cards`)
        pinSpots.forEach { s ->
            fun pinCenter(cam: MapCamera) = Offset(cam.toScreenX(MapProjection.x(s.lon.toFloat()), w), cam.toScreenY(MapProjection.y(s.lat.toFloat()), h)) +
                (nudge[s.id] ?: Offset.Zero)
            cards[s.id]?.let { rel ->
                val mod = Modifier.offset { val c = pinCenter(camera()); IntOffset((c.x + rel.left).toInt(), (c.y + rel.top).toInt()) }
                if (!split) Column(mod.background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r12)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(s.name, style = KagomType.Body16Strong, color = KagomColor.Text.primary)
                    Text(pinStatus(s), style = KagomType.Caption14, color = KagomColor.Text.secondary)
                } else Text(s.name, style = KagomType.Caption14Strong, color = KagomColor.Text.primary,
                    modifier = mod.background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.full)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
            LandmarkPin(s.pin(), Modifier.offset { val c = pinCenter(camera()); IntOffset((c.x - 24.dp.toPx()).toInt(), (c.y - 24.dp.toPx()).toInt()) }) {
                if (gearP) nav.popupSpot = s.id
            }
        }

        // 랜드마크 상세 팝업 (Full: 핀 옆, Split: 지도 위 가운데)
        pinSpots.firstOrNull { it.id == nav.popupSpot }?.let { s ->
            val reward = rewardOf(s.rewardItemId)
            val popW = if (split) 340.dp else 440.dp
            LandmarkDetailPopup(s.name, reward?.name ?: "준비 중", reward?.mark ?: "?", s.visited,
                Modifier.layout { m, c ->
                    val p = m.measure(c)
                    layout(p.width, p.height) {
                        val pp = pinAt(s, camera())
                        val margin = 12.dp.toPx()
                        // Split: 가운데 정렬, 핀을 가리지 않게 핀이 위쪽이면 아래(OSM 출처 위)에, 아래쪽이면 위에
                        if (split) p.place(((w - p.width) / 2).toInt(), (if (pp.y < h / 2) h - p.height - 40.dp.toPx() else margin).toInt())
                        else p.place((pp.x + 36.dp.toPx()).coerceAtMost(w - p.width - margin).toInt(),
                            (pp.y - 60.dp.toPx()).coerceIn(margin, (h - p.height - margin).coerceAtLeast(margin)).toInt())
                    }
                }.clickable(role = Role.Button) { nav.popupSpot = null },
                width = popW, itemId = reward?.id)
        }

        // 북쪽 표시
        Column(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NorthArrowGlyph(Modifier.size(16.dp, 20.dp), KagomColor.Text.primary)
            Text("N", style = KagomType.Caption14Strong, color = KagomColor.Text.primary)
        }
        // 조작 안내 (Full) · 출처. 라벨 칩은 이 자리를 비워 둔다 (MapCanvas 라벨 부분)
        if (hint != null) Text(
            hint,
            style = KagomType.Body16Strong, color = KagomColor.Text.inverse,
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                .background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.r12)).padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Text("© OpenStreetMap contributors", style = KagomType.Caption14, color = KagomColor.Text.tertiary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/**
 * 탭(손가락 하나로 거의 안 움직이고 뗌)과 두 손가락 오므리기(처음 간격의 75% 아래). 오므리기는 제스처마다 한 번.
 */
@Composable
private fun Modifier.mapGestures(onTap: (Offset) -> Unit, onPinchIn: () -> Unit): Modifier {
    val tap by rememberUpdatedState(onTap)
    val pinch by rememberUpdatedState(onPinchIn)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            var multi = false; var moved = false; var fired = false; var start = 0f
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val d = (pressed[0].position - pressed[1].position).getDistance()
                    if (!multi) { multi = true; start = d }
                    else if (!fired && start > 0f && d < start * 0.75f) { fired = true; pinch() }
                    ev.changes.forEach { it.consume() }
                } else if (!multi) {
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c != null && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                }
                if (pressed.isEmpty()) {
                    val up = ev.changes.firstOrNull { it.id == down.id }
                    if (!multi && !moved && up != null && !up.isConsumed) tap(down.position)
                    break
                }
            }
        }
    }
}

// ───────────── 오른쪽 패널 (Split: 지도 아래) ─────────────

@Composable
private fun MapPanel(
    base: MapBase,
    nav: MapNav,
    spots: List<MapSpot>,
    areas: List<RegionShape>,
    tallyOf: (RegionShape) -> Tally,
    gearP: Boolean,
    split: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier.background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r24)).verticalScroll(rememberScrollState())
            .padding(if (split) 20.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s4),
    ) {
        when (nav.layer) {
            1 -> {
                PanelTitle("시·도별 해금 현황", if (split) "시·도를 누르면 그 지역으로 들어가요" else "구획을 누르면 다음 단계로 들어가요 · 숫자는 해금 / 전체 랜드마크")
                MapTally.ranked(base.provinces, tallyOf).forEach { s -> CountRow(s.region.name, tallyOf(s)) { nav.toProvince(s.region.id) } }
            }
            2 -> {
                val p = base.map.province(nav.provinceId)
                val units = base.units.filter { it.region.parentId == nav.provinceId }
                val metro = units.all { it.region.name.endsWith("구") || it.region.name.endsWith("군") }
                PanelTitle("${p?.name?.shortProvince() ?: ""} ${if (metro) "구·군별" else "시·군별"} 해금 현황",
                    "구획을 누르면 다음 단계로 들어가요 · 숫자는 해금 / 전체 랜드마크")
                MapTally.ranked(units, tallyOf).forEach { s -> CountRow(s.region.name, tallyOf(s)) { nav.toUnit(nav.provinceId, s.region.id) } }
            }
            else -> AreaPanel(nav, spots, areas, tallyOf, gearP, split)
        }
    }
}

@Composable
private fun PanelTitle(title: String, sub: String) {
    Text(title, style = KagomType.Heading22, color = KagomColor.Text.primary)
    Text(sub, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
    Spacer(Modifier.height(KagomSpace.s12))
}

@Composable
private fun CountRow(name: String, t: Tally, label: String = name, onClick: () -> Unit) {
    val on = t.unlocked > 0
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(KagomRadius.r8)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$name, 해금 ${t.unlocked} / ${t.total}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12),
    ) {
        Box(Modifier.size(8.dp).background(if (on) KagomColor.Map.landUnlocked else KagomColor.Border.default, CircleShape))
        Text(label, style = if (on) KagomType.Body16Strong else KagomType.Body16, color = if (on) KagomColor.Text.primary else KagomColor.Text.secondary,
            modifier = Modifier.weight(1f))
        Text("%,d / %,d".format(t.unlocked, t.total), style = KagomType.Body16Strong,
            color = if (on) KagomColor.Severity.fg(Severity.OK) else KagomColor.Text.tertiary)
    }
}

/** L3: 선택 구획 카드(랜드마크 3곳) + 다른 구획 목록 */
@Composable
private fun AreaPanel(nav: MapNav, spots: List<MapSpot>, areas: List<RegionShape>, tallyOf: (RegionShape) -> Tally, gearP: Boolean, split: Boolean) {
    val sel = areas.firstOrNull { it.region.id == nav.areaId }
    if (sel == null) {
        PanelTitle("구획을 골라 주세요", "구획을 누르면 그 구획의 랜드마크 3곳을 볼 수 있어요")
    } else {
        val inArea = spots.filter { it.areaId == sel.region.id }
        val t = tallyOf(sel)
        PanelTitle("${sel.region.name} 랜드마크",
            if (split) "랜드마크 3개 · 미방문이어도 이름과 보상을 미리 볼 수 있어요"
            else "선택한 구획의 랜드마크 3개 · 미방문이어도 이름과 보상을 미리 볼 수 있어요. 방문하면 안개가 걷히고 보상을 받아요")
        Column(
            Modifier.fillMaxWidth().background(KagomColor.Severity.bg(Severity.OK), RoundedCornerShape(KagomRadius.r16)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s12),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sel.region.name, style = KagomType.Body18, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
                Text("${t.unlocked} / ${t.total} 해금", style = KagomType.Body16Strong, color = KagomColor.Severity.fg(Severity.OK))
            }
            inArea.take(MapTally.PER_AREA).forEach { s -> SpotRow(s) { if (gearP) nav.popupSpot = s.id } }
            repeat(MapTally.PER_AREA - inArea.size.coerceAtMost(MapTally.PER_AREA)) { PendingSpotRow() }
        }
    }
    val others = areas.filter { it.region.id != nav.areaId }
        .sortedWith(compareByDescending<RegionShape> { tallyOf(it).state == AreaState.COMPLETE }
            .thenByDescending { tallyOf(it).unlocked }
            .thenByDescending { s -> spots.any { it.areaId == s.region.id } }
            .thenBy { it.region.name })
    if (others.isNotEmpty()) {
        Spacer(Modifier.height(KagomSpace.s12))
        Text("다른 구획", style = KagomType.Caption14Strong, color = KagomColor.Text.tertiary)
        others.forEach { s ->
            val t = tallyOf(s)
            val hint = spots.firstOrNull { it.areaId == s.region.id }?.name
            val label = when {
                t.state == AreaState.COMPLETE -> "${s.region.name} · 모두 해금"
                hint != null -> "${s.region.name} · $hint 일대"
                else -> s.region.name
            }
            CountRow(s.region.name, t, label) { nav.areaId = s.region.id; nav.popupSpot = null }
        }
    }
}

@Composable
private fun SpotRow(s: MapSpot, onClick: () -> Unit) {
    val reward = rewardOf(s.rewardItemId)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(KagomRadius.r8)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12),
    ) {
        Box(Modifier.size(36.dp).background(if (s.visited) KagomColor.Accent.positive else KagomColor.Surface.dark.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center) {
            if (s.visited) CheckGlyph(Modifier.size(18.dp), KagomColor.Text.inverse, 2.5.dp) else LockGlyph(Modifier.size(14.dp), KagomColor.Text.inverse)
        }
        Column(Modifier.weight(1f)) {
            Text(s.name, style = KagomType.Body16Strong, color = KagomColor.Text.primary)
            Text(if (s.visited) "방문 완료 · ${reward?.name ?: ""} 획득" else "미방문 · 보상 ${reward?.name ?: "준비 중"}",
                style = KagomType.Caption14, color = KagomColor.Text.secondary)
        }
    }
}

/** 아직 정해지지 않은 랜드마크 자리 (구획당 3곳 중 남은 자리) */
@Composable
private fun PendingSpotRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
        Canvas(Modifier.size(36.dp)) {
            drawCircle(KagomColor.Text.tertiary, size.minDimension / 2 - 1.dp.toPx(),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        Column(Modifier.widthIn(max = 280.dp)) {
            Text("준비 중", style = KagomType.Body16Strong, color = KagomColor.Text.tertiary)
            Text("아직 정해지지 않은 랜드마크예요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
        }
    }
}
