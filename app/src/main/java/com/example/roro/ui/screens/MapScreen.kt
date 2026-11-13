package com.example.roro.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.roro.R
import com.example.roro.core.GeoBounds
import com.example.roro.core.Items
import com.example.roro.core.KoreaMap
import com.example.roro.core.Landmark
import com.example.roro.core.Landmarks
import com.example.roro.core.MapCamera
import com.example.roro.core.MapProjection
import com.example.roro.core.MapRegion
import com.example.roro.core.MapFill
import com.example.roro.core.MapZoom
import com.example.roro.core.Severity
import com.example.roro.core.Visit
import com.example.roro.data.GeoPoint
import com.example.roro.data.KoreaMapAsset
import com.example.roro.ui.components.FLAG_ANCHOR_X
import com.example.roro.ui.components.FLAG_ANCHOR_Y
import com.example.roro.ui.components.LandmarkFlag
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.StatusBadge
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import com.example.roro.ui.theme.PleosKit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val landmarkDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

data class LandmarkView(val landmark: Landmark, val pin: PinState, val firstVisitDay: Long?) {
    val visited: Boolean get() = pin != PinState.Locked
    val firstVisitDate: String? get() = firstVisitDay?.let { LocalDate.ofEpochDay(it).format(landmarkDateFormat) }
}

/** 목록과 핀 이름표의 상태 글. 해금 중 = 방문은 했지만 보상 팝업(POP-03)이 정차를 기다리는 동안이라 보상은 아직 못 받았다 */
private val PinState.caption: String
    get() = when (this) {
        PinState.Locked -> "미방문"
        PinState.Unlocking -> "해금 중"
        PinState.Unlocked -> "방문 완료"
    }

private data class RegionProgress(
    val region: MapRegion,
    val landmarks: List<LandmarkView>,
) {
    val visited: Int = landmarks.count { it.visited }
    val count: String = String.format(Locale.KOREA, "%,d / %,d", visited, landmarks.size)
    /** 지역 전체를 방문 비율로 칠한다 (core MapFill) */
    val fill: MapFill = MapFill.of(visited, landmarks.size)
    val fullyVisited: Boolean get() = fill == MapFill.FULL
}

