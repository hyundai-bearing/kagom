
package com.example.roro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.roro.core.Category
import com.example.roro.core.Examples
import com.example.roro.core.Expression
import com.example.roro.core.Item
import com.example.roro.core.Items
import com.example.roro.core.Landmarks
import com.example.roro.core.Progress
import com.example.roro.core.ProgressContext
import com.example.roro.core.Quests
import com.example.roro.core.Source
import com.example.roro.data.AppData
import com.example.roro.data.Equipped
import com.example.roro.data.Profile
import com.example.roro.ui.components.CellState
import com.example.roro.ui.components.AssetImage
import com.example.roro.ui.components.Chip
import com.example.roro.ui.components.KagomRenders
import com.example.roro.ui.components.GhostSegmentedTabs
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.IviToast
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.NavTab
import com.example.roro.ui.components.Pagination
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.josa
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.ClosetPalette
import com.example.roro.ui.theme.KagomClosetPalette
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode
import kotlinx.coroutines.delay

/** 잠긴 아이템 획득 안내에 필요한 값 */
private data class UnlockInfo(
    val progressValue: String, val ratio: Float, val hint: String, val cta: String, val target: NavTab, val toastFull: String, val toastSplit: String,
    /** 랜드마크 아이템: 지도에서 바로 보여 줄 랜드마크 id */
    val landmarkId: String? = null,
)

private fun unlockInfo(item: Item, ctx: ProgressContext, completed: Set<String>): UnlockInfo {
    if (Examples.isExample(item.id)) {
        val landmark = item.source == Source.LANDMARK
        return UnlockInfo(
            "예시 데이터", 0f, "시연용 예시라 진행 상황이 없어요. 예시 데이터는 시연 패널에서 끌 수 있어요.",
            if (landmark) "지도에서 보기" else "도전과제 보기", if (landmark) NavTab.MAP else NavTab.QUESTS,
            "${item.name}: 예시 데이터라 실제 획득처가 없어요", "예시 데이터예요",
        )
    }
    val lm = item.sourceId?.let { Landmarks.byId(it) }
    if (lm != null) return UnlockInfo(
        "방문 전 · 0 / 1", 0f, "지도 탭에서 핀을 누르면 위치를 볼 수 있어요. 기어 P에서만 열려요.", "지도에서 보기", NavTab.MAP,
        "${lm.name}에 방문하면 ${item.name.josa("을", "를")} 받을 수 있어요", "${lm.name}에 방문하면 받을 수 있어요",
        landmarkId = lm.id,
    )
    val q = Quests.byId(item.sourceId)
    val p = q?.let { Progress.questProgress(it, ctx, completed) }
    val maintenance = item.source == Source.MAINTENANCE
    return UnlockInfo(
        p?.countText ?: "", p?.ratio ?: 0f,
        if (maintenance) "홈의 빠른 정비에서 정비 시트를 열 수 있어요. 기어 P에서만 열려요." else "도전과제 탭에서 진행 상황을 볼 수 있어요.",
        "도전과제 보기", NavTab.QUESTS,
        "${q?.title?.josa("을", "를") ?: "도전과제를"} 달성하면 ${item.name.josa("을", "를")} 받을 수 있어요",
        "${q?.title?.josa("을", "를") ?: "도전과제를"} 달성하면 받을 수 있어요",
    )
}

/**
 * SCR-06 치장 옷장 (tab=head|top|bottom|title, lockedItem=selected|sheet, grid=overflow).
 * 라벤더 스튜디오 (Figma 2027:10015): Full 3×3, Split 3칸.
 * [examples] = 시연 패널의 '예시 데이터' (Figma 예시 아이템 03~12, 페이지 넘김 확인용)
 */
