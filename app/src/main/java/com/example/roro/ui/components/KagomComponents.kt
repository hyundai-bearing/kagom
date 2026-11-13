package com.example.roro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.roro.core.Item
import com.example.roro.core.QuestProgress
import com.example.roro.core.Severity
import com.example.roro.core.Source
import com.example.roro.core.StatusRow
import com.example.roro.core.Items
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomMotion
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSize
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.PleosKit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ───────────── 작은 부품 ─────────────

/** statusBadge (Severity=Observe|Recommend|Urgent|Ok · Label). 높이 40 */
@Composable
fun StatusBadge(
    severity: Severity,
    label: String,
    modifier: Modifier = Modifier,
    style: TextStyle = KagomType.Caption14Strong,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(KagomRadius.full)
    Box(
        modifier
            .height(KagomSize.chipHeight)
            .clip(shape)
            .background(KagomColor.Severity.bg(severity), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = KagomSpace.s16),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style, color = KagomColor.Severity.fg(severity), maxLines = 1)
    }
}

/** 작은 칩 ("달성", "NEW", 출처 칩 등) */
@Composable
fun Chip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, height: Dp = 28.dp, style: TextStyle = KagomType.Caption14Strong, horizontalPadding: Dp = 10.dp) {
    Box(
        modifier.height(height).background(bg, RoundedCornerShape(KagomRadius.full)).padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = style, color = fg, maxLines = 1) }
}

/**
 * progressBar (Size=Wide 480 | Compact 240 · Ratio). 높이 8.
 * 값이 바뀌면 새 값까지 차오른다 (EXP 획득, 도전과제 진행). 처음 그릴 때는 움직이지 않는다
 */
@Composable
fun ProgressBar(ratio: Float, modifier: Modifier = Modifier, height: Dp = 8.dp, fill: Color = KagomColor.Progress.fill) {
    val r by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(KagomMotion.PROGRESS, easing = FastOutSlowInEasing), label = "progress")
    Box(modifier.height(height).clip(RoundedCornerShape(KagomRadius.full)).background(KagomColor.Progress.track)) {
        if (r > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(r).background(fill, RoundedCornerShape(KagomRadius.full)))
    }
}

/**
 * 아이템 이미지 슬롯 (ItemCell·UnlockGuide·LandmarkPopup·RewardModal 공용, 가이드 8.3).
 * [itemId]의 3D 썸네일(assets/kagom/thumb_<ID>.webp)이 있으면 그것을, 없으면 Mark 글자 플레이스홀더.
 */
@Composable
fun ItemThumb(
    mark: String?,
    size: Dp,
    locked: Boolean,
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    markStyle: TextStyle = KagomType.Heading20,
    lockBadge: Boolean = false,
    itemId: String? = null,
) {
    val shape = RoundedCornerShape(radius)
    val context = LocalContext.current
    val thumb = itemId?.let { KagomRenders.thumbPath(it) }?.takeIf { !locked && KagomRenders.exists(context, it) }
    Box(
        modifier.size(size).background(if (locked) KagomColor.Surface.dark else KagomColor.Surface.mint, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 배경 아이템은 3D 썸네일 대신 그림 자체를 칸에 채운다
            !locked && KagomBackgrounds.has(itemId) -> BackgroundArt(itemId, Modifier.fillMaxSize().clip(shape))
            thumb != null -> AssetImage(thumb, Modifier.fillMaxSize().padding(size * 0.06f))
            locked && mark == null -> LockGlyph(Modifier.size(size * 0.32f), KagomColor.Text.inverse)
            else -> Text(mark ?: "?", style = markStyle, color = if (locked) KagomColor.Text.inverse else KagomColor.Text.accent)
        }
        if (lockBadge) LockGlyph(Modifier.align(Alignment.BottomEnd).padding(end = size * 0.1f, bottom = size * 0.08f).size(size * 0.2f), KagomColor.Text.inverse)
    }
}

/** 카곰 말풍선 (보상 모달) */
@Composable
fun SpeechBubble(name: String, text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(KagomColor.Surface.subtle, RoundedCornerShape(KagomRadius.r16)).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8),
    ) {
        Text(name, style = KagomType.Body16Strong, color = KagomColor.Text.accent)
        Text(text, style = KagomType.Body16, color = KagomColor.Text.primary)
    }
}

// ───────────── 내비게이션 ─────────────

