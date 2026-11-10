package com.example.roro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.roro.core.Progress
import com.example.roro.core.QuestFilter
import com.example.roro.core.QuestProgress
import com.example.roro.ui.components.AvatarBearGlyph
import com.example.roro.ui.components.FilterChip
import com.example.roro.ui.components.ProgressBar
import com.example.roro.ui.components.QuestCard
import com.example.roro.ui.components.QuestCardSize
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode

/**
 * SCR-04 도전과제 (Figma 6:2085 Full / 7:2099 Split, 11/10 개편): 제목 → 달성률 카드 → 필터 칩 → 카드 목록.
 * [doneAt] = 도전과제 id → 달성 시각(ms)
 */
@Composable
fun QuestsScreen(quests: List<QuestProgress>, doneAt: Map<String, Long>) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val pad = if (split) 20.dp else 36.dp
    var filter by rememberSaveable { mutableStateOf(QuestFilter.ALL) }
    val shown = Progress.listed(quests, doneAt, filter)
    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page).verticalScroll(rememberScrollState())
            .padding(start = pad, end = pad, top = if (split) 20.dp else 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("도전과제", style = KagomType.Title28, color = KagomColor.Text.primary)
            if (!split) {
                Spacer(Modifier.weight(1f))
                Text("달성하면 정차 중에 보상 상자가 열려요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
            }
        }
        SummaryCard(Progress.achievedPercent(quests))
        Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            QuestFilter.entries.forEach { f -> FilterChip(f.label, f == filter, { filter = f }) }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            shown.forEach { p ->
                key(p.quest.id) { QuestCard(p, if (split) QuestCardSize.Compact else QuestCardSize.Wide, doneAt = doneAt[p.quest.id]) }
            }
            // 필터에 걸리는 과제가 없을 때 (Figma에 없는 상태)
            if (shown.isEmpty()) Text(
                if (filter == QuestFilter.DONE) "아직 달성한 도전과제가 없어요" else "도전과제를 모두 달성했어요",
                Modifier.padding(horizontal = KagomSpace.s8, vertical = KagomSpace.s24),
                style = KagomType.Body16, color = KagomColor.Text.tertiary,
            )
        }
    }
}

/** summaryCard (Figma 193:4478): 곰 얼굴 + "달성률" + 퍼센트 + 진행 바 */
@Composable
private fun SummaryCard(percent: Int) {
    Row(
        Modifier.fillMaxWidth().height(96.dp).background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r24))
            .padding(horizontal = KagomSpace.s28),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s20),
    ) {
        Box(Modifier.size(56.dp).background(KagomColor.Surface.mintSoft, CircleShape), contentAlignment = Alignment.Center) {
            AvatarBearGlyph(Modifier.size(36.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("달성률", style = KagomType.Heading24, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
                Text("$percent%", style = KagomType.Heading24, color = KagomColor.Text.accent)
            }
            ProgressBar(percent / 100f, Modifier.fillMaxWidth())
        }
    }
}
