package com.example.roro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.roro.R
import com.example.roro.core.Items

/** 사용자가 제공한 4색 시트에서 현재 스킨의 번호판 영역을 표시한다. */
@Composable
fun ClosetTitlePlate(titleId: String, skin: String, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val darkSheet = skin in setOf("P04", "P06", "P07", "P08")
    val row = when (skin) {
        "P05", "P04" -> 0
        "P01", "P06" -> 1
        "P02", "P07" -> 2
        else -> 3
    }
    val resource = when (titleId) {
        "TTL-00" -> if (darkSheet) R.drawable.title_owner_dark else R.drawable.title_owner_light
        "TTL-01" -> if (darkSheet) R.drawable.title_coast_dark else R.drawable.title_coast_light
        "TTL-02" -> if (darkSheet) R.drawable.title_caretaker_dark else R.drawable.title_caretaker_light
        else -> return
    }
    val bitmap = ImageBitmap.imageResource(resource)
    val top = if (darkSheet) 70 + row * 309 else 82 + row * 303
    val left = if (darkSheet) 106 else 112
    val width = if (darkSheet) 988 else 974
    val height = if (darkSheet) 278 else 270
    Canvas(modifier.aspectRatio(width.toFloat() / height).clip(RoundedCornerShape(16.dp))
        .semantics { contentDescription = Items.byId(titleId)?.name ?: "칭호" }) {
        drawImage(bitmap, srcOffset = IntOffset(left, top), srcSize = IntSize(width, height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()), colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium)
    }
}

fun hasClosetTitlePlate(titleId: String?): Boolean = titleId in setOf("TTL-00", "TTL-01", "TTL-02")
