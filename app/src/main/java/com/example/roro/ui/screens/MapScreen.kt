package com.example.roro.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roro.core.GeoBounds
import com.example.roro.core.Items
import com.example.roro.core.KoreaMap
import com.example.roro.core.Landmark
import com.example.roro.core.Landmarks
import com.example.roro.core.MapProjection
import com.example.roro.core.MapRegion
import com.example.roro.core.MapZoom
import com.example.roro.core.Severity
import com.example.roro.data.KoreaMapAsset
import com.example.roro.ui.components.LandmarkDetailPopup
import com.example.roro.ui.components.LandmarkPin
import com.example.roro.ui.components.NorthArrowGlyph
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.StatusBadge
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class LandmarkView(val landmark: Landmark, val pin: PinState) {
    val visited: Boolean get() = pin != PinState.Locked
}

private data class RegionProgress(val region: MapRegion, val landmarks: List<LandmarkView>) {
    val visited: Int = landmarks.count { it.visited }
    val count: String = "$visited/${landmarks.size}"
    val fill: Color
        get() = when {
            visited == 0 -> KagomColor.Map.landFogged
            visited == landmarks.size -> KagomColor.Map.landUnlocked
            else -> KagomColor.Map.landPartial
        }
}

@Composable
fun MapScreen(views: List<LandmarkView>, gearP: Boolean, onEquip: (String) -> Unit) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val context = LocalContext.current
    val result by produceState<Result<KoreaMap>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { KoreaMapAsset.load(context) }
                .onFailure { Log.e("Kagom", "지도 데이터를 읽지 못함", it) }
        }
    }
    var provinceId by rememberSaveable { mutableStateOf<String?>(null) }
    var unitId by rememberSaveable { mutableStateOf<String?>(null) }
    var districtId by rememberSaveable { mutableStateOf<String?>(null) }
    var pinId by rememberSaveable { mutableStateOf<String?>(null) }

    fun goNationwide() {
        pinId = null
        districtId = null
        unitId = null
        provinceId = null
    }

    fun goProvince() {
        pinId = null
        districtId = null
        unitId = null
    }

    fun goBack() {
        when {
            pinId != null -> pinId = null
            unitId != null -> goProvince()
            else -> goNationwide()
        }
    }

    BackHandler(enabled = provinceId != null || pinId != null, onBack = ::goBack)

    val loaded = result?.getOrNull()
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (result == null) "지도를 불러오는 중이에요" else "지도를 불러오지 못했어요.",
                style = KagomType.Body16, color = KagomColor.Text.secondary)
        }
        return
    }

    val province = loaded.province(provinceId)
    val unit = loaded.unit(unitId)
    val regions = remember(loaded, province, unit, views) { mapRegions(loaded, province, unit, views) }
    val district = unit?.let {
        regions.firstOrNull { it.region.id == districtId } ?: regions.firstOrNull { it.landmarks.isNotEmpty() }
    }
    val activeLandmarks = district?.landmarks.orEmpty()
    val selectedPin = activeLandmarks.firstOrNull { it.landmark.id == pinId }
    val count = district?.landmarks ?: regions.flatMap { it.landmarks }

    fun chooseRegion(id: String) {
        pinId = null
        when {
            unit != null -> districtId = id
            province != null -> unitId = id
            else -> provinceId = id
        }
    }

    val panelTitle = when {
        district != null -> "${district.region.name} 랜드마크"
        unit != null -> "${unit.name} 랜드마크"
        province != null -> "${province.name} 시·군별 해금 현황"
        else -> "시·도별 해금 현황"
    }
    val panelHint = when {
        district != null && activeLandmarks.isNotEmpty() -> "미방문이어도 이름과 보상을 미리 볼 수 있어요."
        unit != null && (district != null || regions.isEmpty()) -> "이 구획의 랜드마크는 준비 중이에요."
        unit != null -> "구획을 누르면 해당 랜드마크를 볼 수 있어요."
        else -> "구획을 누르면 다음 단계로 들어가요. 숫자는 해금 / 전체 랜드마크예요."
    }
    val mapContent: @Composable (Modifier) -> Unit = { modifier ->
        RegionMap(
            bounds = unit?.bounds ?: province?.bounds ?: loaded.bounds,
            regions = regions,
            outline = unit?.takeIf { regions.isEmpty() },
            district = district,
            selectedPin = selectedPin,
            split = split,
            showDistrictAttribution = unit != null && regions.isNotEmpty(),
            hint = when {
                unit != null -> "핀을 누르면 상세를 볼 수 있어요. 구획을 누르면 핀이 바뀌어요."
                province != null -> "시·군을 누르면 구획 지도로 들어가요."
                else -> "시·도를 누르면 그 지역으로 들어가요."
            },
            gearP = gearP,
            onRegion = ::chooseRegion,
            onPin = { pinId = if (pinId == it) null else it },
            onDismissPin = { pinId = null },
            onBack = if (province != null) ::goBack else null,
            onEquip = onEquip,
            modifier = modifier,
        )
    }
    val panel: @Composable (Modifier) -> Unit = { modifier ->
        RegionPanel(panelTitle, panelHint, regions, district, selectedPin, ::chooseRegion,
            onPin = { pinId = if (pinId == it) null else it }, modifier = modifier)
    }

    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page)
            .padding(horizontal = if (split) 20.dp else 36.dp, vertical = if (split) 16.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (province != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(KagomColor.Surface.card)
                        .border(1.dp, KagomColor.Border.default, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "이전 지도", onClick = ::goBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", style = KagomType.Title32, color = KagomColor.Text.primary) }
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Breadcrumb("전국", province?.let { { goNationwide() } })
                    province?.let {
                        Text("›", color = KagomColor.Text.tertiary)
                        Breadcrumb(it.name, unit?.let { { goProvince() } })
                    }
                }
                if (unit != null) Text(unit.name, style = KagomType.Body16Strong, color = KagomColor.Text.primary)
            }
            StatusBadge(Severity.OK, "해금 ${count.count { it.visited }}/${count.size}")
            if (!split) MapLegend()
        }
        if (split) {
            mapContent(Modifier.fillMaxWidth().weight(0.47f))
            panel(Modifier.fillMaxWidth().weight(0.53f))
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                mapContent(Modifier.weight(1f).fillMaxSize())
                panel(Modifier.width(360.dp).fillMaxSize())
            }
        }
    }
}