@Composable
fun ClosetScreen(
    profile: Profile,
    data: AppData,
    ctx: ProgressContext,
    gearP: Boolean,
    expression: Expression,
    onSave: (Equipped) -> Unit,
    onSaveOutfit: (Int, Equipped) -> Unit,
    examples: Boolean = false,
    /** CTA: 탭 이동. 랜드마크 아이템이면 두 번째 값 = 지도에서 바로 보여 줄 랜드마크 id */
    onNavigate: (NavTab, String?) -> Unit,
) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val closetPalette = KagomClosetPalette.of(profile.skin)
    var draft by remember(data.equipped) { mutableStateOf(data.equipped) }
    var selectedSlot by remember { mutableStateOf(data.savedOutfits.indexOf(data.equipped).takeIf { it >= 0 }) }
    var tab by remember { mutableIntStateOf(0) }
    /** 마지막으로 누른 아이템. 카드 테두리와 정보 줄에 보이고, 정보 줄을 누르면 팝업([popupOpen])으로 자세히 본다 */
    var selected by remember { mutableStateOf<Item?>(null) }
    var popupOpen by remember { mutableStateOf(false) }
    val splitScroll = rememberScrollState()
    var page by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    LaunchedEffect(toast) { toast?.let { lastToast = it; delay(3_000); toast = null } }
    fun itemsOf(category: Category) = Items.of(category) + if (examples) Examples.of(category) else emptyList()
    val items = itemsOf(Category.entries[tab])

    val pageSize = if (split) 3 else 9
    val cols = 3
    val pageCount = maxOf(1, (items.size + pageSize - 1) / pageSize)
    val shownPage = page.coerceIn(0, pageCount - 1)

    /** 선택을 바꾸거나 지워도 선택한 아이템이 있는 페이지를 유지한다. 선택을 지우면 팝업도 닫는다 */
    fun select(sel: Item?) {
        val anchor = sel?.let { s -> items.indexOfFirst { it.id == s.id } }?.takeIf { it >= 0 } ?: (shownPage * pageSize)
        selected = sel
        if (sel == null) popupOpen = false
        page = anchor / pageSize
    }
    // 뒤로 키: 팝업 → 선택(정보 줄) 순서로 닫는다
    BackHandler(enabled = selected != null) { if (popupOpen) popupOpen = false else select(null) }
    LaunchedEffect(gearP) { if (!gearP) select(null) }

    fun owns(item: Item) = item.id in data.owned || (Examples.isExample(item.id) && item.id in Examples.owned)

    fun stateOf(item: Item) = when {
        !owns(item) -> CellState.Locked
        draft[item.category] == item.id -> CellState.Equipped
        else -> CellState.Owned
    }

    /**
     * 누를 때마다 팝업이 화면을 덮어 [닫기]를 한 번 더 눌러야 했다 (옷을 갈아입어 볼수록 피로하다).
     * 보유 아이템은 바로 입혀 보고, 잠긴 아이템은 얻는 방법을 그리드 아래 정보 줄에 보인다. 팝업은 정보 줄을 눌러야 열린다
     */
    fun onCell(item: Item) {
        if (!gearP) { toast = "주차 중에 이용할 수 있어요"; return }
        val s = stateOf(item)
        if (s != CellState.Locked && !Examples.isExample(item.id)) {
            draft = when {
                s == CellState.Owned -> draft.with(item.category, item.id)
                item.category == Category.TITLE -> draft
                else -> draft.with(item.category, null)
            }
        }
        popupOpen = false
        select(item)
    }

    // 시착: 저장 전 draft를 입혀 본다 (스킨 + 하의 + 상의 + 머리). 밀면 돌려 볼 수 있다.
    // 가이드 v1.2: 프리뷰 아래 장착 아이템 칩 줄은 없앴다
    val preview: @Composable (Modifier, Boolean) -> Unit = { m, compact ->
        Box(m.clip(RoundedCornerShape(KagomRadius.r28)).background(closetPalette.studio), contentAlignment = Alignment.Center) {
            // 원 양쪽의 여백이 같도록 슬롯 오른쪽 끝의 절반만큼 중심을 옮긴다.
            val shift = if (compact) 52.dp else 54.dp
            Box(Modifier.offset(x = shift).size(if (compact) 300.dp else 540.dp).background(closetPalette.halo, CircleShape))
            // 회전하는 렌더와 분리해 발밑 그림자의 크기와 위치를 고정한다.
            Canvas((if (compact) Modifier.align(Alignment.BottomCenter).offset(x = shift).padding(bottom = 42.dp)
                else Modifier.align(Alignment.Center).offset(x = shift, y = 238.dp))
                .size(if (compact) 140.dp else 240.dp, if (compact) 24.dp else 40.dp)) {
                val radius = size.width / 2f
                val center = Offset(radius, radius)
                val shadow = Brush.radialGradient(
                    0f to KagomColor.Closet.shadow.copy(alpha = 0.30f),
                    0.45f to KagomColor.Closet.shadow.copy(alpha = 0.16f),
                    1f to KagomColor.Closet.shadow.copy(alpha = 0f),
                    center = center, radius = radius,
                )
                scale(scaleX = 1f, scaleY = size.height / size.width, pivot = Offset.Zero) {
                    drawCircle(shadow, radius = radius, center = center)
                }
            }
            KagomCharacter(
                profile.skin, expression,
                if (compact) Modifier.offset(x = shift).size(234.dp, 366.dp) else Modifier.offset(x = shift).size(350.dp, 544.dp),
                worn = wornLayers(draft.head, draft.top, draft.bottom),
                onTouch = if (gearP) ({}) else null,
                turnable = gearP && expression == Expression.IDLE,
                alive = gearP,
            )
            SavedOutfitSlots(data.savedOutfits.size, selectedSlot, gearP, compact, closetPalette,
                onSelect = { index ->
                    if (!gearP) { toast = "주차 중에 이용할 수 있어요" }
                    else if (index > data.savedOutfits.size) { toast = "빈 칸은 순서대로 저장해요" }
                    else {
                        selectedSlot = index
                        data.savedOutfits.getOrNull(index)?.let { outfit ->
                            draft = outfit
                            onSave(outfit)
                            select(null)
                        }
                    }
                },
                onSave = {
                    val index = selectedSlot ?: data.savedOutfits.size
                    if (index < 3) {
                        onSaveOutfit(index, draft)
                        selectedSlot = index
                        toast = "${index + 1}번 착장을 저장했어요"
                    }
                },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = if (compact) 8.dp else 12.dp),
            )
        }
    }
    val grid: @Composable (Modifier, Dp) -> Unit = { m, cardHeight ->
        // 탭·페이지를 넘기면 앞 칸들이 옅어지며 다음 칸들로 바뀐다 (주행 중에는 바로)
        Crossfade(tab to shownPage, m, tween(KagomMotion.ms(KagomMotion.SCREEN, gearP)), label = "grid") { (shownTab, shown) ->
            val gap = if (split) KagomSpace.s12 else KagomSpace.s20
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                itemsOf(Category.entries[shownTab]).drop(shown * pageSize).take(pageSize).chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { item ->
                            key(item.id) {
                                val s = stateOf(item)
                                ClosetItemCard(item, s, selected?.id == item.id, split, cardHeight, closetPalette, { onCell(item) }, Modifier.weight(1f))
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    val pagination: @Composable (Modifier) -> Unit = { m ->
        Pagination(shownPage, pageCount, { page = it }, m)
    }
    val tabs: @Composable (Modifier) -> Unit = { m ->
        GhostSegmentedTabs(Category.entries.map { it.label }, tab, { tab = it; select(null); page = 0 }, m, itemWidth = null, gap = 8.dp)
    }
    // 고른 아이템의 정보: 보유 = 받은 날·곳, 잠금 = 얻는 조건과 진행 + 획득처로 가는 버튼
    val infoLine: @Composable (Modifier) -> Unit = { m ->
        if (gearP) selected?.let { item ->
            val state = stateOf(item)
            val locked = state == CellState.Locked
            val info = unlockInfo(item, ctx, data.completedQuests)
            val hasTarget = !Examples.isExample(item.id) && (info.landmarkId != null || Quests.byId(item.sourceId) != null)
            val text = popupDescription(item, locked, data) +
                if (locked && !Examples.isExample(item.id) && info.progressValue.isNotBlank()) " · ${info.progressValue}" else ""
            ClosetInfoLine(item, state, text, if (locked && hasTarget) info.cta else null, split, closetPalette,
                onNavigate = { select(null); onNavigate(info.target, info.landmarkId) },
                onDetails = { popupOpen = true },
                modifier = m,
            )
        }
    }
    // Split에서 잠긴 아이템을 누르면 얻는 방법이 바로 보이게 정보 줄까지 내린다. 보유 아이템은 입혀 본 곰이 보여야 해서 그대로 둔다
    LaunchedEffect(selected?.id) {
        val item = selected ?: return@LaunchedEffect
        if (split && stateOf(item) == CellState.Locked) {
            withFrameNanos { }
            splitScroll.animateScrollTo(splitScroll.maxValue)
        }
    }
    // 주행 중에는 저장 버튼이 꺼져 있는 까닭을 알려 준다 (가이드 5.2: 치장 저장은 기어 P에서만)
    val hint = if (gearP) "잠긴 아이템을 누르면 얻는 방법을 알려드려요" else "주행 중에는 돌려보기와 저장을 할 수 없어요"
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            val size = if (split) IviButtonSize.Medium else IviButtonSize.Large
            IviButton("초기화", { draft = draft.copy(head = null, top = null, bottom = null); select(null) }, type = IviButtonType.Primary, size = size, enabled = gearP)
            // 치장 저장은 기어 P에서만 (가이드 5.2)
            IviButton("저장하기", { onSave(draft); toast = "저장했어요" }, type = IviButtonType.Secondary, size = size, enabled = gearP)
        }
    }

    Box(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        if (split) {
            // Pleos 세로 슬롯(높이 약 920)은 Figma Split(1080)보다 낮아 페이지 넘김·정보 줄·버튼이 화면 밖으로 밀렸다.
            // 넘칠 때만 세로로 밀리고, 높이가 넉넉하면 전처럼 버튼이 맨 아래에 붙는다 (가정)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(splitScroll).heightIn(min = maxHeight).padding(20.dp)) {
                    Text("옷장", style = KagomType.Title28, color = KagomColor.Text.primary)
                    Spacer(Modifier.height(KagomSpace.s16))
                    preview(Modifier.fillMaxWidth().height(360.dp), true)
                    Spacer(Modifier.height(KagomSpace.s16))
                    Text(hint, style = KagomType.ClosetSmallText, color = KagomColor.Text.tertiary)
                    Spacer(Modifier.height(KagomSpace.s12))
                    tabs(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(KagomSpace.s12))
                    grid(Modifier.fillMaxWidth(), 230.dp)
                    // 그리드 아래 가운데 페이지 넘김, 버튼은 오른쪽 끝 (가이드 v1.2 2.1)
                    Box(Modifier.fillMaxWidth().padding(top = KagomSpace.s12), contentAlignment = Alignment.Center) { pagination(Modifier) }
                    infoLine(Modifier.padding(top = KagomSpace.s16))
                    Spacer(Modifier.height(KagomSpace.s16))
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(start = 36.dp, end = 8.dp, top = 28.dp, bottom = 68.dp)) {
                Text("옷장", style = KagomType.Title28, color = KagomColor.Text.primary)
                Spacer(Modifier.height(40.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cardHeight = (((maxHeight - 64.dp - 16.dp - 40.dp - 46.dp - 100.dp) / 3) * 0.96f).coerceAtLeast(180.dp)
                    // 미리보기와 하단 버튼이 같은 높이에서 끝나도록 계산한다.
                    val contentHeight = 64.dp + 16.dp + cardHeight * 3 + 40.dp + 46.dp + 100.dp
                    Row(Modifier.fillMaxSize()) {
                        // 그리드가 세 줄을 다 쓰므로 정보 줄은 미리보기 아래쪽(곰 발밑의 빈 자리)에 둔다 (가정)
                        Box(Modifier.width(760.dp).height(contentHeight)) {
                            preview(Modifier.fillMaxSize(), false)
                            infoLine(Modifier.align(Alignment.BottomCenter).padding(KagomSpace.s16))
                        }
                        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp)) {
                            tabs(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(KagomSpace.s16))
                            // 카드 수와 상관없이 세 줄 공간을 유지한다.
                            Box(Modifier.height(cardHeight * 3 + 40.dp).fillMaxWidth()) {
                                grid(Modifier, cardHeight)
                            }
                            Spacer(Modifier.height(46.dp))
                            // 페이지와 버튼의 아래쪽 기준을 맞춘다.
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                pagination(Modifier)
                                Spacer(Modifier.weight(1f))
                                actions()
                            }
                        }
                    }
                }
            }
        }

        if (gearP && popupOpen) selected?.let { item ->
            val info = unlockInfo(item, ctx, data.completedQuests)
            val hasTarget = !Examples.isExample(item.id) && (info.landmarkId != null || Quests.byId(item.sourceId) != null)
            ClosetItemPopup(item, stateOf(item) == CellState.Locked, profile, data, split, if (hasTarget) info.cta else null,
                onDismiss = { popupOpen = false },
                onNavigate = { select(null); onNavigate(info.target, info.landmarkId) },
            )
        }

        AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(top = if (split) 16.dp else 24.dp),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            IviToast(toast ?: lastToast, Modifier.padding(horizontal = 20.dp))
        }
    }
}