/** GNB 목적지 (레일/탭바 공용 라벨) */
enum class NavTab(val label: String) { HOME("홈"), QUESTS("도전"), MAP("지도"), CLOSET("치장") }

/** navItem (State=Default|Selected · Label). 72×58 */
@Composable
fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(KagomRadius.r16)
    val bg by animateColorAsState(if (selected) KagomColor.Surface.inverse else KagomColor.Surface.card, tween(KagomMotion.FAST), label = "navBg")
    val fg by animateColorAsState(if (selected) KagomColor.Text.inverse else KagomColor.Text.secondary, tween(KagomMotion.FAST), label = "navFg")
    Box(
        modifier
            .size(KagomSize.navItem, KagomSize.navItemHeight)
            .clip(shape)
            .background(bg, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "$label 탭" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = KagomType.Caption14Strong, color = fg, maxLines = 1)
    }
}

/** gnbRail (Full): 좌측 88px, 상단 36 패딩, 항목 간격 14 */
@Composable
fun NavRail(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(KagomSize.navRailWidth).fillMaxHeight().background(KagomColor.Surface.card).padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NavTab.entries.forEach { NavItem(it.label, it == selected, { onSelect(it) }) }
    }
}

/** bottomTabBar (Split): 하단 64px, 항목 4개 균등 */
@Composable
fun BottomTabBar(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(KagomSize.tabBarHeight).background(KagomColor.Surface.card).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { NavItem(it.label, it == selected, { onSelect(it) }) }
    }
}

// ───────────── 프로필 헤더 ─────────────

/**
 * tierEmblem (Tier). [large] = POP-04. Figma 6:1713: 높이 36, 글자 16/24 Bold.
 * 화면 글자는 "레벨 N"이다 (Figma가 11/10에 티어 → 레벨로 바꿨다. 코드 이름은 Tier 그대로 둔다)
 */
@Composable
fun TierEmblem(tier: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val h = if (large) 96.dp else 36.dp
    Row(
        modifier.height(h).background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.full))
            .padding(horizontal = if (large) 28.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 20.dp else 8.dp),
    ) {
        Box(Modifier.size(if (large) 38.dp else 16.dp).background(KagomColor.Accent.tier, CircleShape))
        Text("레벨 $tier", style = if (large) KagomType.Title36 else KagomType.Body16Strong, color = KagomColor.Text.inverse)
    }
}

/**
 * headerProfile (kagomName · equippedTitle · tierBadge · expProgress 200×6).
 * 한 줄에 안 들어가면 뒤쪽부터 다음 줄로 내린다: Split(폭 600)에서 이름이 길면(10자) EXP가 눌려 글자가 잘렸다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeaderProfile(
    name: String, title: String?, tier: Int, exp: Int, nextExp: Int?, modifier: Modifier = Modifier,
    /** 흰 카드 위에 놓일 때 (SCR-00 Carry-over): 칭호 칩이 묻히지 않게 옅은 회색으로 */
    onCard: Boolean = false,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s8),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = KagomType.Title32, color = KagomColor.Text.primary, maxLines = 1)
        // equippedTitle (Figma 6:1711): 높이 36, 좌우 12, 글자 16/24 Bold
        if (title != null) Box(
            Modifier.height(36.dp).background(if (onCard) KagomColor.Surface.subtle else KagomColor.Surface.card, RoundedCornerShape(KagomRadius.full))
                .padding(horizontal = KagomSpace.s12),
            contentAlignment = Alignment.Center,
        ) { Text(title, style = KagomType.Body16Strong, color = KagomColor.Text.primary, maxLines = 1) }
        TierEmblem(tier)
        // Pleos 글자 단계(20px)에서 "EXP 1250 / 2000"이 한 줄에 들어가는 폭 (Figma 160은 14px 기준)
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (nextExp != null) "EXP $exp / $nextExp" else "EXP $exp · MAX", style = KagomType.Caption14Strong, color = KagomColor.Text.secondary, maxLines = 1)
            ProgressBar(if (nextExp != null) exp.toFloat() / nextExp else 1f, Modifier.fillMaxWidth(), height = 6.dp)
        }
    }
}

/**
 * 홈 머리 (2026-11-13 요청, 디자인에 없는 배치): 칭호는 곰 아래 번호판이 보여 주므로 빼고,
 * 첫 줄에 이름과 레벨, 둘째 줄에 EXP를 넓게 둔다. [align]은 Full(가운데)·Split(왼쪽)에 맞춘다.
 */