private fun mapRegions(
    map: KoreaMap, province: MapRegion?, unit: MapRegion?, views: List<LandmarkView>,
): List<RegionProgress> {
    if (unit != null) {
        val cityLandmarks = views.filter { it.landmark.unitId == unit.id }
        val districts = map.districtsOf(unit.id)
        val byDistrict = cityLandmarks.groupBy { view ->
            val lm = view.landmark
            districts.firstOrNull { it.contains(lm.lon.toFloat(), lm.lat.toFloat()) }?.id
        }
        val entries = districts.map { RegionProgress(it, byDistrict[it.id].orEmpty()) }
        val unassigned = byDistrict[null].orEmpty()
        return if (unassigned.isEmpty()) entries else entries + RegionProgress(unit, unassigned)
    }
    if (province != null) {
        val byUnit = views.groupBy { it.landmark.unitId }
        return map.units.filter { it.provinceId == province.id }.map { RegionProgress(it, byUnit[it.id].orEmpty()) }
    }
    val byProvince = views.groupBy { map.unit(it.landmark.unitId)?.provinceId }
    return map.provinces.map { RegionProgress(it, byProvince[it.id].orEmpty()) }
}

@Composable
private fun Breadcrumb(label: String, onClick: (() -> Unit)?) {
    val modifier = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    Text(label, modifier.padding(vertical = 6.dp), style = KagomType.Caption14Strong, color = KagomColor.Text.primary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MapLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            KagomColor.Map.landFogged to "미해금",
            KagomColor.Map.landPartial to "일부 해금",
            KagomColor.Map.landUnlocked to "모두 해금",
            KagomColor.Text.primary to "선택 중",
        ).forEach { (color, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                Text(label, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
            }
        }
    }
}

