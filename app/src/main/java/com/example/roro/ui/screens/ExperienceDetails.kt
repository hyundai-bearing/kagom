package com.example.roro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.roro.R
import com.example.roro.core.Expression
import com.example.roro.core.Item
import com.example.roro.core.Items
import com.example.roro.core.LandmarkScenery
import com.example.roro.core.Progress
import com.example.roro.core.Quest
import com.example.roro.core.Severity
import com.example.roro.data.GeoPoint
import com.example.roro.ui.components.Chip
import com.example.roro.ui.components.LocalToday
import com.example.roro.ui.components.TitlePlate
import com.example.roro.ui.components.CloseGlyph
import com.example.roro.ui.components.ItemThumb
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.KagomCharacter
import com.example.roro.ui.components.LockGlyph
import com.example.roro.ui.components.PinState
import com.example.roro.ui.components.StatusBadge
import com.example.roro.ui.components.TouchAct
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.random.Random

data class DetailAvatar(val skin: String, val expression: Expression, val worn: List<String>)

internal val detailDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/** 같은 랜드마크는 상세를 다시 열어도 같은 풍경을 쓴다. 범주에 리소스를 추가하면 다음 선택에 반영된다. */
private val sceneryVariants = mapOf(
    LandmarkScenery.COAST to listOf(R.drawable.detail_coast),
    LandmarkScenery.HIGHLAND to listOf(R.drawable.detail_highland),
    LandmarkScenery.FOREST to listOf(R.drawable.detail_forest),
)

@Composable
internal fun QuestDetail(
    quest: Quest,
    doneAt: Long?,
    avatar: DetailAvatar,
    split: Boolean,
    onBack: () -> Unit,
) {
    val firstDrive = quest.id == "QST-01"
    val date = doneAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(detailDateFormat) }
    var cheer by remember { mutableStateOf(false) }
    val record: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            Text(if (firstDrive) "처음 함께한 10 km" else quest.name, style = KagomType.Title28, color = KagomColor.Text.primary)
            StatusBadge(Severity.OK, date?.let { "$it 달성" } ?: "달성")
            Box(
                Modifier.fillMaxWidth().then(if (split) Modifier else Modifier.weight(1f))
                    .background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r24))
                    .padding(if (split) KagomSpace.s16 else KagomSpace.s40),
                contentAlignment = Alignment.Center,
            ) {
                // 칭호 번호판과 같은 그림에 과제 이름과 달성일을 쓴다 (2026-11-13 요청)
                val doneDay = doneAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() } ?: LocalToday.current
                TitlePlate(if (firstDrive) "첫 10 km" else quest.name, doneDay, avatar.skin,
                    Modifier.widthIn(max = if (split) 468.dp else 740.dp).fillMaxWidth())
            }
        }
    }
    val companion: @Composable (Modifier, Modifier) -> Unit = { modifier, sceneSize ->
        Column(
            modifier.fillMaxWidth().background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r24))
                .padding(if (split) KagomSpace.s16 else KagomSpace.s24),
            verticalArrangement = Arrangement.spacedBy(if (split) KagomSpace.s12 else KagomSpace.s16),
        ) {
            val worn = avatar.worn.mapNotNull(Items::byId).reversed()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
                Text("현재 착장", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                Chip(worn.joinToString(" · ") { it.name }.ifEmpty { "기본 착장" },
                    KagomColor.Surface.subtle, KagomColor.Text.primary)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().then(if (split) sceneSize else Modifier.weight(1f))
                .clip(RoundedCornerShape(KagomRadius.r20)).background(KagomColor.Surface.mintSoft)) {
                DetailScenery(R.drawable.detail_road)
                val speechTop = if (split) KagomSpace.s12 else maxHeight * 0.08f
                val bubbleColor = KagomColor.Surface.mint
                // 말풍선과 카곰의 영역을 나눠 긴 대사가 줄을 바꿔도 얼굴을 덮지 않게 한다.
                Column(
                    Modifier.fillMaxSize().padding(top = speechTop, bottom = KagomSpace.s8),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (firstDrive) "우리 처음으로 10 km를 같이 달렸어요!" else quest.flavor,
                        Modifier.padding(horizontal = KagomSpace.s16).widthIn(max = 560.dp).fillMaxWidth()
                            .background(bubbleColor, RoundedCornerShape(KagomRadius.r24))
                            .padding(horizontal = KagomSpace.s16, vertical = if (split) KagomSpace.s10 else KagomSpace.s20),
                        style = if (split) KagomType.Heading20 else KagomType.Heading24,
                        color = KagomColor.Text.primary, textAlign = TextAlign.Center,
                    )
                    Canvas(Modifier.size(width = 32.dp, height = 12.dp)) {
                        drawPath(Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        }, bubbleColor)
                    }
                    DetailCharacter(avatar, TouchAct.CHEER, cheer, { cheer = false },
                        Modifier.weight(1f).fillMaxWidth(if (split) 0.72f else 0.84f))
                }
            }
            IviButton("같이 만세하기", { cheer = true }, Modifier.fillMaxWidth(),
                size = if (split) IviButtonSize.Medium else IviButtonSize.Large)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(KagomColor.Surface.page)) {
        val detailHeight = (maxHeight - 104.dp).coerceAtLeast(580.dp)
        val compactSceneHeight = (maxHeight - 624.dp).coerceIn(208.dp, 340.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (split) KagomSpace.s20 else KagomSpace.s36, vertical = KagomSpace.s20),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s16)) {
                Image(painterResource(if (split) R.drawable.map_back_split else R.drawable.map_back), "도전과제 목록으로",
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack))
                Text("달성한 도전과제", style = KagomType.Heading24, color = KagomColor.Text.primary)
            }
            if (split) {
                record(Modifier.fillMaxWidth())
                companion(Modifier, Modifier.height(compactSceneHeight))
            } else Row(Modifier.height(detailHeight), horizontalArrangement = Arrangement.spacedBy(KagomSpace.s24)) {
                record(Modifier.weight(1f).fillMaxHeight())
                companion(Modifier.weight(1f).fillMaxHeight(), Modifier)
            }
        }
    }
}