/** Figma 2027:10043: 실제 아이템 상태에 따라 썸네일과 상태만 바꾼다. */
@Composable
private fun ClosetItemCard(item: Item, state: CellState, selected: Boolean, compact: Boolean, cardHeight: Dp, palette: ClosetPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val highlighted = selected
    val context = LocalContext.current
    val path = KagomRenders.thumbPath(item.id)
    Column(
        modifier.height(cardHeight).clip(shape)
            .background(KagomColor.Surface.card)
            .border(if (highlighted) 3.dp else 1.dp, if (highlighted) palette.accent else KagomColor.Border.default, shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = if (compact) 8.dp else 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
            if (KagomRenders.exists(context, path)) AssetImage(path, Modifier.fillMaxSize().padding(top = if (compact) 12.dp else 20.dp).graphicsLayer {
                scaleX = 1.5f; scaleY = 1.5f; translationY = (if (compact) 4.dp else 8.dp).toPx()
            })
            else Text(item.mark, Modifier.offset(y = if (compact) 6.dp else 12.dp), style = KagomType.Heading24, color = KagomColor.Text.tertiary)
        }
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
        // 긴 이름은 위로 늘리고 이름과 상태의 아래쪽 기준은 유지한다.
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.BottomCenter) {
            Text(item.name, style = KagomType.ClosetItemName, color = KagomColor.Text.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(40.dp), contentAlignment = Alignment.TopCenter) {
            ClosetStateChip(state, palette)
        }
        Spacer(Modifier.height(if (compact) 0.dp else 10.dp))
    }
}

