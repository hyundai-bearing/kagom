package com.example.roro.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.roro.R
import com.example.roro.ui.components.CloseGlyph
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomRadius
import com.example.roro.ui.theme.KagomSize
import com.example.roro.ui.theme.KagomSpace
import com.example.roro.ui.theme.KagomType
import java.time.LocalDate
import kotlinx.coroutines.launch

private data class LandmarkPhoto(
    val caption: String,
    @DrawableRes val image: Int,
    @DrawableRes val thumbnail: Int,
    val author: String,
    val sourceFile: String,
    val takenOn: LocalDate,
    val licenseVersion: String = "4.0",
) {
    val sourceUrl: String get() = "https://commons.wikimedia.org/wiki/File:${sourceFile.replace(' ', '_')}"
    val licenseUrl: String get() = "https://creativecommons.org/licenses/by-sa/$licenseVersion/"
}

private val photosByLandmark = mapOf(
    "LM-GN-01" to listOf(
        LandmarkPhoto(
            "정동진 해돋이", R.drawable.landmark_jeongdongjin_sunrise, R.drawable.landmark_jeongdongjin_sunrise_thumb,
            "Loewelad", "Jeongdongjin Beach.jpg", LocalDate.of(2008, 3, 22), licenseVersion = "3.0",
        ),
        LandmarkPhoto(
            "정동진역 옛 역사", R.drawable.landmark_jeongdongjin_station, R.drawable.landmark_jeongdongjin_station_thumb,
            "Rsa", "Jeongdongjin Station old building 20230430.jpg", LocalDate.of(2023, 4, 30), licenseVersion = "3.0",
        ),
        LandmarkPhoto(
            "정동진역과 열차", R.drawable.landmark_jeongdongjin_train, R.drawable.landmark_jeongdongjin_train_thumb,
            "Smiley.toerist", "Jeongdongjin station and train.jpg", LocalDate.of(2014, 10, 12),
        ),
    ),
    "LM-GN-02" to listOf(
        LandmarkPhoto(
            "커피거리 입구", R.drawable.landmark_anmok_street, R.drawable.landmark_anmok_street_thumb,
            "Altostratus", "Gangneung coffee street entrance.jpg", LocalDate.of(2017, 2, 14),
        ),
        LandmarkPhoto(
            "강릉 커피거리", R.drawable.landmark_anmok_cafes, R.drawable.landmark_anmok_cafes_thumb,
            "Altostratus", "Gangneung coffee street.jpg", LocalDate.of(2017, 2, 14),
        ),
        LandmarkPhoto(
            "안목해변", R.drawable.landmark_anmok_beach, R.drawable.landmark_anmok_beach_thumb,
            "Mobius6", "Anmok Beach 20220430 005.jpg", LocalDate.of(2022, 4, 30),
        ),
    ),
    "LM-GN-03" to listOf(
        LandmarkPhoto(
            "주문진항 풍경", R.drawable.landmark_jumunjin_harbor, R.drawable.landmark_jumunjin_harbor_thumb,
            "Mobius6", "Jumunjin Harbor 20220501 001.jpg", LocalDate.of(2022, 5, 1),
        ),
        LandmarkPhoto(
            "주문진항의 배", R.drawable.landmark_jumunjin_boats, R.drawable.landmark_jumunjin_boats_thumb,
            "Mobius6", "Jumunjin Harbor 20220501 003.jpg", LocalDate.of(2022, 5, 1),
        ),
        LandmarkPhoto(
            "주문진항 바닷가", R.drawable.landmark_jumunjin_waterfront, R.drawable.landmark_jumunjin_waterfront_thumb,
            "Mobius6", "Jumunjin Harbor 20220501 006.jpg", LocalDate.of(2022, 5, 1),
        ),
    ),
)

internal fun hasLandmarkPhotos(landmarkId: String): Boolean = landmarkId in photosByLandmark