@Composable
fun MapScreen(
    views: List<LandmarkView>,
    gearP: Boolean,
    onEquip: (String) -> Unit,
    onNavigate: (Landmark) -> Unit,
    location: GeoPoint?,
    detailAvatar: DetailAvatar,
    focusLandmarkId: String? = null,
    onFocusShown: () -> Unit = {},
    avatar: @Composable (Modifier) -> Unit,
) {
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
    var pinId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Split: 지도 위로 끌어올리는 시트가 멈춘 자리 (Full에서는 쓰지 않는다) */
    var sheetStop by rememberSaveable { mutableStateOf(SheetStop.PEEK) }

    fun goNationwide() {
        pinId = null
        unitId = null
        provinceId = null
    }

    fun goProvince() {
        pinId = null
        unitId = null
    }

    /** 상세를 닫으면 Split 시트는 목록 높이로 내려간다 (보던 목록으로 돌아간다) */
    fun closeLandmarkDetail() {
        pinId = null
        if (sheetStop == SheetStop.DETAIL) sheetStop = SheetStop.HALF
    }

    fun goMapBack() {
        if (unitId != null) goProvince() else goNationwide()
    }

    // 뒤로 키: 상세 닫기 → (Split) 시트 접기 → 상위 지도
    BackHandler(enabled = provinceId != null || pinId != null || (split && sheetStop != SheetStop.PEEK)) {
        when {
            pinId != null -> closeLandmarkDetail()
            split && sheetStop != SheetStop.PEEK -> sheetStop = SheetStop.PEEK
            else -> goMapBack()
        }
    }
    // 단계를 옮기거나 주행을 시작하면 시트를 접어 지도와 내 위치가 보이게 한다
    LaunchedEffect(provinceId, unitId, gearP) { sheetStop = SheetStop.PEEK }

    val loaded = result?.getOrNull()
    LaunchedEffect(focusLandmarkId, loaded) {
        val lm = Landmarks.byId(focusLandmarkId) ?: return@LaunchedEffect
        val target = loaded?.unit(lm.unitId) ?: return@LaunchedEffect
        provinceId = target.provinceId
        unitId = target.id
        pinId = lm.id
        onFocusShown()
    }
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (result == null) "지도를 불러오는 중이에요" else "지도를 불러오지 못했어요.",
                style = KagomType.Body16, color = KagomColor.Text.secondary)
        }
        return
    }

    val mapLocation = location?.takeIf {
        it.lon in loaded.bounds.west.toDouble()..loaded.bounds.east.toDouble() &&
            it.lat in loaded.bounds.south.toDouble()..loaded.bounds.north.toDouble()
    }

    fun showMyLocation(point: GeoPoint) {
        val lon = point.lon.toFloat()
        val lat = point.lat.toFloat()
        val city = loaded.unitAt(lon, lat)
        provinceId = city?.provinceId ?: loaded.provinces.firstOrNull { it.contains(lon, lat) }?.id
        unitId = city?.id
        pinId = null
    }
    val province = loaded.province(provinceId)
    val unit = loaded.unit(unitId)
    val regions = remember(loaded, province, unit, views) { mapRegions(loaded, province, unit, views) }
    val districtListState = key(unit?.id) { rememberLazyListState() }
    val count = regions.flatMap { it.landmarks }
    val selectedPin = count.firstOrNull { it.landmark.id == pinId }
    val detail = selectedPin.takeIf { gearP }
    // 상세가 열리면(핀·목록에서 고름, 치장에서 넘어옴, 다시 정차함) Split 시트를 상세 높이로 올린다
    LaunchedEffect(detail?.landmark?.id) { if (detail != null) sheetStop = SheetStop.DETAIL }

    fun selectPin(id: String) {
        if (pinId == id) {
            closeLandmarkDetail()
        } else {
            pinId = id
        }
    }

    fun chooseRegion(id: String) {
        pinId = null
        when {
            // 시 단계는 시 한 덩어리다 (구획 없음): 지도를 누르면 고른 깃발만 닫는다
            unit != null -> Unit
            province != null -> unitId = id
            else -> provinceId = id
        }
    }

    val panelTitle = province?.let { "${it.name.shortRegionName()} 시·군별 해금 현황" } ?: "시·도별 해금 현황"
    val panelHint = when {
        !split -> "구획을 누르면 다음 단계로 들어가요 · 숫자는 해금 / 전체 랜드마크"
        province != null -> "시·군을 누르면 구획 지도로 들어가요"
        else -> "시·도를 누르면 그 지역으로 들어가요"
    }
    // [top]·[bottom] = 지도 위에 얹힌 머리줄·접힌 시트의 높이, [sheetRise] = 시트가 접힌 자리보다 올라온 높이 px (Split만)
    val mapContent: @Composable (Modifier, Dp, Dp, () -> Float) -> Unit = { modifier, top, bottom, sheetRise ->
        RegionMap(
            location = mapLocation,
            locationLabel = when {
                location == null -> "위치 확인 불가"
                mapLocation == null -> "지도 범위 밖"
                else -> "내 위치"
            },
            onLocate = mapLocation?.let { point -> { showMyLocation(point) } },
            avatar = avatar,
            sceneId = unit?.id ?: province?.id ?: "nationwide",
            bounds = unit?.bounds ?: province?.bounds ?: loaded.bounds,
            regions = regions,
            outline = unit?.takeIf { regions.isEmpty() },
            district = null,
            visiblePins = if (unit != null) count else emptyList(),
            selectedPin = selectedPin,
            split = split,
            // 강릉 읍면 구획(통계청 SGIS)은 더는 그리지 않는다 → 모든 단계가 OSM 경계다
            attributions = listOf("© OpenStreetMap contributors"),
            hint = when {
                // 주행 중에는 핀 상세가 막혀 있다. 핀을 누르면 뜨는 정차 안내와 같은 말로 맞춘다 (반대되는 안내가 나란히 보였다)
                unit != null && !gearP -> "기어 P에서 플래그 상세를 볼 수 있어요."
                // Figma SCR-05 layer=3 (149:4593)
                unit != null -> "플래그를 누르면 그날의 기록과 얻은 착장을 볼 수 있어요"
                province != null -> "시·군을 누르면 구획 지도로 들어가요."
                else -> "시·도를 누르면 그 지역으로 들어가요."
            },
            gearP = gearP,
            onRegion = ::chooseRegion,
            onPin = ::selectPin,
            onDismissPin = ::closeLandmarkDetail,
            onBack = if (province != null) ::goMapBack else null,
            insetTop = top,
            insetBottom = bottom,
            sheetRise = sheetRise,
            modifier = modifier,
        )
    }
    val panel: @Composable (Modifier) -> Unit = { modifier ->
        if (detail != null) {
            key(detail.landmark.id) {
                LandmarkDetail(detail, mapLocation, detailAvatar, split,
                    onBack = ::closeLandmarkDetail,
                    onNavigate = { onNavigate(detail.landmark) },
                    onEquip = { onEquip(detail.landmark.rewardItemId) },
                    modifier = modifier)
            }
        } else if (unit != null) {
            LandmarkOverviewPanel(unit.name, count, selectedPin?.landmark?.id, split, districtListState, ::selectPin, modifier)
        } else {
            RegionPanel(panelTitle, panelHint, regions, split, ::chooseRegion, modifier)
        }
    }

    val title = unit?.name ?: province?.name ?: "전국"
    // 시 단계는 Figma처럼 "방문 n / m", 위 단계는 "해금 n / m"
    val unlocked = String.format(Locale.KOREA, if (unit != null) "방문 %,d / %,d" else "해금 %,d / %,d", count.count { it.visited }, count.size)
    if (split) {
        // Split(세로 슬롯): 지도를 화면 전체에 깔고 머리줄과 목록·상세 시트를 그 위에 얹는다 (디자인에 없는 배치, 가정).
        // 지도 아래에 목록을 쌓으면 Pleos 세로 슬롯(높이 약 920)에서 지도가 화면의 40%쯤만 차지해 구획과 핀이 뭉쳤다
        BoxWithConstraints(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
            val stops = with(LocalDensity.current) {
                mapOf(
                    SheetStop.PEEK to SHEET_PEEK.toPx(),
                    SheetStop.HALF to maxOf(SHEET_PEEK, maxHeight * 0.48f).toPx(),
                    SheetStop.DETAIL to maxOf(SHEET_PEEK, maxHeight * 0.62f).toPx(),
                )
            }
            val sheetHeight = remember { Animatable(stops.getValue(SheetStop.PEEK)) }
            LaunchedEffect(sheetStop, stops) {
                val target = stops.getValue(sheetStop)
                // 주행 중에는 움직임 없이 바로 바꾼다
                if (gearP) sheetHeight.animateTo(target, tween(KagomMotion.SHEET, easing = FastOutSlowInEasing)) else sheetHeight.snapTo(target)
            }
            mapContent(Modifier.fillMaxSize(), SPLIT_HEADER_INSET, SHEET_PEEK) { sheetHeight.value - stops.getValue(SheetStop.PEEK) }
            SplitMapHeader(
                title, province, unit, unlocked, ::goMapBack, ::goNationwide, ::goProvince,
                Modifier.align(Alignment.TopCenter).padding(start = KagomSpace.s12, end = KagomSpace.s12, top = KagomSpace.s12),
            )
            MapSheet(
                height = sheetHeight,
                stops = stops,
                stop = sheetStop,
                onStop = { next ->
                    // 상세를 끌어내리면 상세를 닫는다 (시트에는 목록이 남는다)
                    if (next != SheetStop.DETAIL) pinId = null
                    sheetStop = next
                },
                // 상세는 제 머리줄(이름·닫기)이 있다
                title = when {
                    detail != null -> null
                    unit != null -> "${unit.name} 랜드마크"
                    else -> panelTitle
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { panel(it) }
        }
    } else {
        Column(
            Modifier.fillMaxSize().background(KagomColor.Surface.page)
                .padding(horizontal = KagomSpace.s36, vertical = KagomSpace.s28),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KagomSpace.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (province != null) {
                    Image(
                        painterResource(R.drawable.map_back),
                        contentDescription = "상위 지역 지도로 돌아가기",
                        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = ::goMapBack),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8), verticalAlignment = Alignment.CenterVertically) {
                    if (province != null) {
                        Breadcrumb("전국", KagomType.Heading20, ::goNationwide)
                        Image(painterResource(R.drawable.map_chevron), contentDescription = null)
                        if (unit != null) {
                            Breadcrumb(province.name, KagomType.Heading20, ::goProvince)
                            Image(painterResource(R.drawable.map_chevron), contentDescription = null)
                        }
                    }
                    Text(title, style = KagomType.Title28, color = KagomColor.Text.primary)
                }
                StatusBadge(Severity.OK, unlocked, style = KagomType.Body16Strong)
                Spacer(Modifier.weight(1f))
                MapLegend(cityLayer = unit != null)
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(KagomSpace.s16)) {
                mapContent(Modifier.weight(1f).fillMaxSize(), 0.dp, 0.dp) { 0f }
                panel(Modifier.width(if (unit != null) 480.dp else 400.dp).fillMaxSize())
            }
        }
    }
}