@Composable
private fun ClosetStateChip(state: CellState, palette: ClosetPalette) {
    val label = when (state) { CellState.Equipped -> "착용중"; CellState.Owned -> "보유중"; CellState.Locked -> "잠금" }
    val color = when (state) { CellState.Equipped -> palette.selectedBackground; CellState.Owned -> palette.ownedBackground; CellState.Locked -> KagomColor.Closet.locked }
    val textColor = when (state) { CellState.Equipped -> palette.equippedText; CellState.Owned -> palette.ownedText; CellState.Locked -> KagomColor.Closet.lockedText }
    Chip(label, color, textColor, height = 34.dp, style = KagomType.ClosetStatusText, horizontalPadding = 18.dp)
}

/**
 * 정보 줄 (Full: 미리보기 아래쪽, Split: 그리드 아래): 고른 아이템의 이름·상태와 한 줄 설명 (보유 = 받은 날·곳, 잠금 = 얻는 조건·진행).
 * 잠긴 아이템은 획득처로 가는 버튼([cta])을 같이 둔다. 줄 전체를 누르면 팝업(Figma 2021:5094 / 2071:7596)으로 자세히 본다.
 * 화면을 덮지 않아서 닫을 필요가 없고, 다른 아이템을 누르면 내용만 바뀐다 (디자인에 없는 자리, 가정)
 */