@Composable
internal fun LandmarkPhotoSection(landmarkId: String, landmarkTitle: String, split: Boolean) {
    val photos = photosByLandmark[landmarkId] ?: return
    var openIndex by rememberSaveable(landmarkId) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KagomColor.Border.default))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("이곳의 기록", style = KagomType.Heading20, color = KagomColor.Text.primary)
            Text("${photos.size}장", style = KagomType.Caption14, color = KagomColor.Text.secondary)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val thumbnailWidth = (maxWidth - KagomSpace.s8 * 2) / 3
            val thumbnailHeight = (thumbnailWidth / 1.4f).let { if (split) it.coerceAtMost(KagomSize.touchMin) else it }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s8)) {
                itemsIndexed(photos, key = { _, photo -> photo.image }) { index, photo ->
                    Image(
                        painterResource(photo.thumbnail),
                        contentDescription = photo.caption,
                        modifier = Modifier.width(thumbnailWidth).height(thumbnailHeight)
                            .clip(RoundedCornerShape(KagomRadius.r12))
                            .clickable(role = Role.Button, onClickLabel = "사진 크게 보기") { openIndex = index },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }

    openIndex?.let { index ->
        LandmarkPhotoViewer(landmarkTitle, photos, index, onClose = { openIndex = null })
    }
}

@Composable
private fun LandmarkPhotoViewer(
    landmarkTitle: String,
    photos: List<LandmarkPhoto>,
    initialIndex: Int,
    onClose: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val photo = photos[page]
    val uriHandler = LocalUriHandler.current
    val imageShape = RoundedCornerShape(KagomRadius.r20)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(KagomColor.Surface.inverse).padding(KagomSpace.s24),
            verticalArrangement = Arrangement.spacedBy(KagomSpace.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KagomSpace.s16),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("이곳의 기록", style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary)
                    Text(landmarkTitle, style = KagomType.Title28, color = KagomColor.Text.inverse)
                }
                Box(
                    Modifier.size(KagomSize.touchMin).clip(CircleShape).background(KagomColor.Surface.dark)
                        .clickable(role = Role.Button, onClickLabel = "사진 닫기", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    CloseGlyph(Modifier.size(24.dp), KagomColor.Text.inverse)
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                    val picture = photos[index]
                    val painter = painterResource(picture.image)
                    val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = KagomSize.touchMin),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter, picture.caption, Modifier.aspectRatio(ratio).clip(imageShape),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                if (photos.size > 1) {
                    PhotoArrow(previous = true, enabled = page > 0, Modifier.align(Alignment.CenterStart)) {
                        scope.launch { pager.animateScrollToPage(page - 1) }
                    }
                    PhotoArrow(previous = false, enabled = page < photos.lastIndex, Modifier.align(Alignment.CenterEnd)) {
                        scope.launch { pager.animateScrollToPage(page + 1) }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(photo.caption, style = KagomType.Heading20, color = KagomColor.Text.inverse)
                Text("${page + 1} / ${photos.size}", style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary)
            }
            Text(
                "${photo.takenOn.format(detailDateFormat)} 촬영",
                style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(KagomSpace.s12)) {
                Text(
                    "사진 · ${photo.author} (크기 조정)",
                    Modifier.clickable(role = Role.Button, onClickLabel = "사진 원본 출처 열기") {
                        runCatching { uriHandler.openUri(photo.sourceUrl) }
                    },
                    style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary,
                    textDecoration = TextDecoration.Underline,
                )
                Text(
                    "CC BY-SA ${photo.licenseVersion}",
                    Modifier.clickable(role = Role.Button, onClickLabel = "사진 라이선스 열기") {
                        runCatching { uriHandler.openUri(photo.licenseUrl) }
                    },
                    style = KagomType.Caption14, color = KagomColor.Text.inverseSecondary,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }
    }
}

@Composable
private fun PhotoArrow(previous: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(KagomSize.touchMin).clip(CircleShape)
            .background(KagomColor.Surface.dark.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(
                enabled = enabled, role = Role.Button,
                onClickLabel = if (previous) "이전 사진" else "다음 사진", onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.map_chevron), null,
            Modifier.size(18.dp).rotate(if (previous) 180f else 0f),
            colorFilter = ColorFilter.tint(if (enabled) KagomColor.Text.inverse else KagomColor.Text.inverseSecondary),
        )
    }
}