/** Split 지도 시트가 멈추는 자리: 접힘(제목 한 줄) · 목록 · 상세 */
private enum class SheetStop { PEEK, HALF, DETAIL }

/** 접힌 시트의 높이 = 손잡이 32 + 제목 줄 56 + 아래 여백 8. 지도는 이만큼을 뺀 자리에 맞춘다 */
private val SHEET_PEEK = 96.dp

/** Split 머리줄이 가리는 높이 = 위 여백 12 + 머리줄 72 + 아래 틈 8 */
private val SPLIT_HEADER_INSET = 92.dp

/**
 * Split 지도 위에 떠 있는 머리줄: 상위 지도로 가는 화살표 + 빵부스러기·지역 이름 + 해금 수.
 * 높이를 고정해 둔다 (지도 시점이 머리줄 높이에 맞춰져 있어, 단계마다 높이가 달라지면 지도를 다시 그린다)
 */
@Composable
private fun SplitMapHeader(
    title: String,
    province: MapRegion?,
    unit: MapRegion?,
    unlocked: String,
    onBack: () -> Unit,
    onNationwide: () -> Unit,
    onProvince: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(KagomRadius.r20)
    Row(
        modifier.fillMaxWidth().height(72.dp)
            .shadow(8.dp, shape, ambientColor = PleosKit.shadow, spotColor = PleosKit.shadow)
            .background(KagomColor.Surface.card, shape)
            // 머리줄을 누른 것이 아래 지도의 구획 선택으로 넘어가지 않게 한다
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = KagomSpace.s12),
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (province != null) {
            Image(
                painterResource(R.drawable.map_back_split),
                contentDescription = "상위 지역 지도로 돌아가기",
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            )
        }
        Column(Modifier.weight(1f).padding(start = if (province == null) KagomSpace.s8 else 0.dp)) {
            if (province != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Breadcrumb("전국", KagomType.Caption14, onNationwide)
                    if (unit != null) {
                        Text(" › ", style = KagomType.Caption14, color = KagomColor.Text.secondary)
                        Breadcrumb(province.name, KagomType.Caption14, onProvince)
                    }
                }
            }
            Text(title, style = KagomType.Heading22, color = KagomColor.Text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusBadge(Severity.OK, unlocked, style = KagomType.Body16Strong)
    }
}

/**
 * Split 지도의 끌어올리는 시트 (디자인에 없는 것, 가정). 손잡이·제목 줄을 끌거나 누르면 [stops] 사이를 오간다.
 * 목록은 시트 안에서 따로 스크롤된다 (지도 제스처와 섞이지 않게 시트는 손잡이·제목 줄에서만 끌린다).
 * [height] = 지금 높이 px. 끄는 동안 지도의 가림 보정이 같은 값을 읽는다. [title] = null이면 제목 줄이 없다 (상세)
 */
@Composable
private fun MapSheet(
    height: Animatable<Float, AnimationVector1D>,
    stops: Map<SheetStop, Float>,
    stop: SheetStop,
    onStop: (SheetStop) -> Unit,
    title: String?,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = KagomRadius.r24, topEnd = KagomRadius.r24)
    val expanded = stop != SheetStop.PEEK
    val toggle = { onStop(if (expanded) SheetStop.PEEK else SheetStop.HALF) }
    val drag = rememberDraggableState { delta ->
        scope.launch { height.snapTo((height.value - delta).coerceIn(stops.getValue(SheetStop.PEEK), stops.getValue(SheetStop.DETAIL))) }
    }
    val grip = Modifier
        .draggable(drag, Orientation.Vertical, onDragStopped = { velocity ->
            // 놓은 자리에서 가까운 멈춤 자리로 간다. 빠르게 튕기면 그 방향의 자리까지 (0.15초만큼 더 간 자리로 본다, 가정)
            val projected = height.value - velocity * 0.15f
            val next = stops.minBy { abs(it.value - projected) }.key
            if (next == stop) height.animateTo(stops.getValue(next), tween(KagomMotion.SHEET_OUT)) else onStop(next)
        })
        .clickable(role = Role.Button, onClickLabel = if (expanded) "목록 접기" else "목록 펼치기", onClick = toggle)
    Column(
        modifier.fillMaxWidth()
            .layout { measurable, constraints ->
                val px = height.value.roundToInt()
                val placeable = measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
                layout(placeable.width, px) { placeable.place(0, 0) }
            }
            .shadow(16.dp, shape, ambientColor = PleosKit.shadow, spotColor = PleosKit.shadow)
            .background(KagomColor.Surface.card, shape)
            // 시트의 빈 곳을 누른 것이 아래 지도로 넘어가지 않게 한다
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(Modifier.fillMaxWidth().then(grip)) {
            Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp, 5.dp).background(KagomColor.Border.default, CircleShape))
            }
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = KagomSpace.s20),
                    horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f), style = KagomType.Heading20, color = KagomColor.Text.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Image(painterResource(R.drawable.map_chevron), null, Modifier.size(20.dp).rotate(if (expanded) 90f else -90f))
                }
                // 접힌 시트에 목록의 첫 줄 머리가 비치지 않게 한다
                Spacer(Modifier.height(KagomSpace.s8))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) { content(Modifier.fillMaxSize()) }
    }
}

private fun mapRegions(
    map: KoreaMap, province: MapRegion?, unit: MapRegion?, views: List<LandmarkView>,
): List<RegionProgress> {
    fun progress(region: MapRegion, landmarks: List<LandmarkView>) = RegionProgress(region, landmarks)

    if (unit != null) {
        // 시 단계: 읍면 구획 없이 시 한 덩어리 (Figma SCR-05 layer=3). 랜드마크가 없으면 빈 목록 → 외곽선만 그린다
        val cityLandmarks = views.filter { it.landmark.unitId == unit.id }
        return if (cityLandmarks.isEmpty()) emptyList() else listOf(progress(unit, cityLandmarks))
    }
    if (province != null) {
        val byUnit = views.groupBy { it.landmark.unitId }
        return map.units.filter { it.provinceId == province.id }.map { progress(it, byUnit[it.id].orEmpty()) }
    }
    val byProvince = views.groupBy { map.unit(it.landmark.unitId)?.provinceId }
    return map.provinces.map { progress(it, byProvince[it.id].orEmpty()) }
}