@Composable
private fun ClosetInfoLine(
    item: Item, state: CellState, text: String, cta: String?, compact: Boolean, palette: ClosetPalette,
    onNavigate: () -> Unit, onDetails: () -> Unit, modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s10)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s10)) {
                Text(item.name, Modifier.weight(1f, fill = false), style = KagomType.ClosetItemName, color = KagomColor.Text.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                ClosetStateChip(state, palette)
            }
            Text("자세히 ›", style = KagomType.ClosetSmallStrong, color = palette.accent, maxLines = 1)
        }
    }
    val body: @Composable () -> Unit = { Text(text, style = KagomType.ClosetSmallText, color = KagomColor.Text.secondary) }
    val box = modifier.fillMaxWidth().clip(shape).background(KagomColor.Surface.card).border(1.dp, KagomColor.Border.default, shape)
        .clickable(onClickLabel = "자세히 보기", onClick = onDetails)
        .padding(horizontal = if (compact) 20.dp else 28.dp, vertical = if (compact) 16.dp else 20.dp)
    if (compact) {
        // Split은 폭이 좁아 버튼을 설명 아래 오른쪽 끝에 둔다 (버튼은 늘 오른쪽 끝, 가이드 v1.2 2.1)
        Column(box, verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            header()
            body()
            if (cta != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IviButton(cta, onNavigate, size = IviButtonSize.Medium)
            }
        }
    } else {
        Row(box, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s16)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
                header()
                body()
            }
            if (cta != null) IviButton(cta, onNavigate, size = IviButtonSize.Medium)
        }
    }
}

