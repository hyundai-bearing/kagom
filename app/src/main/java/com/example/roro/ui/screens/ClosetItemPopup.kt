package com.example.roro.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.roro.R
import com.example.roro.core.Category
import com.example.roro.core.Examples
import com.example.roro.core.Expression
import com.example.roro.core.Item
import com.example.roro.core.Landmarks
import com.example.roro.data.AppData
import com.example.roro.data.Profile
import com.example.roro.ui.components.AssetImage
import com.example.roro.ui.components.BackgroundArt
import com.example.roro.ui.components.IviButton
import com.example.roro.ui.components.IviButtonSize
import com.example.roro.ui.components.IviButtonType
import com.example.roro.ui.components.KagomRenders
import com.example.roro.ui.components.LockGlyph
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomClosetPalette
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Figma 2021:5094 / 2071:7596. 아이템과 카곰은 실제 데이터의 렌더를 쓴다. */
@Composable
internal fun ClosetItemPopup(
    item: Item,
    locked: Boolean,
    profile: Profile,
    data: AppData,
    split: Boolean,
    targetLabel: String?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
) {
    val shape = RoundedCornerShape(if (split) KagomRadius.r24 else 44.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(KagomColor.Overlay.dim)
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() })
        Column(
            Modifier.padding(horizontal = if (split) 20.dp else 32.dp)
                .widthIn(max = 1010.dp).fillMaxWidth().shadow(24.dp, shape).clip(shape)
                .background(KagomColor.Surface.card),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1010f / 342f)) {
                val unit = maxWidth / 1010f
                Image(painterResource(R.drawable.closet_display_stage), contentDescription = null,
                    contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                val context = LocalContext.current
                val thumb = KagomRenders.thumbPath(item.id)
                Box(Modifier.offset(x = unit * 305, y = unit * 48).size(unit * 400, unit * 235), contentAlignment = Alignment.Center) {
                    if (item.category == Category.BACKGROUND) BackgroundArt(item.id,
                        Modifier.fillMaxHeight().aspectRatio(4f / 3f).clip(RoundedCornerShape(unit * 16)), if (locked) lockedItemFilter else null)
                    else if (KagomRenders.exists(context, thumb)) AssetImage(thumb, Modifier.fillMaxSize(), colorFilter = if (locked) lockedItemFilter else null)
                    else Text(item.mark, style = KagomType.PleosTitleSmallStrong, color = KagomColor.Text.primary)
                    if (locked) LockGlyph(Modifier.size(unit * 44), KagomColor.Text.inverse)
                }
                AssetImage(KagomRenders.basePath(profile.skin, Expression.IDLE),
                    Modifier.offset(x = unit * 660, y = unit * 65).size(unit * 170, unit * 264))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = if (split) 24.dp else 46.dp, vertical = if (split) 20.dp else 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(item.name, style = if (split) KagomType.PleosTitleSmallStrong else KagomType.ClosetPopupTitle, color = KagomColor.Text.primary, modifier = Modifier.weight(1f, fill = false))
                    Row(Modifier.height(if (split) 42.dp else 48.dp)
                        .background(if (locked) KagomColor.Closet.locked else KagomClosetPalette.of(profile.skin).ownedBackground, RoundedCornerShape(KagomRadius.r24))
                        .padding(horizontal = if (split) 14.dp else 20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val statusText = if (locked) KagomColor.Closet.lockedText else KagomClosetPalette.of(profile.skin).ownedText
                        if (locked) LockGlyph(Modifier.size(if (split) 16.dp else 20.dp), statusText)
                        Text(if (locked) "잠금" else "보유중", style = if (locked) KagomType.ClosetSmallText else KagomType.ClosetPopupBadge, color = statusText, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(if (split) 8.dp else 20.dp))
                Text(popupDescription(item, locked, data), style = KagomType.ClosetPopupBody, color = KagomColor.Text.secondary)
                Spacer(Modifier.height(if (split) 20.dp else 40.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (targetLabel != null) IviButton(targetLabel, onNavigate, Modifier.weight(1f), size = if (split) IviButtonSize.Medium else IviButtonSize.Large)
                    IviButton("닫기", onDismiss, if (targetLabel == null) Modifier.fillMaxWidth() else Modifier.width(if (split) 150.dp else 234.dp), type = IviButtonType.Primary, size = if (split) IviButtonSize.Medium else IviButtonSize.Large)
                }
            }
        }
    }
}

/** 잠긴 아이템 이미지의 색만 어둡고 차분하게 바꾼다. */
private val lockedItemFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.25f, 0.50f, 0.08f, 0f, 0f,
    0.25f, 0.50f, 0.08f, 0f, 0f,
    0.25f, 0.50f, 0.08f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)))

internal fun popupDescription(item: Item, locked: Boolean, data: AppData): String {
    if (Examples.isExample(item.id)) return "시연용 예시 아이템이에요."
    if (locked) return item.condition.ifBlank { item.guide }
    val landmark = item.sourceId?.let { Landmarks.byId(it) }
    val visit = landmark?.let { lm -> data.visits.filter { it.landmarkId == lm.id }.minByOrNull { it.day } }
    val date = visit?.let { LocalDate.ofEpochDay(it.day) }
        ?: data.questDoneAt[item.sourceId]?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    if (date == null) return "보유 중인 아이템이에요."
    val formatted = date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
    return if (landmark != null) "${formatted}에 ${landmark.name}에 방문해서 받았어요" else "${formatted}에 받았어요"
}