@Composable
internal fun LandmarkDetail(
    view: LandmarkView,
    location: GeoPoint?,
    avatar: DetailAvatar,
    split: Boolean,
    onBack: () -> Unit,
    onNavigate: () -> Unit,
    onEquip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val landmark = view.landmark
    val reward = Items.byId(landmark.rewardItemId) ?: return
    val unlocked = view.pin == PinState.Unlocked
    val pending = view.pin == PinState.Unlocking
    val showPhotos = unlocked && hasLandmarkPhotos(landmark.id)
    val landscape = remember(landmark.id, landmark.scenery) {
        val variants = sceneryVariants[landmark.scenery].orEmpty()
        if (variants.isEmpty()) null else variants.random(Random(landmark.id.hashCode()))
    }
    var wave by remember { mutableStateOf(false) }
    val title = if (unlocked) "${landmark.name}에 함께 간 날" else landmark.name
    val date = view.firstVisitDate
    val info = when {
        unlocked -> date?.let { "$it · 첫 방문" } ?: "첫 방문"
        pending -> "정차 중 보상 상자를 열면 풍경이 열려요"
        else -> {
            val km = location?.let { Progress.distanceM(it.lat, it.lon, landmark.lat, landmark.lon) / 1000 }
            listOfNotNull("아직 가보지 않은 곳", landmark.areaName, km?.let { "직선거리 약 ${it.roundToInt()} km" }).joinToString(" · ")
        }
    }
    val shape = RoundedCornerShape(KagomRadius.r24)
    Column(
        // Split: 지도 시트 안에 들어간다. 카드 바탕과 위 여백(손잡이)은 시트가 그린다
        (if (split) modifier.padding(start = KagomSpace.s16, end = KagomSpace.s16, bottom = KagomSpace.s16)
            else modifier.clip(shape).background(KagomColor.Surface.card).padding(KagomSpace.s24)),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s12),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!split) Image(
                painterResource(R.drawable.map_back),
                contentDescription = "랜드마크 목록으로 돌아가기",
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            )
            Text(title, Modifier.weight(1f), style = KagomType.Title28, color = KagomColor.Text.primary)
            // Split: 지도 머리줄의 화살표(상위 지도)와 나란히 있으면 헷갈려서, 상세는 오른쪽의 닫기(×)로 닫는다
            if (split) Box(
                Modifier.size(48.dp).clip(CircleShape).background(KagomColor.Surface.subtle)
                    .clickable(role = Role.Button, onClickLabel = "랜드마크 상세 닫기", onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(Modifier.size(20.dp), KagomColor.Text.primary)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val sceneSize = if (split && showPhotos) {
                Modifier.height((maxHeight * 0.37f).coerceIn(112.dp, 210.dp))
            } else if (split) {
                Modifier.height((maxHeight - 78.dp).coerceIn(if (unlocked) 146.dp else 194.dp, 310.dp))
            } else Modifier.aspectRatio(1.25f)
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (split) KagomSpace.s8 else KagomSpace.s10),
            ) {
                Text(info, style = KagomType.Caption14, color = KagomColor.Text.secondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
                    Text(if (unlocked) "여기서 얻은 착장" else "방문하면 얻는 착장",
                        style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                    Chip(reward.name, KagomColor.Surface.subtle, KagomColor.Text.primary,
                        if (unlocked) Modifier.clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "보상 장착", onClick = onEquip) else Modifier)
                }
                Box(Modifier.fillMaxWidth().then(sceneSize)
                    .clip(RoundedCornerShape(KagomRadius.r20)).background(KagomColor.Surface.subtle)) {
                    if (unlocked) {
                        DetailScenery(landscape)
                        DetailCharacter(avatar, TouchAct.WAVE, wave, { wave = false },
                            Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.92f).fillMaxWidth(0.65f))
                    } else {
                        LockedScenery(avatar, reward, pending, split)
                    }
                }
                if (!split) Text(
                    if (unlocked) "이 착장, 여기서 같이 얻었죠." else "여기 가면 ${reward.name} 보상을 받을 수 있어요. 같이 가볼래요?",
                    Modifier.fillMaxWidth().background(KagomColor.Surface.mint, RoundedCornerShape(KagomRadius.r20))
                        .padding(KagomSpace.s12),
                    style = KagomType.Body16Strong, color = KagomColor.Text.primary, textAlign = TextAlign.Center,
                )
                if (showPhotos) LandmarkPhotoSection(landmark.id, title, split)
            }
        }
        val actionSize = if (split) IviButtonSize.Medium else IviButtonSize.Large
        if (unlocked) IviButton("카곰에게 손 흔들기", { wave = true }, Modifier.fillMaxWidth(), size = actionSize)
        else if (!pending) IviButton("여기로 길 안내 시작", onNavigate, Modifier.fillMaxWidth(), size = actionSize)
    }
}