private fun MapRegion.outline() = Path().apply {
    fillType = PathFillType.EvenOdd
    for (ring in rings) {
        moveTo(MapProjection.x(ring[0]), MapProjection.y(ring[1]))
        for (i in 2 until ring.size step 2) lineTo(MapProjection.x(ring[i]), MapProjection.y(ring[i + 1]))
        close()
    }
}

private data class RegionLabel(val id: String, val text: TextLayoutResult, val rect: Rect)

@Composable
private fun RegionMap(
    bounds: GeoBounds,
    regions: List<RegionProgress>,
    outline: MapRegion?,
    district: RegionProgress?,
    selectedPin: LandmarkView?,
    split: Boolean,
    showDistrictAttribution: Boolean,
    hint: String,
    gearP: Boolean,
    onRegion: (String) -> Unit,
    onPin: (String) -> Unit,
    onDismissPin: () -> Unit,
    onBack: (() -> Unit)?,
    onEquip: (String) -> Unit,
    modifier: Modifier,
) {
    val paths = remember(regions) { regions.associate { it.region.id to it.region.outline() } }
    val outlinePath = remember(outline) { outline?.outline() }
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val labelStyle = KagomType.Caption14Strong.copy(color = KagomColor.Text.primary)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(KagomRadius.r24)).background(KagomColor.Map.sea)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val pad = with(density) { minOf(32.dp.toPx(), minOf(w, h) / 5f) }
        val camera = remember(bounds, w, h, pad) { MapZoom.fit(bounds, w, h, pad) }
        fun point(lon: Float, lat: Float) = Offset(
            camera.toScreenX(MapProjection.x(lon), w),
            camera.toScreenY(MapProjection.y(lat), h),
        )
        val pinLocations = district?.landmarks.orEmpty().associate { v ->
            v.landmark.id to point(v.landmark.lon.toFloat(), v.landmark.lat.toFloat())
        }
        val labels = remember(regions, district, camera, w, h, measurer, labelStyle) {
            val gap = with(density) { 5.dp.toPx() }
            val inset = with(density) { 8.dp.toPx() }
            val radius = with(density) { 26.dp.toPx() }
            val placed = mutableListOf<RegionLabel>()
            val pinRects = pinLocations.values.map { Rect(it - Offset(radius, radius), Size(radius * 2, radius * 2)) }
            val ordered = regions.sortedWith(
                compareByDescending<RegionProgress> { it.region.id == district?.region?.id }
                    .thenByDescending { it.landmarks.isNotEmpty() }
                    .thenByDescending { (it.region.bounds.east - it.region.bounds.west) * (it.region.bounds.north - it.region.bounds.south) },
            )
            for (entry in ordered) {
                val region = entry.region
                val p = point(region.labelLon, region.labelLat)
                val text = measurer.measure("${region.name.shortRegionName()}  ${entry.count}", labelStyle)
                val size = Size(text.size.width + inset * 2, text.size.height + gap * 2)
                val rect = Rect(p - Offset(size.width / 2, size.height / 2), size)
                if (rect.left < 0 || rect.top < 0 || rect.right > w || rect.bottom > h) continue
                if (placed.any { it.rect.overlaps(rect) } || pinRects.any { it.overlaps(rect) }) continue
                placed += RegionLabel(region.id, text, rect)
            }
            placed
        }
        val tap by rememberUpdatedState<(Offset) -> Unit> { pos ->
            val id = labels.firstOrNull { it.rect.contains(pos) }?.id ?: regions.firstOrNull { entry ->
                entry.region.contains(
                    MapProjection.lon(camera.toPlaneX(pos.x, w)),
                    MapProjection.lat(camera.toPlaneY(pos.y, h)),
                )
            }?.region?.id
            if (id != null) onRegion(id) else onDismissPin()
        }
        val back by rememberUpdatedState(onBack)
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                var moved = false
                var initialSpan: Float? = null
                var pinched = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val span = (pressed[0].position - pressed[1].position).getDistance()
                        val start = initialSpan ?: span.also { initialSpan = it }
                        if (!pinched && span < start * 0.75f) {
                            pinched = true
                            back?.invoke()
                        }
                    }
                    val first = event.changes.firstOrNull { it.id == down.id }
                    if (first != null && (first.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (pressed.isEmpty() && initialSpan == null && !moved && first?.isConsumed == false) tap(down.position)
                } while (pressed.isNotEmpty())
            }
        }) {
            val border = 1.25.dp.toPx() / camera.scale
            withTransform({
                translate(w / 2 - camera.cx * camera.scale, h / 2 - camera.cy * camera.scale)
                scale(camera.scale, camera.scale, Offset.Zero)
            }) {
                outlinePath?.let {
                    drawPath(it, KagomColor.Map.landFogged)
                    drawPath(it, KagomColor.Map.boundary, style = Stroke(border))
                }
                regions.asReversed().forEach { drawPath(paths.getValue(it.region.id), it.fill) }
                regions.forEach { drawPath(paths.getValue(it.region.id), KagomColor.Map.boundary, style = Stroke(border)) }
                district?.let {
                    val dashes = floatArrayOf(7.dp.toPx() / camera.scale, 4.dp.toPx() / camera.scale)
                    drawPath(paths.getValue(it.region.id), KagomColor.Text.primary,
                        style = Stroke(2.5.dp.toPx() / camera.scale, pathEffect = PathEffect.dashPathEffect(dashes)))
                }
            }
            labels.forEach { label ->
                val rect = label.rect
                val radius = CornerRadius(rect.height / 2)
                drawRoundRect(KagomColor.Surface.card, rect.topLeft, rect.size, radius)
                if (label.id == district?.region?.id) {
                    drawRoundRect(KagomColor.Text.primary, rect.topLeft, rect.size, radius, style = Stroke(1.dp.toPx()))
                }
                drawText(label.text, topLeft = rect.center - Offset(label.text.size.width / 2f, label.text.size.height / 2f))
            }
        }
        district?.landmarks.orEmpty().forEach { v ->
            val p = pinLocations.getValue(v.landmark.id)
            val name = v.landmark.name
            LandmarkPin(v.pin, Modifier.offset {
                IntOffset((p.x - 24.dp.toPx()).roundToInt(), (p.y - 24.dp.toPx()).roundToInt())
            }.semantics { contentDescription = name }) { onPin(v.landmark.id) }
            val x = with(density) { p.x.toDp() }
            val y = with(density) { p.y.toDp() }
            Text(
                name, Modifier.offset(x = (x + 28.dp).coerceIn(4.dp, maxOf(4.dp, maxWidth - 110.dp)),
                    y = (y - 12.dp).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(KagomRadius.full)).background(KagomColor.Surface.card)
                    .clickable(role = Role.Button) { onPin(v.landmark.id) }.padding(horizontal = 8.dp, vertical = 2.dp),
                style = KagomType.Caption14Strong, color = KagomColor.Text.primary, maxLines = 1,
            )
        }
        Column(Modifier.align(Alignment.TopEnd).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NorthArrowGlyph(Modifier.size(16.dp, 20.dp), KagomColor.Text.primary)
            Text("N", style = KagomType.Caption14Strong, color = KagomColor.Text.primary)
        }
        Text(
            if (showDistrictAttribution) "경계 © 통계청 SGIS · vuski/admdongkor" else "© OpenStreetMap contributors",
            Modifier.align(Alignment.BottomEnd).padding(horizontal = 10.dp, vertical = 6.dp),
            style = KagomType.Caption14, color = KagomColor.Text.secondary,
        )
        if (!split) Text(
            hint, Modifier.align(Alignment.BottomStart).padding(12.dp)
                .background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.full))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = KagomType.Caption14Strong, color = KagomColor.Text.inverse,
        )
        val reward = Items.byId(selectedPin?.landmark?.rewardItemId)
        if (selectedPin != null && reward != null) {
            val pinOnLowerHalf = pinLocations.getValue(selectedPin.landmark.id).y >= h / 2
            val detailPosition = if (pinOnLowerHalf) {
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            } else {
                Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
            }
            if (gearP) {
                LandmarkDetailPopup(
                    selectedPin.landmark.name, reward.name, reward.mark, selectedPin.visited,
                    modifier = detailPosition
                        .clickable(enabled = selectedPin.pin == PinState.Unlocked, onClickLabel = "보상 장착") {
                            onEquip(reward.id)
                            onDismissPin()
                        },
                    width = minOf(if (split) maxWidth - 16.dp else 440.dp, maxWidth),
                    itemId = reward.id,
                )
            } else {
                Text("기어 P에서 핀 상세를 볼 수 있어요",
                    detailPosition
                        .background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.full))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = KagomType.Caption14Strong, color = KagomColor.Text.inverse)
            }
        }
    }
}