@Composable
private fun Breadcrumb(label: String, style: TextStyle, onClick: () -> Unit) {
    Text(label, Modifier.clickable(role = Role.Button, onClick = onClick), style = style, color = KagomColor.Text.secondary,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 범례: 시 단계는 깃발 색(미방문·방문), 위 단계는 칠하기 색 (Figma SCR-05) */
@Composable
private fun MapLegend(cityLayer: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        (if (cityLayer) listOf(
            KagomColor.Map.poleUnvisited to "미방문",
            KagomColor.Map.flagVisited to "방문",
            KagomColor.Text.primary to "선택 중",
        ) else listOf(
            KagomColor.Map.landFogged to "미해금 · 안개",
            KagomColor.Map.landPartial to "일부 해금",
            KagomColor.Map.landUnlocked to "모두 해금",
            KagomColor.Text.primary to "선택 중",
        )).forEach { (color, label) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (label == "선택 중") {
                    Canvas(Modifier.size(14.dp)) {
                        val inset = 1.dp.toPx()
                        drawRoundRect(color, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
                    }
                } else if (cityLayer) {
                    Box(Modifier.size(12.dp).background(color, CircleShape))
                } else {
                    // 일부 해금은 반투명 초록이라 지도처럼 흰 바탕 위에 칠한다
                    Box(Modifier.size(14.dp).background(KagomColor.Surface.card, RoundedCornerShape(3.dp)).background(color, RoundedCornerShape(3.dp)))
                }
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

/** 깃발 이름표 한 장의 자리. 지역 이름표와 카곰이 피해 갈 수 있게 그리기 전에 정해 둔다 */
private class PinLabel(val view: LandmarkView, val rect: Rect)

/** 깃발([LandmarkFlag])이 차지하는 자리: 랜드마크 자리(깃대 밑)에서 위·오른쪽으로 */
private fun Density.flagRect(at: Offset) = Rect(
    at.x - FLAG_ANCHOR_X.toPx(), at.y - FLAG_ANCHOR_Y.toPx(),
    at.x - FLAG_ANCHOR_X.toPx() + 48.dp.toPx(), at.y - FLAG_ANCHOR_Y.toPx() + 56.dp.toPx(),
)

/** 지도 한 단계의 그림 (칸·경계·시점·점선 테두리를 두른 칸). 단계를 옮길 때 떠나온 단계를 겹쳐 그리려고 묶어 둔다 */
private class MapScene(
    val bounds: GeoBounds,
    val regions: List<RegionProgress>,
    val paths: Map<String, Path>,
    val outline: Path?,
    val camera: MapCamera,
    val selectedId: String?,
)

/** 두 시점 사이 ([t] 0..1). 화면에서 모든 점이 곧게 움직이도록 배율과 화면 위 이동량을 같이 보간한다 */
private fun MapCamera.towards(to: MapCamera, t: Float): MapCamera {
    val s = scale + (to.scale - scale) * t
    val ox = -cx * scale + (-to.cx * to.scale + cx * scale) * t
    val oy = -cy * scale + (-to.cy * to.scale + cy * scale) * t
    return MapCamera(-ox / s, -oy / s, s)
}

/**
 * 지도 한 단계의 그림. 자기 시점([MapScene.camera])으로 한 번만 그려 두고, 단계 이동 중에는 그 그림을 통째로 옮기고 키운다
 * ([shown] = 지금 화면의 시점). 프레임마다 해안선 수천 점을 새 배율로 다시 그리면 끊긴다 (에뮬레이터에서 프레임당 70~200 ms).
 */
@Composable
private fun SceneLayer(scene: MapScene, shown: () -> MapCamera, alpha: () -> Float) {
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        val now = shown()
        val k = now.scale / scene.camera.scale
        scaleX = k; scaleY = k
        translationX = (scene.camera.cx - now.cx) * now.scale
        translationY = (scene.camera.cy - now.cy) * now.scale
        this.alpha = alpha()
    }) {
        val camera = scene.camera
        val border = 1.25.dp.toPx() / camera.scale
        withTransform({
            translate(size.width / 2 - camera.cx * camera.scale, size.height / 2 - camera.cy * camera.scale)
            scale(camera.scale, camera.scale, Offset.Zero)
        }) {
            scene.outline?.let {
                drawPath(it, KagomColor.Map.landFogged)
                drawPath(it, KagomColor.Map.boundary, style = Stroke(border))
            }
            // 지역 전체를 방문 비율로: 0% 회색, 일부 = 흰 바탕 위 초록 50%, 모두 = 초록
            scene.regions.asReversed().forEach { entry ->
                val path = scene.paths.getValue(entry.region.id)
                when (entry.fill) {
                    MapFill.NONE -> drawPath(path, KagomColor.Map.landFogged)
                    MapFill.PARTIAL -> { drawPath(path, KagomColor.Surface.card); drawPath(path, KagomColor.Map.landPartial) }
                    MapFill.FULL -> drawPath(path, KagomColor.Map.landUnlocked)
                }
            }
            scene.regions.forEach { drawPath(scene.paths.getValue(it.region.id), KagomColor.Map.boundary, style = Stroke(border)) }
            scene.paths[scene.selectedId]?.let {
                val dashes = floatArrayOf(7.dp.toPx() / camera.scale, 4.dp.toPx() / camera.scale)
                drawPath(it, KagomColor.Text.primary, style = Stroke(2.5.dp.toPx() / camera.scale, pathEffect = PathEffect.dashPathEffect(dashes)))
            }
        }
    }
}

@Composable
private fun RegionMap(
    location: GeoPoint?,
    locationLabel: String,
    onLocate: (() -> Unit)?,
    avatar: @Composable (Modifier) -> Unit,
    sceneId: String,
    bounds: GeoBounds,
    regions: List<RegionProgress>,
    outline: MapRegion?,
    district: RegionProgress?,
    visiblePins: List<LandmarkView>,
    selectedPin: LandmarkView?,
    split: Boolean,
    attributions: List<String>,
    hint: String,
    gearP: Boolean,
    onRegion: (String) -> Unit,
    onPin: (String) -> Unit,
    onDismissPin: () -> Unit,
    onBack: (() -> Unit)?,
    /** 지도 위에 얹힌 것이 가리는 높이 (Split의 머리줄, 접힌 시트). 지도와 지도 위 글·버튼은 그 사이에 둔다 */
    insetTop: Dp = 0.dp,
    insetBottom: Dp = 0.dp,
    /** 시트가 접힌 자리보다 더 올라온 높이 px. 그리는 단계에서 읽는다 (시트를 끄는 동안 지도를 다시 짜지 않게) */
    sheetRise: () -> Float = { 0f },
    modifier: Modifier,
) {
    val paths = remember(regions) { regions.associate { it.region.id to it.region.outline() } }
    val outlinePath = remember(outline) { outline?.outline() }
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val labelStyle = KagomType.Caption14.copy(color = KagomColor.Text.primary)
    // Split은 지도가 화면 끝까지 닿는다
    val shape = RoundedCornerShape(if (split) 0.dp else KagomRadius.r24)
    val density = LocalDensity.current
    val topPx = with(density) { insetTop.toPx() }
    val bottomPx = with(density) { insetBottom.toPx() }
    // 시트가 올라와 고른 핀·구획을 가리면 지도 그림을 통째로 위로 민다 (다시 그리지 않는다).
    // 고른 곳이 시트 위에 남은 자리의 가운데에 오는 만큼만, 시트가 올라온 높이를 넘지 않게
    val focusY = remember { mutableFloatStateOf(Float.NaN) }
    fun lift(height: Float): Float {
        val rise = sheetRise()
        val focus = focusY.floatValue
        if (rise <= 0f || focus.isNaN()) return 0f
        return (focus - (topPx + (height - topPx - bottomPx - rise) / 2)).coerceIn(0f, rise)
    }
    BoxWithConstraints(modifier.graphicsLayer { translationY = -lift(size.height) }.clip(shape).background(KagomColor.Map.sea)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        if (w <= 0f || h <= 0f) return@BoxWithConstraints
        // 지도 위 글·버튼은 지도가 밀려도 제자리에 있고, 시트가 올라오면 시트 위에 얹혀 같이 올라간다
        val ride: Density.() -> IntOffset = { IntOffset(0, (lift(h) - sheetRise()).roundToInt()) }
        val pad = with(density) { minOf((if (split) KagomSpace.s12 else KagomSpace.s28).toPx(), minOf(w, h) / 5f) }
        val camera = remember(bounds, w, h, pad, topPx, bottomPx) { MapZoom.fit(bounds, w, h, pad, topPx, bottomPx) }
        // 단계 이동: 떠나온 단계의 그림에서 새 단계의 그림으로 시점이 옮겨 가며 바뀐다 (주행 중에는 바로 바꾼다).
        // 방문 기록만 바뀌거나 화면 크기가 바뀐 것(범위가 같음)은 단계 이동이 아니다
        val scene = remember(regions, paths, outlinePath, camera, district?.region?.id) {
            MapScene(bounds, regions, paths, outlinePath, camera, district?.region?.id)
        }
        val scenes = remember { arrayOfNulls<MapScene>(2) }     // [0] 지금 단계, [1] 떠나온 단계
        scenes[0].let { shown ->
            if (shown != null && shown.bounds != scene.bounds) scenes[1] = shown.takeIf { gearP }
            scenes[0] = scene
        }
        val from = scenes[1]
        val move = remember(scene.bounds) { Animatable(if (from != null) 0f else 1f) }
        LaunchedEffect(move) {
            // 두 단계의 그림이 처음 그려지는 프레임이 지난 뒤에 움직이기 시작한다 (그 프레임이 길어서 앞부분을 건너뛰지 않게)
            if (move.value < 1f) repeat(2) { withFrameNanos { } }
            move.animateTo(1f, tween(KagomMotion.MAP, easing = FastOutSlowInEasing))
            scenes[1] = null
        }
        val moving by remember(move) { derivedStateOf { move.value < 1f } }
        fun point(lon: Float, lat: Float) = Offset(
            camera.toScreenX(MapProjection.x(lon), w),
            camera.toScreenY(MapProjection.y(lat), h),
        )
        val pinLocations = visiblePins.associate { v ->
            v.landmark.id to point(v.landmark.lon.toFloat(), v.landmark.lat.toFloat())
        }
        val pinLabels = remember(visiblePins, camera, w, h, measurer, split) {
            with(density) {
                visiblePins.map { v ->
                    val p = pinLocations.getValue(v.landmark.id)
                    // Figma SCR-05 layer=3: 깃발 오른쪽에 이름만 (상태·보상은 상세에서)
                    val name = measurer.measure(v.landmark.name, KagomType.Caption14Strong).size
                    val width = minOf(360.dp.toPx(), w, name.width + 2 * KagomSpace.s10.toPx())
                    val height = name.height + 2 * KagomSpace.s4.toPx()
                    // 깃발 천의 가운데(깃대 밑에서 위로 약 38) 높이에 맞춘다. 오른쪽에 자리가 없으면 깃발 아래
                    val right = p.x + 34.dp.toPx()
                    val beside = right + width <= w
                    PinLabel(v, Rect(
                        Offset(
                            (if (beside) right else p.x - width / 2).coerceIn(0f, w - width),
                            (if (beside) p.y - 38.dp.toPx() - height / 2 else p.y + 6.dp.toPx()).coerceAtLeast(0f),
                        ),
                        Size(width, height),
                    ))
                }
            }
        }
        val credits = listOfNotNull(locationLabel.takeIf { onLocate == null }) + attributions
        val north = if (split) null else painterResource(R.drawable.map_north)
        // 지도 위에 얹는 글·버튼의 자리 (아래에서 그리는 것과 같은 여백). 구획 이름표와 카곰이 여기를 피해야 서로 겹쳐서 못 읽는 일이 없다
        val overlays = remember(credits, locationLabel, hint, north, w, h, measurer, split, topPx, bottomPx) {
            with(density) {
                fun sizeOf(text: String, style: TextStyle) = measurer.measure(text, style).size
                buildList {
                    val lines = credits.map { sizeOf(it, KagomType.Caption14) }
                    val credit = Rect(
                        w - lines.maxOf { it.width } - 2 * KagomSpace.s10.toPx(),
                        h - bottomPx - lines.sumOf { it.height } - 2 * 6.dp.toPx(), w, h - bottomPx,
                    )
                    add(credit)
                    if (onLocate != null) {
                        val text = sizeOf(locationLabel, KagomType.Body16Strong)
                        val edge = KagomSpace.s12.toPx()
                        val width = text.width + 2 * KagomSpace.s16.toPx()
                        val height = maxOf(48.dp.toPx(), text.height.toFloat())
                        // Split: 왼쪽 위는 머리줄이 차지하고 핀·이름표가 몰린다 → 오른쪽 아래, 출처 글 위에 둔다
                        add(if (split) Rect(w - edge - width, credit.top - height, w - edge, credit.top)
                            else Rect(edge, edge, edge + width, edge + height))
                    }
                    // 머리줄과 접힌 시트 밑으로는 이름표·카곰을 두지 않는다
                    if (topPx > 0f) add(Rect(0f, 0f, w, topPx))
                    if (bottomPx > 0f) add(Rect(0f, h - bottomPx, w, h))
                    if (north != null) {
                        val letter = sizeOf("N", KagomType.Caption14Strong)
                        val edge = KagomSpace.s20.toPx()
                        add(Rect(
                            w - edge - maxOf(north.intrinsicSize.width, letter.width.toFloat()), edge,
                            w - edge, edge + north.intrinsicSize.height + letter.height,
                        ))
                        val text = sizeOf(hint, KagomType.Caption14)
                        val corner = KagomSpace.s24.toPx()
                        add(Rect(
                            corner, h - corner - text.height - 2 * KagomSpace.s10.toPx(),
                            corner + text.width + 2 * KagomSpace.s16.toPx(), h - corner,
                        ))
                    }
                }
            }
        }
        val labels = remember(regions, district, camera, w, h, measurer, labelStyle, split, sceneId, overlays, pinLabels) {
            val gap = with(density) { KagomSpace.s4.toPx() }
            val inset = with(density) { KagomSpace.s10.toPx() }
            val placed = mutableListOf<RegionLabel>()
            // 시 단계(깃발이 있는 단계)는 Figma처럼 지역 이름표 없이 깃발 이름표만 둔다
            if (visiblePins.isNotEmpty()) return@remember placed
            val keepClear = overlays
            val ordered = regions.sortedWith(
                compareByDescending<RegionProgress> { it.region.id == district?.region?.id }
                    .thenByDescending { it.landmarks.isNotEmpty() }
                    .thenByDescending { (it.region.bounds.east - it.region.bounds.west) * (it.region.bounds.north - it.region.bounds.south) },
            )
            for (entry in ordered) {
                val region = entry.region
                val p = point(region.labelLon, region.labelLat)
                val caption = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(region.name.shortRegionName()) }
                    append("  ")
                    withStyle(SpanStyle(color = if (entry.visited > 0) KagomColor.Accent.positive else KagomColor.Text.secondary)) {
                        if (entry.fullyVisited) append("✓ ")
                        append(entry.count.replace(" ", ""))
                    }
                }
                val text = measurer.measure(caption, labelStyle)
                val size = Size(text.size.width + inset * 2, text.size.height + gap * 2)
                val origin = p - Offset(size.width / 2, size.height / 2)
                val offsets = listOf(Offset.Zero, Offset(0f, -size.height), Offset(-size.width / 2, 0f),
                    Offset(size.width / 2, 0f), Offset(0f, size.height))
                val rect = offsets.asSequence().map { Rect(origin + it, size) }.firstOrNull { candidate ->
                    candidate.left >= 0 && candidate.top >= 0 && candidate.right <= w && candidate.bottom <= h &&
                        placed.none { it.rect.overlaps(candidate) } && keepClear.none { it.overlaps(candidate) }
                } ?: continue
                placed += RegionLabel(region.id, text, rect)
            }
            placed
        }
        val focus = selectedPin?.let { pinLocations[it.landmark.id]?.y }
            ?: labels.firstOrNull { it.id == district?.region?.id }?.rect?.center?.y
        SideEffect { focusY.floatValue = focus ?: Float.NaN }
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
        // 떠나온 단계는 뒤쪽 절반 동안 옅어지고, 새 단계는 앞쪽 절반 동안 짙어진다. 이름표는 자리 잡을 무렵에 나타난다
        val shown = { if (from != null && move.value < 1f) from.camera.towards(scene.camera, move.value) else scene.camera }
        // key = 단계: 보이던 단계가 '떠나온 단계'가 돼도 같은 그림을 그대로 쓴다 (다시 그리지 않는다)
        listOfNotNull(from.takeIf { moving }, scene).forEach { layer ->
            key(layer.bounds) {
                SceneLayer(layer, shown) {
                    when {
                        from == null -> 1f
                        // 새 단계는 처음부터 아주 옅게라도 그려 둔다 (알파 0이면 그리기를 미뤄서 움직이는 도중에 끊긴다)
                        layer === scene -> (move.value / 0.55f).coerceIn(0.02f, 1f)
                        else -> 1f - ((move.value - 0.4f) / 0.5f).coerceIn(0f, 1f)
                    }
                }
            }
        }
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
            val labelAlpha = if (from != null) ((move.value - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
            if (labelAlpha > 0f) labels.forEach { label ->
                val rect = label.rect
                val radius = CornerRadius(rect.height / 2)
                drawRoundRect(KagomColor.Surface.card, rect.topLeft, rect.size, radius, alpha = labelAlpha)
                if (label.id == district?.region?.id) {
                    drawRoundRect(KagomColor.Text.primary, rect.topLeft, rect.size, radius, alpha = labelAlpha, style = Stroke(1.dp.toPx()))
                }
                drawText(label.text, topLeft = rect.center - Offset(label.text.size.width / 2f, label.text.size.height / 2f), alpha = labelAlpha)
            }
        }
        if (!moving) pinLabels.forEach { label ->
            val v = label.view
            val p = pinLocations.getValue(v.landmark.id)
            val name = v.landmark.name
            val labelShape = RoundedCornerShape(KagomRadius.r12)
            LandmarkFlag(v.pin, v == selectedPin, Modifier.offset {
                IntOffset((p.x - FLAG_ANCHOR_X.toPx()).roundToInt(), (p.y - FLAG_ANCHOR_Y.toPx()).roundToInt())
            }.semantics { contentDescription = name + ", " + v.pin.caption }) { onPin(v.landmark.id) }
            Column(
                Modifier.offset { IntOffset(label.rect.left.roundToInt(), label.rect.top.roundToInt()) }
                    .widthIn(max = with(density) { label.rect.width.toDp() })
                    .clip(labelShape).background(KagomColor.Surface.card)
                    // 고른 깃발의 이름표: 방문 = 초록 테두리, 미방문 = 검은 테두리 (Figma 149:4593 / 351:4417)
                    .then(if (v == selectedPin) Modifier.border(2.dp, if (v.visited) KagomColor.Map.flagVisited else KagomColor.Text.primary, labelShape) else Modifier)
                    .clickable(role = Role.Button) { onPin(v.landmark.id) }
                    .padding(horizontal = KagomSpace.s10, vertical = KagomSpace.s4),
            ) {
                Text(v.landmark.name, style = KagomType.Caption14Strong, color = KagomColor.Text.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        location?.let { here ->
            fun projectedLocation(): Offset {
                val currentCamera = shown()
                return Offset(
                    currentCamera.toScreenX(MapProjection.x(here.lon.toFloat()), w),
                    currentCamera.toScreenY(MapProjection.y(here.lat.toFloat()), h),
                )
            }
            val diameter = with(density) { 68.dp.toPx() }
            // 카곰은 위치점 둘레에서 이름표·핀·지도 위 글을 가리지 않는 첫 자리에 둔다 (위 → 옆 → 대각선 → 아래, 가까운 쪽부터).
            // 다 막혀 있으면 가장 덜 가리는 자리. 단계 이동 중에 자리가 바뀌지 않게 도착한 시점 기준으로 정한다
            val lastShift = remember(camera) { arrayOfNulls<Offset>(1) }
            val avatarShift = remember(here, camera, w, h, labels, pinLabels, overlays) {
                val at = point(here.lon.toFloat(), here.lat.toFloat())
                val pinRadius = with(density) { 24.dp.toPx() }
                val blocked = labels.map { it.rect } + pinLabels.map { it.rect } +
                    pinLocations.values.map { with(density) { flagRect(it) } } + overlays
                val near = diameter / 2 + with(density) { KagomSpace.s4.toPx() }
                val directions = listOf(
                    Offset(0f, -1f), Offset(1f, 0f), Offset(-1f, 0f), Offset(0.7f, -0.7f), Offset(-0.7f, -0.7f),
                    Offset(0.7f, 0.7f), Offset(-0.7f, 0.7f), Offset(0f, 1f),
                )
                // 둘째 거리는 위치점에 핀이 있을 때 그 핀 바깥이다 (차가 랜드마크에 도착한 경우). 셋째는 이름표가 몰린 곳에서 한 칸 더
                val spots = listOf(near, near + pinRadius, near + 2 * pinRadius).flatMap { reach -> directions.map { it * reach } }
                fun covered(shift: Offset): Float {
                    val box = Rect(at + shift, diameter / 2)
                    if (box.left < 0f || box.top < topPx || box.right > w || box.bottom > h - bottomPx) return Float.MAX_VALUE
                    return blocked.filter { it.overlaps(box) }.map { it.intersect(box) }
                        .sumOf { (it.width * it.height).toDouble() }.toFloat()
                }
                val best = spots.minBy(::covered)
                // 차가 움직이는 동안 카곰이 이리저리 튀지 않게, 쓰던 자리가 새로 고른 자리만큼 비어 있으면 그대로 둔다
                (lastShift[0]?.takeIf { covered(it) <= covered(best) } ?: best).also { lastShift[0] = it }
            }
            Box(
                Modifier.offset {
                    val projected = projectedLocation()
                    if (projected.x !in 0f..w || projected.y !in 0f..h) {
                        IntOffset(-diameter.roundToInt(), -diameter.roundToInt())
                    } else {
                        IntOffset(
                            (projected.x + avatarShift.x - diameter / 2).coerceIn(0f, (w - diameter).coerceAtLeast(0f)).roundToInt(),
                            (projected.y + avatarShift.y - diameter / 2)
                                .coerceIn(topPx, (h - bottomPx - diameter).coerceAtLeast(topPx)).roundToInt(),
                        )
                    }
                }.clearAndSetSemantics { contentDescription = "내 위치의 카곰" }
                    .size(68.dp).background(KagomColor.Surface.card, CircleShape)
                    .border(2.dp, KagomColor.Text.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                avatar(Modifier.size(56.dp))
            }
            Box(
                Modifier.offset {
                    val projected = projectedLocation()
                    val radius = KagomSpace.s4.toPx()
                    IntOffset((projected.x - radius).roundToInt(), (projected.y - radius).roundToInt())
                }.size(KagomSpace.s8).background(KagomColor.Text.accent, CircleShape),
            )
        }
        val creditHeight = with(density) { overlays.first().height.toDp() }
        if (onLocate != null) {
            Box(
                // Split: 접힌 시트 바로 위. 시트가 올라오면 따라 올라가지 않고 그 뒤에 가려진다 (좁아진 지도에서 고른 핀을 덮지 않게)
                (if (split) Modifier.align(Alignment.BottomEnd).offset { IntOffset(0, lift(h).roundToInt()) }
                    .padding(end = KagomSpace.s12, bottom = insetBottom + creditHeight)
                    else Modifier.align(Alignment.TopStart).padding(KagomSpace.s12))
                    .heightIn(min = 48.dp)
                    .clip(CircleShape).background(KagomColor.Surface.card)
                    .clickable(role = Role.Button, onClick = onLocate).padding(horizontal = KagomSpace.s16),
                contentAlignment = Alignment.Center,
            ) {
                Text(locationLabel, style = KagomType.Body16Strong, color = KagomColor.Text.accent)
            }
        }
        if (north != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(KagomSpace.s20),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(north, contentDescription = null)
                Text("N", style = KagomType.Caption14Strong, color = KagomColor.Text.primary)
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).offset(ride).padding(bottom = insetBottom)
                // Split: 지도가 화면 끝까지 차서 출처 글이 땅·이름표 위에 겹친다 → 바다색 바탕을 깐다
                .then(if (split) Modifier.background(KagomColor.Map.sea.copy(alpha = 0.85f), RoundedCornerShape(topStart = KagomRadius.r12)) else Modifier)
                .padding(horizontal = KagomSpace.s10, vertical = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            credits.forEach { Text(it, style = KagomType.Caption14, color = KagomColor.Text.secondary) }
        }
        if (!split) Text(
            hint, Modifier.align(Alignment.BottomStart).padding(KagomSpace.s24)
                .background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.full))
                .padding(horizontal = KagomSpace.s16, vertical = KagomSpace.s10),
            style = KagomType.Caption14, color = KagomColor.Text.inverse,
        )
        if (split && !gearP && !moving && selectedPin != null) {
            // 고른 핀을 가리지 않게 핀의 반대쪽에 띄운다: 머리줄 아래 또는 출처 글 위
            val pinOnLowerHalf = pinLocations[selectedPin.landmark.id]?.y?.let { it >= (topPx + h - bottomPx) / 2 } ?: false
            val position = if (pinOnLowerHalf) Modifier.align(Alignment.TopCenter).offset { IntOffset(0, lift(h).roundToInt()) }.padding(top = insetTop + 8.dp)
                else Modifier.align(Alignment.BottomCenter).offset(ride).padding(bottom = insetBottom + creditHeight + KagomSpace.s4)
            Text("기어 P에서 플래그 상세를 볼 수 있어요",
                position.background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.full))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = KagomType.Caption14Strong, color = KagomColor.Text.inverse,
            )
        }
    }
}

@Composable
private fun RegionPanel(
    title: String,
    hint: String,
    regions: List<RegionProgress>,
    split: Boolean,
    onRegion: (String) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val sorted = remember(regions) {
        regions.sortedWith(compareByDescending<RegionProgress> { it.visited }.thenByDescending { it.landmarks.size }.thenBy { it.region.name })
    }
    LazyColumn(
        // Split: 시트 안에 들어간다. 카드 바탕과 제목은 시트가 그린다
        if (split) modifier.padding(horizontal = KagomSpace.s20) else modifier.clip(shape).background(KagomColor.Surface.card).padding(KagomSpace.s24),
        contentPadding = PaddingValues(bottom = if (split) KagomSpace.s16 else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (split) 2.dp else KagomSpace.s4),
    ) {
        if (!split) item {
            Text(title, style = KagomType.Heading20, color = KagomColor.Text.primary)
        }
        item { Text(hint, style = KagomType.Caption14, color = KagomColor.Text.secondary) }
        item { Spacer(Modifier.height(if (split) 6.dp else KagomSpace.s8)) }
        items(sorted, key = { it.region.id }) { entry ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(KagomRadius.r16))
                    .clickable(role = Role.Button) { onRegion(entry.region.id) },
                horizontalArrangement = Arrangement.spacedBy(KagomSpace.s10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val started = entry.visited > 0
                Box(Modifier.size(KagomSpace.s10).background(if (started) KagomColor.Map.landUnlocked else KagomColor.Map.landLocked, CircleShape))
                Text(entry.region.name, Modifier.weight(1f),
                    style = if (started) KagomType.Body16Strong else KagomType.Body16,
                    color = if (started) KagomColor.Text.primary else KagomColor.Text.secondary)
                Text(entry.count, style = KagomType.Body16Strong,
                    color = if (started) KagomColor.Accent.positive else KagomColor.Text.tertiary)
            }
        }
    }
}

/** 시 단계 오른쪽(Split은 시트) 목록: 지도 위에서 아래 순서의 랜드마크. 읍면 구획 묶음은 없앴다 (시 단계에 구획이 없다) */
@Composable
private fun LandmarkOverviewPanel(
    cityName: String,
    landmarks: List<LandmarkView>,
    pinId: String?,
    split: Boolean,
    listState: LazyListState,
    onPin: (String) -> Unit,
    modifier: Modifier,
) {
    // 지도 위에서 아래 순으로 고정해서, 방문 상태와 선택이 바뀌어도 같은 자리에서 찾을 수 있게 한다
    val ordered = remember(landmarks) { landmarks.sortedByDescending { it.landmark.lat } }
    val shape = RoundedCornerShape(KagomRadius.r24)
    LazyColumn(
        // Split: 시트 안에 들어간다. 카드 바탕과 제목은 시트가 그린다
        if (split) modifier.padding(horizontal = KagomSpace.s20) else modifier.clip(shape).background(KagomColor.Surface.card).padding(KagomSpace.s24),
        state = listState,
        contentPadding = PaddingValues(bottom = if (split) KagomSpace.s16 else 0.dp),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s12),
    ) {
        if (!split) item {
            Text("$cityName 랜드마크", style = KagomType.Heading22, color = KagomColor.Text.primary)
        }
        if (ordered.isEmpty()) item {
            Text("등록된 랜드마크가 없어요.", style = KagomType.Body16, color = KagomColor.Text.secondary)
        }
        items(ordered, key = { it.landmark.id }) { view ->
            LandmarkRow(view, view.landmark.id == pinId) { onPin(view.landmark.id) }
        }
    }
}

