
package com.example.roro.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.roro.core.Category
import com.example.roro.core.Examples
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
import com.example.roro.ui.components.GhostSegmentedTabs
import com.example.roro.ui.components.ItemGridCell
import com.example.roro.ui.components.ItemUnlockPanel
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.IviToast
import com.example.roro.ui.components.KagomTurntable
import com.example.roro.ui.components.NavTab
import com.example.roro.ui.components.Pagination
import com.example.roro.ui.components.unlockLabel
import com.example.roro.ui.components.wornLayers
import com.example.roro.ui.josa
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSkinPalette
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
 * 한 페이지 = 열 수 × 줄 수: Full 4×3 = 12칸, 획득 안내 패널이 열리면 3×3 = 9칸, Split 3칸 (가이드 v1.2 2.1).
 * [examples] = 시연 패널의 '예시 데이터' (Figma 예시 아이템 03~12, 페이지 넘김 확인용)
 */
@Composable
fun ClosetScreen(
    profile: Profile,
    data: AppData,
    ctx: ProgressContext,
    gearP: Boolean,
    onSave: (Equipped) -> Unit,
    examples: Boolean = false,
    /** CTA: 탭 이동. 랜드마크 아이템이면 두 번째 값 = 지도에서 바로 보여 줄 랜드마크 id */
    onNavigate: (NavTab, String?) -> Unit,
) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val palette = KagomSkinPalette.of(profile.skin)
    var draft by remember(data.equipped) { mutableStateOf(data.equipped) }
    var tab by remember { mutableIntStateOf(0) }
    var lockedSel by remember { mutableStateOf<Item?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    LaunchedEffect(toast) { toast?.let { lastToast = it; delay(3_000); toast = null } }
    val category = Category.entries[tab]
    val items = Items.of(category) + if (examples) Examples.of(category) else emptyList()

    fun pageSizeFor(panelOpen: Boolean) = when {
        split -> 3
        panelOpen -> 9
        else -> 12
    }
    val cols = if (split || lockedSel != null) 3 else 4
    val pageSize = pageSizeFor(lockedSel != null)
    val pageCount = maxOf(1, (items.size + pageSize - 1) / pageSize)
    val shownPage = page.coerceIn(0, pageCount - 1)

    /** 패널이 열리고 닫히면 칸 수가 바뀐다 → 선택한 아이템(없으면 보던 첫 칸)이 있는 페이지로 */
    fun select(sel: Item?) {
        val anchor = sel?.let { s -> items.indexOfFirst { it.id == s.id } }?.takeIf { it >= 0 } ?: (shownPage * pageSize)
        lockedSel = sel
        page = anchor / pageSizeFor(sel != null)
    }
    BackHandler(enabled = lockedSel != null) { select(null) }

    fun owns(item: Item) = item.id in data.owned || (Examples.isExample(item.id) && item.id in Examples.owned)

    fun stateOf(item: Item) = when {
        !owns(item) -> CellState.Locked
        draft[item.category] == item.id -> CellState.Equipped
        else -> CellState.Owned
    }

    fun captionOf(item: Item, s: CellState): String = when (s) {
        CellState.Equipped -> "착용 중"
        CellState.Owned -> if (Examples.isExample(item.id)) "보유 · 예시 데이터" else "보유"
        CellState.Locked -> {
            val q = Quests.byId(item.sourceId)
            if (q != null && q.cumulative && item.source != Source.LANDMARK) "${item.condition} (${Progress.questProgress(q, ctx, data.completedQuests).countText})"
            else item.condition
        }
    }

    fun onCell(item: Item) {
        val s = stateOf(item)
        if (s == CellState.Locked) {
            select(item)
            val info = unlockInfo(item, ctx, data.completedQuests)
            toast = if (split) info.toastSplit else info.toastFull
            return
        }
        if (lockedSel != null) select(null)
        if (Examples.isExample(item.id)) { toast = "예시 아이템이라 입어 볼 수 없어요"; return }   // 렌더가 없다
        draft = when {
            s == CellState.Owned -> draft.with(item.category, item.id)
            item.category == Category.TITLE -> draft             // 칭호는 하나는 꼭 단다
            else -> draft.with(item.category, null)
        }
    }

    // 시착: 저장 전 draft를 입혀 본다 (스킨 + 하의 + 상의 + 머리). 밀면 돌려 볼 수 있다.
    // 가이드 v1.2: 프리뷰 아래 장착 아이템 칩 줄은 없앴다
    val preview: @Composable (Modifier, Boolean) -> Unit = { m, compact ->
        Box(m, contentAlignment = Alignment.Center) {
            KagomTurntable(profile.skin, wornLayers(draft.head, draft.top, draft.bottom),
                if (compact) Modifier.size(240.dp, 376.dp) else Modifier.size(480.dp, 754.dp), enabled = gearP)
        }
    }
    val grid: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            items.drop(shownPage * pageSize).take(pageSize).chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
                    row.forEach { item ->
                        key(item.id) {
                            val s = stateOf(item)
                            ItemGridCell(item, s, captionOf(item, s), lockedSel?.id == item.id, { onCell(item) })
                        }
                    }
                }
            }
        }
    }
    val pagination: @Composable (Modifier) -> Unit = { m ->
        if (pageCount > 1) Pagination(shownPage, pageCount, { page = it }, m)
    }
    val tabs: @Composable (Modifier) -> Unit = { m ->
        GhostSegmentedTabs(Category.entries.map { it.label }, tab, { tab = it; lockedSel = null; page = 0 }, m, itemWidth = if (split) null else 160.dp)
    }
    val actions: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            val size = if (split) IviButtonSize.Medium else IviButtonSize.Large
            IviButton("원래대로", { draft = data.equipped; select(null) }, type = IviButtonType.Primary, size = size)
            // 치장 저장은 기어 P에서만 (가이드 5.2)
            IviButton("저장하기", { onSave(draft); toast = "저장했어요" }, type = IviButtonType.Secondary, size = size, enabled = gearP)
        }
    }

    Box(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        if (split) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                preview(Modifier.fillMaxWidth().height(400.dp).background(palette.canvasTint, RoundedCornerShape(KagomRadius.r24)).padding(12.dp), true)
                Spacer(Modifier.height(KagomSpace.s16))
                Text("치장", style = KagomType.Heading22, color = KagomColor.Text.primary)
                Text("잠긴 아이템을 누르면 얻는 방법을 알려드려요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                Spacer(Modifier.height(KagomSpace.s12))
                tabs(Modifier.fillMaxWidth())
                Spacer(Modifier.height(KagomSpace.s12))
                grid(Modifier.fillMaxWidth())
                // 그리드 아래 가운데 페이지 넘김, 버튼은 오른쪽 끝 (가이드 v1.2 2.1)
                Box(Modifier.fillMaxWidth().padding(top = KagomSpace.s12), contentAlignment = Alignment.Center) { pagination(Modifier) }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                preview(Modifier.width(824.dp).fillMaxHeight().background(palette.canvasTint), false)
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("치장", style = KagomType.Heading24, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
                        Text("잠긴 아이템을 누르면 얻는 방법을 알려드려요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                    }
                    Spacer(Modifier.height(KagomSpace.s16))
                    tabs(Modifier)
                    Spacer(Modifier.height(KagomSpace.s16))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        grid(Modifier)
                        lockedSel?.let { item ->
                            val info = unlockInfo(item, ctx, data.completedQuests)
                            ItemUnlockPanel(item, item.source.unlockLabel(), info.progressValue, info.ratio, info.hint, info.cta, true,
                                { onNavigate(info.target, info.landmarkId) }, Modifier.align(Alignment.TopEnd).width(320.dp))
                        }
                    }
                    // closetFooter: 페이지 넘김 | 빈칸 | 원래대로·저장하기 (버튼은 늘 오른쪽 끝, 가이드 v1.2 2.1)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        pagination(Modifier)
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                }
            }
        }

        // Split: 바텀시트 (딤 위)
        if (split) lockedSel?.let { item ->
            val info = unlockInfo(item, ctx, data.completedQuests)
            Box(Modifier.fillMaxSize().background(KagomColor.Overlay.dim).clickable(remember { MutableInteractionSource() }, null) { select(null) })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(KagomColor.Surface.card, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(start = 44.dp, end = 44.dp, top = 12.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(40.dp, 5.dp).background(KagomColor.Border.divider, RoundedCornerShape(KagomRadius.full)))
                Spacer(Modifier.height(32.dp))
                ItemUnlockPanel(item, item.source.unlockLabel(), info.progressValue, info.ratio, info.hint, info.cta, true,
                    { select(null); onNavigate(info.target, info.landmarkId) }, Modifier.fillMaxWidth(), framed = false)
            }
        }

        AnimatedVisibility(toast != null, Modifier.align(Alignment.TopCenter).padding(top = if (split) 16.dp else 24.dp),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
            IviToast(toast ?: lastToast, Modifier.padding(horizontal = 20.dp))
        }
    }
}