@Composable
private fun RegionPanel(
    title: String,
    hint: String,
    regions: List<RegionProgress>,
    district: RegionProgress?,
    selectedPin: LandmarkView?,
    onRegion: (String) -> Unit,
    onPin: (String) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val others = remember(regions, district) {
        regions.filter { it.region.id != district?.region?.id }
            .sortedWith(compareByDescending<RegionProgress> { it.landmarks.isNotEmpty() }.thenBy { it.region.name })
    }
    LazyColumn(
        modifier.clip(shape).background(KagomColor.Surface.card).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(title, style = KagomType.Heading20, color = KagomColor.Text.primary)
            Text(hint, Modifier.padding(top = 6.dp, bottom = 6.dp), style = KagomType.Caption14, color = KagomColor.Text.secondary)
        }
        if (district != null && district.landmarks.isNotEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().background(KagomColor.Map.landPartial.copy(alpha = 0.5f), RoundedCornerShape(KagomRadius.r20))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(district.region.name, style = KagomType.Caption14Strong, color = KagomColor.Text.primary)
                        Text("${district.count} 해금", style = KagomType.Caption14Strong, color = KagomColor.Accent.positive)
                    }
                    district.landmarks.forEach { v -> LandmarkRow(v, v == selectedPin) { onPin(v.landmark.id) } }
                }
            }
        }
        if (district != null && others.isNotEmpty()) {
            item { Text("다른 구획", Modifier.padding(top = 6.dp), style = KagomType.Caption14Strong, color = KagomColor.Text.primary) }
        }
        items(others, key = { it.region.id }) { entry ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(KagomRadius.r16))
                    .clickable(role = Role.Button) { onRegion(entry.region.id) }.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(9.dp).background(entry.fill, CircleShape))
                Text(entry.region.name, Modifier.weight(1f), style = KagomType.Body16, color = KagomColor.Text.primary)
                Text(entry.count, style = KagomType.Caption14Strong,
                    color = if (entry.visited > 0) KagomColor.Accent.positive else KagomColor.Text.secondary)
            }
        }
    }
}