@Composable
fun HomeHeader(
    name: String, tier: Int, exp: Int, nextExp: Int?, modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KagomSpace.s8), horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(name, style = KagomType.Title32, color = KagomColor.Text.primary, maxLines = 1)
            TierEmblem(tier)
        }
        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (nextExp != null) "EXP $exp / $nextExp" else "EXP $exp · MAX", style = KagomType.Caption14Strong, color = KagomColor.Text.secondary, maxLines = 1)
            ProgressBar(if (nextExp != null) exp.toFloat() / nextExp else 1f, Modifier.fillMaxWidth(), height = 6.dp)
        }
    }
}

// ───────────── 홈 패널 ─────────────

/** vehicleStatusRow (Label · Value · hasBadge). 59px + 하단 1px 구분선 */
@Composable
fun VehicleStatusRow(row: StatusRow, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = KagomSpace.s16),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.label, style = KagomType.Body18, color = KagomColor.Text.secondary, modifier = Modifier.weight(1f))
            Text(row.value, style = KagomType.Body18Strong, color = if (row.unavailable) KagomColor.Text.tertiary else KagomColor.Text.primary)
            if (row.badge != null) {
                Spacer(Modifier.width(KagomSpace.s12))
                StatusBadge(row.badge, row.badgeLabel ?: row.badge.label)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KagomColor.Border.default))
    }
}

/** quickMaintenanceCard (OilRemaining · Inspection · Hint · CTA → POP-05) */
@Composable
fun QuickMaintenanceCard(
    oilRemaining: String?,
    oilRatio: Float,
    inspection: String,
    inspectionRatio: Float,
    issues: List<com.example.roro.core.StatusBadge>,
    hint: String,
    hasAlert: Boolean,
    ctaEnabled: Boolean,
    onCta: () -> Unit,
    onBlockedCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().background(KagomColor.Surface.mintSoft, RoundedCornerShape(KagomRadius.r24)).padding(KagomSpace.s24),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("빠른 정비", style = KagomType.Heading20, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
            if (hint.isNotEmpty()) Text(hint, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
        }
        if (issues.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            issues.forEach { issue ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(issue.label, style = KagomType.Body16, color = KagomColor.Text.primary, modifier = Modifier.weight(1f))
                    val severity = issue.severity
                    StatusBadge(severity, if (severity == Severity.URGENT) "긴급" else "점검 필요")
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(KagomColor.Border.divider))
        }
        if (oilRemaining != null) Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            LabelValue("엔진오일 교체까지", oilRemaining)
            ProgressBar(oilRatio, Modifier.fillMaxWidth(), height = 6.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            LabelValue("1만 km 셀프 체크", inspection)
            ProgressBar(if (hasAlert) 1f else inspectionRatio, Modifier.fillMaxWidth(), height = 6.dp)
        }
        Box {
            IviButton("정비 시트 열기", onCta, enabled = ctaEnabled, type = IviButtonType.Secondary, size = IviButtonSize.Medium)
            if (!ctaEnabled) Box(
                Modifier.matchParentSize().clickable(role = Role.Button, onClick = onBlockedCta),
            )
            if (hasAlert) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(28.dp)
                    .background(KagomColor.Severity.fg(Severity.URGENT), CircleShape)
                    .border(2.dp, KagomColor.Surface.card, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = KagomType.Caption14Strong, color = KagomColor.Text.inverse)
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = KagomType.Body16, color = KagomColor.Text.secondary, modifier = Modifier.weight(1f))
        Text(value, style = KagomType.Body16Strong, color = KagomColor.Text.primary)
    }
}

/** driveLockBanner (Title · Body): gear≠P 안내 */
@Composable
fun DriveLockBanner(title: String, body: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().background(KagomColor.Surface.dark, RoundedCornerShape(40.dp))
            .padding(horizontal = KagomSpace.s28, vertical = if (body == null) 24.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s8),
    ) {
        Text(title, style = if (body == null) KagomType.Heading22 else KagomType.Heading22, color = KagomColor.Text.inverse)
        if (body != null) Text(body, style = KagomType.Body18, color = KagomColor.Text.inverse)
    }
}

// ───────────── 온보딩 ─────────────