@Composable
private fun LockedScenery(avatar: DetailAvatar, reward: Item, pending: Boolean, split: Boolean) {
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().padding(top = if (split) KagomSpace.s16 else KagomSpace.s24),
            verticalAlignment = Alignment.CenterVertically) {
            KagomCharacter(avatar.skin, avatar.expression, Modifier.weight(1f).fillMaxHeight().alpha(0.42f), worn = avatar.worn)
            Column(Modifier.weight(1f).padding(KagomSpace.s12)
                .background(KagomColor.Surface.card, RoundedCornerShape(KagomRadius.r20)).padding(KagomSpace.s12),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(KagomSpace.s4),
            ) {
                ItemThumb(reward.mark, if (split) 48.dp else 64.dp, locked = false, itemId = reward.id)
                Text(reward.name, style = KagomType.Body16Strong, color = KagomColor.Text.primary, textAlign = TextAlign.Center)
                Text("방문 보상 · ${reward.category.noun}", style = KagomType.Caption14,
                    color = KagomColor.Text.tertiary, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(KagomSpace.s12)
                .background(KagomColor.Surface.inverse, CircleShape).padding(horizontal = KagomSpace.s12, vertical = KagomSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8), verticalAlignment = Alignment.CenterVertically,
        ) {
            LockGlyph(Modifier.size(16.dp), KagomColor.Text.inverse)
            Text(if (pending) "보상 받으면 풍경이 열려요" else "방문하면 풍경이 열려요",
                style = KagomType.Caption14Strong, color = KagomColor.Text.inverse)
        }
    }
}

@Composable
private fun DetailScenery(resource: Int?) {
    resource?.let {
        Image(painterResource(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    }
}

@Composable
private fun DetailCharacter(avatar: DetailAvatar, act: TouchAct, requested: Boolean, onShown: () -> Unit, modifier: Modifier) {
    KagomCharacter(
        avatar.skin, avatar.expression, modifier, worn = avatar.worn, onTouch = {}, alive = true,
        preferredAct = act, demoAct = act.key.takeIf { requested }, onDemoActShown = onShown,
    )
}