/** 2071:7849: 빈 칸은 차례대로 고르고 저장 버튼으로 현재 시착을 기록한다. */
@Composable
private fun SavedOutfitSlots(count: Int, selected: Int?, enabled: Boolean, compact: Boolean, palette: ClosetPalette, onSelect: (Int) -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val size = if (compact) 64.dp else 96.dp
    val width = 96.dp
    val shape = RoundedCornerShape(KagomRadius.r24)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
        Text("저장 착장", modifier = Modifier.width(width), style = KagomType.ClosetSlotText, color = KagomColor.Text.primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        repeat(3) { index ->
            val filled = index < count
            Box(Modifier.width(width).height(size).clip(shape)
                .background(if (selected == index) palette.selectedBackground else if (filled) KagomColor.Surface.card else KagomColor.Closet.slotEmpty)
                .border(if (selected == index) 2.dp else 1.dp, if (selected == index) palette.accent else KagomColor.Closet.slotBorder, shape)
                .clickable { onSelect(index) }
                .semantics { contentDescription = if (filled) "저장 착장 ${index + 1}번" else "빈 착장 ${index + 1}번" }, contentAlignment = Alignment.Center) {
                Text(if (filled) "${index + 1}" else "+", style = if (compact) KagomType.ClosetSlotNumberCompact else KagomType.ClosetSlotNumber,
                    color = if (filled) KagomColor.Closet.slotNumber else KagomColor.Closet.slotPlus, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val canSave = enabled && (selected != null || count < 3)
        Box(Modifier.width(width).height(64.dp).clickable(enabled = canSave, onClick = onSave), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(KagomRadius.r16))
                .background(if (canSave) palette.button else KagomColor.Surface.disabled), contentAlignment = Alignment.Center) {
                Text("저장하기", style = KagomType.ClosetSlotButtonText, color = if (canSave) KagomColor.Text.inverse else KagomColor.Text.disabled, maxLines = 1)
            }
        }
    }
}