/** stepDots (Step=1|2|3): 현재 단계는 28×8 막대, 지난 단계는 채운 점 */
@Composable
fun StepDots(step: Int, total: Int = 3, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val color by animateColorAsState(if (i <= step) KagomColor.Progress.fill else KagomColor.Border.divider, tween(KagomMotion.SCREEN), label = "dot")
            val width by animateDpAsState(if (i == step) 28.dp else 8.dp, tween(KagomMotion.SCREEN), label = "dotWidth")
            Box(Modifier.size(width, 8.dp).background(color, RoundedCornerShape(KagomRadius.full)))
        }
    }
}

/** questionCard (State=Default|Selected · Key · Choice · Tag). 키 칩 44px */
@Composable
fun QuestionCard(key: String, choice: String, tag: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) KagomColor.Surface.mintSoft else KagomColor.Surface.card, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) KagomColor.Progress.fill else KagomColor.Border.default, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = KagomSpace.s36, vertical = KagomSpace.s16),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(44.dp).background(if (selected) KagomColor.Progress.fill else KagomColor.Surface.subtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(key, style = KagomType.Body18Strong, color = if (selected) KagomColor.Text.inverse else KagomColor.Text.secondary) }
        Spacer(Modifier.height(KagomSpace.s16))
        Text(choice, style = KagomType.Title28, color = KagomColor.Text.primary)
        Spacer(Modifier.height(KagomSpace.s16))
        Text(tag, style = KagomType.Body16, color = KagomColor.Text.tertiary)
    }
}

// ───────────── 도전과제 ─────────────

enum class QuestCardSize { Wide, Compact }

private val QUEST_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/**
 * questCard (Figma 6:2102 / 7:2102, 11/10 개편): 아이콘 칸 + 이름·조건·EXP + 오른쪽에 달성 날짜 또는 진행 숫자·바.
 * 보상 미리보기(? 또는 아이템)는 카드에서 빠졌다 (보상은 POP-02에서 공개).
 * [doneAt] = 달성 시각(ms). 이 기록이 생기기 전에 달성한 과제는 null → 날짜 없이 "달성"만.
 * 단발형이 진행 중일 때의 모습은 Figma에 없어서 누적형과 같이 숫자·바로 보여 준다.
 */
@Composable
fun QuestCard(
    p: QuestProgress,
    size: QuestCardSize,
    modifier: Modifier = Modifier,
    doneAt: Long? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    Row(
        modifier.fillMaxWidth().height(140.dp)
            .clip(shape).background(KagomColor.Surface.card)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = KagomSpace.s28),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KagomSpace.s20),
    ) {
        Box(
            Modifier.size(64.dp).background(if (p.achieved) KagomColor.Surface.mintSoft else KagomColor.Surface.subtle, RoundedCornerShape(KagomRadius.r16)),
            contentAlignment = Alignment.Center,
        ) { QuestIconGlyph(p.quest.icon, Modifier.size(40.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KagomSpace.s4)) {
            Text(p.quest.name, style = KagomType.Heading24, color = KagomColor.Text.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.quest.title, style = KagomType.Body16, color = KagomColor.Text.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("+${p.quest.exp} EXP", style = KagomType.Caption14Strong, color = KagomColor.Text.accent)
        }
        if (p.achieved) {
            val at = doneAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
            Text(if (at != null) "${at.format(QUEST_DATE)} 달성" else "달성", style = KagomType.Body18Strong, color = KagomColor.Text.primary, maxLines = 1)
        } else {
            Column(
                Modifier.width(if (size == QuestCardSize.Wide) 480.dp else 160.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(KagomSpace.s8),
            ) {
                Text(p.countText, style = KagomType.Body18Strong, color = KagomColor.Text.primary, maxLines = 1)
                ProgressBar(p.ratio, Modifier.fillMaxWidth())
            }
        }
    }
}

/** filterChip (Figma 193:4488): 높이 40, 좌우 20. 고른 것은 검정 바탕 */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(KagomRadius.full)
    val bg by animateColorAsState(if (selected) KagomColor.Surface.inverse else KagomColor.Surface.card, tween(KagomMotion.FAST), label = "chipBg")
    Box(
        modifier.height(KagomSize.chipHeight).clip(shape).background(bg, shape)
            .then(if (selected) Modifier else Modifier.border(1.dp, KagomColor.Border.default, shape))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = KagomSpace.s20),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = KagomType.Body16Strong, color = if (selected) KagomColor.Text.inverse else KagomColor.Text.secondary, maxLines = 1) }
}

// ───────────── 치장 ─────────────