@Composable
private fun LandmarkRow(view: LandmarkView, selected: Boolean, onClick: () -> Unit) {
    val reward = Items.byId(view.landmark.rewardItemId)
    val shape = RoundedCornerShape(KagomRadius.r16)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(if (selected) KagomColor.Surface.card else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LandmarkPin(view.pin, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(view.landmark.name, style = KagomType.Body16Strong, color = KagomColor.Text.primary)
            val state = if (view.visited) "방문 완료" else "미방문"
            Text(listOfNotNull(state, reward?.name).joinToString(" · "), style = KagomType.Caption14, color = KagomColor.Text.secondary)
        }
    }
}

private fun String.shortRegionName() = when (this) {
    "충청북도" -> "충북"
    "충청남도" -> "충남"
    "경상북도" -> "경북"
    "경상남도" -> "경남"
    "전남광주통합특별시" -> "전남광주"
    else -> removeSuffix("특별자치도").removeSuffix("특별자치시").removeSuffix("특별시").removeSuffix("광역시")
}

fun landmarkViews(visited: Set<String>, unlockPending: Set<String>): List<LandmarkView> = Landmarks.all.map { lm ->
    LandmarkView(lm, when {
        lm.id !in visited -> PinState.Locked
        lm.id in unlockPending -> PinState.Unlocking
        else -> PinState.Unlocked
    })
}
