package com.example.roro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.roro.core.QuestProgress
import com.example.roro.core.Severity
import com.example.roro.ui.components.QuestCard
import com.example.roro.ui.components.QuestCardSize
import com.example.roro.ui.components.StatusBadge
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.LayoutMode
import com.example.roro.ui.theme.LocalLayoutMode

/** SCR-04 도전과제 (questList) */
@Composable
fun QuestsScreen(quests: List<QuestProgress>, totalExp: Int) {
    val split = LocalLayoutMode.current == LayoutMode.SPLIT
    val pad = if (split) 20.dp else 36.dp
    Column(
        Modifier.fillMaxSize().background(KagomColor.Surface.page).verticalScroll(rememberScrollState())
            .padding(start = pad, end = pad, top = if (split) 20.dp else 28.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            Text("도전과제", style = KagomType.Heading24, color = KagomColor.Text.primary)
            StatusBadge(Severity.OK, "달성 ${quests.count { it.achieved }} / ${quests.size}")
            StatusBadge(Severity.OBSERVE, "누적 EXP $totalExp")
            if (!split) {
                Spacer(Modifier.weight(1f))
                Text("달성하면 정차 중에 보상 상자가 열려요", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
            }
        }
        Spacer(Modifier.height(KagomSpace.s12))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            quests.forEach { QuestCard(it, if (split) QuestCardSize.Compact else QuestCardSize.Wide) }
        }
    }
}
