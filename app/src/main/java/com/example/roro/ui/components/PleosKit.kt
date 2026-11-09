package com.example.roro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomType
import com.example.roro.ui.theme.PleosKit

/*
 * Pleos ivi_* 플랫폼 공용 UI 키트 대응 (가이드 4.4).
 * 플랫폼 SDK 컴포넌트가 없어 Figma Components 페이지의 치수·색으로 그대로 구현했다.
 * SDK 컴포넌트가 생기면 이 파일만 바꾸면 된다.
 */

/** ivi_basic_button Type. 주의: 이 키트에서 Secondary가 어두운 강조 버튼 */
enum class IviButtonType { Primary, Secondary }

/** ivi_basic_button Size (buttons/basic/medium, buttons/basic/large 변수) */
enum class IviButtonSize(val height: Dp, val radius: Dp, val padH: Dp, val minWidth: Dp) {
    Medium(80.dp, 24.dp, 32.dp, 120.dp),
    Large(100.dp, 32.dp, 40.dp, 140.dp),
}

/** ivi_basic_button State=Enabled|Pressed|Disabled */
@Composable
fun IviButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    type: IviButtonType = IviButtonType.Secondary,
    size: IviButtonSize = IviButtonSize.Medium,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(size.radius)
    val bg = when {
        !enabled -> KagomColor.Surface.disabled
        type == IviButtonType.Secondary -> if (pressed) PleosKit.buttonSecondaryPressed else PleosKit.buttonSecondaryBg
        else -> if (pressed) PleosKit.buttonPrimaryPressed else PleosKit.buttonPrimaryBg
    }
    val fg = when {
        !enabled -> KagomColor.Text.disabled
        type == IviButtonType.Secondary -> KagomColor.Text.inverse
        else -> KagomColor.Text.primary
    }
    Box(
        modifier
            .defaultMinSize(minWidth = size.minWidth)
            .height(size.height)
            .clip(shape)
            .background(bg, shape)
            .then(if (!enabled) Modifier.border(2.dp, KagomColor.Border.disabled, shape) else Modifier)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.padH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (size == IviButtonSize.Large) KagomType.PleosTitleSmallStrong else KagomType.PleosBodyLargeStrong,
            color = fg, maxLines = 1, textAlign = TextAlign.Center,
        )
    }
}

/** ivi_text_button: 글자만 있는 버튼 (예: 성향 테스트 "이전") */
@Composable
fun IviTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        label,
        style = KagomType.Body16,
        color = if (enabled) KagomColor.Text.tertiary else KagomColor.Text.disabled,
        modifier = modifier
            .clip(RoundedCornerShape(KagomRadius.r8))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

/**
 * ivi_text_field State=Enabled|Focused|Typing|Entered|Error.
 * 포커스 시 Pleos 시스템 가상 키보드가 뜬다. [enabled] = 기어 P일 때만 true.
 */
@Composable
fun IviTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: Boolean = false,
    height: Dp = 100.dp,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocusChange: (Boolean) -> Unit = {},
    onDone: () -> Unit = {},
) {
    val shape = RoundedCornerShape(KagomRadius.r24)
    val borderColor = when {
        error -> KagomColor.Severity.fg(com.example.roro.core.Severity.URGENT)
        else -> KagomColor.Border.default
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = KagomType.PleosField.copy(color = KagomColor.Text.primary),
        cursorBrush = SolidColor(KagomColor.Text.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .height(height)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(KagomColor.Surface.card, shape)
                    .border(2.dp, borderColor, shape)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = KagomType.PleosField, color = KagomColor.Text.tertiary, maxLines = 1)
                inner()
            }
        },
    )
}

/** ivi_switch (selections/switch/medium: 80×48, knob 32) */
@Composable
fun IviSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) PleosKit.switchOn else PleosKit.switchOff, label = "switch")
    val knobX by animateDpAsState(if (checked) 32.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(80.dp, 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(track)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .offset(x = knobX)
                .size(32.dp)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .background(PleosKit.switchKnob, RoundedCornerShape(8.dp)),
        )
    }
}

/** ivi_checkbox (container 64, 박스 48 r12, 테두리 4) + 라벨 */
@Composable
fun IviCheckbox(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(KagomRadius.r12))
            .clickable(enabled = enabled, role = Role.Checkbox) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .size(48.dp)
                    .background(if (checked) PleosKit.switchOn else KagomColor.Surface.card, shape)
                    .then(if (checked) Modifier else Modifier.border(4.dp, PleosKit.checkboxBorder, shape)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) CheckGlyph(Modifier.size(32.dp), KagomColor.Text.inverse, stroke = 4.dp)
            }
        }
        Text(label, style = KagomType.Body20, color = KagomColor.Text.primary)
    }
}

/**
 * segmented_controls/goast_button (Focused=true가 선택). tabs/solid/medium: 160×64 r16.
 * Split에서는 [itemWidth]를 null로 두면 균등 분할.
 */
@Composable
fun GhostSegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp? = 160.dp,
    gap: Dp = 8.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            val shape = RoundedCornerShape(16.dp)
            Box(
                Modifier
                    .then(if (itemWidth != null) Modifier.size(itemWidth, 64.dp) else Modifier.weight(1f).height(64.dp))
                    .then(if (isSel) Modifier.shadow(12.dp, shape, ambientColor = PleosKit.shadow, spotColor = PleosKit.shadow) else Modifier)
                    .clip(shape)
                    .background(if (isSel) KagomColor.Surface.card else KagomColor.Surface.page.copy(alpha = 0f))
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = KagomType.PleosTitleSmallStrong,
                    color = if (isSel) KagomColor.Text.primary else KagomColor.Text.darkSecondary, maxLines = 1)
            }
        }
    }
}

/** ivi_toast_popup (popup/toast: 패딩 40×24, r16, 최대 792) */
@Composable
fun IviToast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 792.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = PleosKit.shadow, spotColor = PleosKit.shadow)
            .background(PleosKit.toastBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 40.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KagomType.PleosBodySmallStrong, color = KagomColor.Text.inverse,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