@Composable
private fun LandmarkRow(view: LandmarkView, highlighted: Boolean, onClick: () -> Unit) {
    val reward = Items.byId(view.landmark.rewardItemId)
    val shape = RoundedCornerShape(KagomRadius.r12)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(KagomColor.Surface.card)
            .border(1.dp, if (highlighted) KagomColor.Text.accent else KagomColor.Border.default, shape)
            .clickable(role = Role.Button, onClickLabel = "랜드마크 상세 보기", onClick = onClick)
            .padding(horizontal = KagomSpace.s12, vertical = KagomSpace.s10),
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = Modifier.size(36.dp)
        if (view.pin == PinState.Unlocking) {
            // Figma 목록 핀에는 해금 중이 없어서 지도 핀(landmarkPin State=Unlocking)과 같은 색·점으로 그린다
            Box(icon.background(KagomColor.Progress.fill, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize(0.625f).border(1.5.dp, KagomColor.Text.inverse, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize(0.32f).background(KagomColor.Text.inverse, CircleShape))
                }
            }
        } else {
            Image(
                painterResource(if (view.visited) R.drawable.map_panel_pin_visited else R.drawable.map_panel_pin_locked),
                contentDescription = null,
                icon,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KagomSpace.s4)) {
            Text(view.landmark.name, style = KagomType.Body18Strong, color = KagomColor.Text.primary)
            val status = when (view.pin) {
                PinState.Unlocked -> listOfNotNull(view.pin.caption, view.firstVisitDate?.let { "첫 방문 $it" }).joinToString(" · ")
                PinState.Unlocking -> "해금 중 · 정차하면 보상 지급"
                PinState.Locked -> view.pin.caption
            }
            Text(status, style = KagomType.Caption14Strong,
                color = if (view.pin == PinState.Unlocked) KagomColor.Accent.positive else KagomColor.Text.secondary)
            if (reward != null) {
                Text("보상 · ${reward.name}", style = KagomType.Caption14, color = KagomColor.Text.secondary)
            }
        }
        Image(painterResource(R.drawable.map_chevron), null, Modifier.size(20.dp))
    }
}

private fun String.shortRegionName() = when (this) {
    "충청북도" -> "충북"
    "충청남도" -> "충남"
    "경상북도" -> "경북"
    "경상남도" -> "경남"
    "경기도" -> "경기"
    "전남광주통합특별시" -> "전남광주"
    else -> removeSuffix("특별자치도").removeSuffix("특별자치시").removeSuffix("특별시").removeSuffix("광역시")
}

fun landmarkViews(visits: List<Visit>, unlockPending: Set<String>): List<LandmarkView> {
    val firstVisitDays = visits.groupBy { it.landmarkId }.mapValues { (_, records) -> records.minOf { it.day } }
    return Landmarks.all.map { lm ->
        val day = firstVisitDays[lm.id]
        val state = when {
            day == null -> PinState.Locked
            lm.id in unlockPending -> PinState.Unlocking
            else -> PinState.Unlocked
        }
        LandmarkView(lm, state, day)
    }
}