enum class CellState { Owned, Equipped, Locked }

/**
 * itemGridCell (State=Owned|Equipped|Locked · Mark/Name/Source/Caption). 192×252.
 * Figma는 180×232인데 Pleos 글자 단계(이름 24px, 설명 20px)로는 이름이 잘려서 칸을 넓히고 이름을 두 줄까지 쓴다.
 * 192 = Split(600)에 세 칸, 252 = Full 1080 높이에 세 줄이 들어가는 가장 큰 값이다 (썸네일은 88 → 80)
 */
@Composable
fun ItemGridCell(item: Item, state: CellState, caption: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val highlighted = selected || state == CellState.Equipped
    val locked = state == CellState.Locked
    val borderColor by animateColorAsState(if (highlighted) KagomColor.Progress.fill else KagomColor.Border.default, tween(KagomMotion.FAST), label = "cellBorder")
    Column(
        modifier
            .size(192.dp, 252.dp)
            .clip(shape)
            .background(KagomColor.Surface.card, shape)
            .border(if (highlighted) 2.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = KagomSpace.s12, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemThumb(if (locked) null else item.mark, 80.dp, locked, itemId = item.id)
        Spacer(Modifier.height(10.dp))
        Text(item.name, style = KagomType.Body16Strong, color = if (locked) KagomColor.Text.secondary else KagomColor.Text.primary,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Chip(
            item.source.chip,
            if (locked) KagomColor.Severity.bg(Severity.RECOMMEND) else KagomColor.Surface.subtle,
            if (locked) KagomColor.Severity.fg(Severity.RECOMMEND) else KagomColor.Text.secondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(caption, style = KagomType.Caption14, color = if (locked) KagomColor.Text.secondary else KagomColor.Text.tertiary,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** itemUnlockPanel (ItemName/Category/Source/Condition/ProgressValue/Hint · CTA). Full 320 패널 / Split 시트 */
@Composable
fun ItemUnlockPanel(
    item: Item,
    sourceLabel: String,
    progressValue: String,
    progressRatio: Float,
    hint: String,
    cta: String,
    ctaEnabled: Boolean,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    Column(
        modifier
            .then(if (framed) Modifier.background(KagomColor.Surface.card, shape).border(1.dp, KagomColor.Border.default, shape).padding(KagomSpace.s24) else Modifier),
        verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
            Box(Modifier.size(44.dp).background(KagomColor.Surface.dark, CircleShape), contentAlignment = Alignment.Center) {
                LockGlyph(Modifier.size(20.dp), KagomColor.Text.inverse)
            }
            Column {
                Text("획득 안내", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
                Text(item.name, style = KagomType.Heading20, color = KagomColor.Text.primary)
                Text("${item.category.noun} · ${item.id}", style = KagomType.Caption14, color = KagomColor.Text.tertiary)
            }
        }
        Chip(sourceLabel, KagomColor.Severity.bg(Severity.RECOMMEND), KagomColor.Severity.fg(Severity.RECOMMEND), height = 32.dp)
        Text(item.guide, style = KagomType.Body16, color = KagomColor.Text.primary)
        Column(verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
            Row {
                Text("진행 상황", style = KagomType.Caption14, color = KagomColor.Text.tertiary, modifier = Modifier.weight(1f))
                Text(progressValue, style = KagomType.Caption14Strong, color = KagomColor.Text.primary)
            }
            ProgressBar(maxOf(progressRatio, 0.02f), Modifier.fillMaxWidth())
        }
        Text(hint, style = KagomType.Caption14, color = KagomColor.Text.tertiary)
        IviButton(cta, onCta, Modifier.fillMaxWidth(), enabled = ctaEnabled)
    }
}

/** 잠긴 아이템 출처 문구 */
fun Source.unlockLabel() = when (this) {
    Source.LANDMARK -> "랜드마크 해금"; Source.QUEST -> "도전과제 보상"; Source.MAINTENANCE -> "정비 보상"; Source.DEFAULT -> "기본 제공"
}

// ───────────── 랜드마크 ─────────────

enum class PinState { Locked, Unlocking, Unlocked }

/** landmarkPin (State=Locked|Unlocking|Unlocked). 48px */
@Composable
fun LandmarkPin(state: PinState, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val pulse = rememberInfiniteTransition(label = "pin")
    val s by pulse.animateFloat(1f, if (state == PinState.Unlocking) 1.15f else 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")
    val bg = when (state) {
        PinState.Locked -> KagomColor.Surface.dark.copy(alpha = 0.85f)
        PinState.Unlocking -> KagomColor.Progress.fill
        PinState.Unlocked -> KagomColor.Accent.positive
    }
    Box(modifier.size(48.dp).scale(s).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp).shadow(4.dp, CircleShape).background(bg, CircleShape))
        Box(Modifier.size(32.dp).border(2.dp, KagomColor.Text.inverse, CircleShape), contentAlignment = Alignment.Center) {
            when (state) {
                PinState.Locked -> LockGlyph(Modifier.size(16.dp), KagomColor.Text.inverse)
                PinState.Unlocking -> Box(Modifier.size(10.dp).background(KagomColor.Text.inverse, CircleShape))
                PinState.Unlocked -> CheckGlyph(Modifier.size(20.dp), KagomColor.Text.inverse, 2.5.dp)
            }
        }
        if (state == PinState.Unlocked) Box(
            Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 2.dp).size(12.dp)
                .background(KagomColor.Accent.tier, CircleShape).border(1.5.dp, KagomColor.Text.inverse, CircleShape),
        )
    }
}

/** landmarkDetailPopup (State=Visited|Locked · Name/Reward/Mark/Status). 440×174 */
/** 잠긴 핀 툴팁 (SCR-05 tooltip): 왼쪽 꼬리가 달린 어두운 말풍선 */
@Composable
fun PinTooltip(title: String, body: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).offset(x = 6.dp).alpha(1f)) {
            androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height); close()
                }, KagomColor.Surface.inverse)
            }
        }
        Column(
            Modifier.background(KagomColor.Surface.inverse, RoundedCornerShape(KagomRadius.r16)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = KagomType.Body16Strong, color = KagomColor.Text.inverse)
            Text(body, style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary)
        }
    }
}

// ───────────── pagination (Figma 139:1864) ─────────────

/**
 * 페이지 넘김: 원형 화살표 64 + 번호 56. [current]는 0부터. 번호는 최대 5개를 현재 페이지 둘레로 보여 준다.
 * 첫 페이지면 이전, 마지막 페이지면 다음이 비활성. 한 페이지뿐이면 부르는 쪽에서 숨긴다 (가이드 v1.2 2.1).
 */
@Composable
fun Pagination(current: Int, count: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
        PageArrow(next = false, enabled = current > 0) { onSelect(current - 1) }
        for (entry in paginationEntries(current, count)) {
            if (entry == null) {
                Box(Modifier.width(24.dp).height(56.dp), contentAlignment = Alignment.Center) {
                    Text("…", style = KagomType.ClosetSmallText, color = KagomColor.Text.tertiary)
                }
                continue
            }
            val i = entry
            val on = i == current
            Box(
                Modifier.size(56.dp).clip(CircleShape)
                    .background(if (on) KagomColor.Surface.inverse else Color.Transparent)
                    .clickable(enabled = !on, role = Role.Button) { onSelect(i) }
                    .semantics { contentDescription = "${i + 1}페이지" + if (on) ", 지금 페이지" else "" },
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", style = KagomType.ClosetSmallText, color = if (on) KagomColor.Text.inverse else KagomColor.Text.secondary, maxLines = 1) }
        }
        PageArrow(next = true, enabled = current < count - 1) { onSelect(current + 1) }
    }
}

@Composable
private fun PageArrow(next: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape)
            .background(if (enabled) KagomColor.Surface.card else KagomColor.Surface.subtle, CircleShape)
            .border(1.dp, if (enabled) KagomColor.Border.default else KagomColor.Border.disabled, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (next) "다음 페이지" else "이전 페이지" },
        contentAlignment = Alignment.Center,
    ) {
        val color = if (enabled) KagomColor.Text.primary else KagomColor.Text.disabled
        androidx.compose.foundation.Canvas(Modifier.size(28.dp)) {
            val w = size.width; val h = size.height
            val (x0, x1) = if (next) w * 0.32f to w * 0.68f else w * 0.68f to w * 0.32f
            val stroke = 2.5.dp.toPx()
            drawLine(color, androidx.compose.ui.geometry.Offset(x0, h * 0.15f), androidx.compose.ui.geometry.Offset(x1, h * 0.5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(x1, h * 0.5f), androidx.compose.ui.geometry.Offset(x0, h * 0.85f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
