package com.example.roro.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.example.roro.R
import com.example.roro.core.Category
import com.example.roro.core.Items
import com.example.roro.ui.theme.KagomColor
import com.example.roro.ui.theme.KagomType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.min

/**
 * 오늘 (epoch day). 칭호 번호판이 날짜를 쓴다. 시연 패널의 날짜 넘기기가 반영되도록 앱 맨 위(KagomApp)에서
 * KagomController.today()와 같은 값을 준다. 미리보기 등 바깥에서는 실제 오늘이다.
 */
val LocalToday = staticCompositionLocalOf { LocalDate.now().toEpochDay() }

/**
 * 번호판 바탕 한 장 (tools/plates/build_plates.py가 사용자 제공 시트에서 글자를 지워 만든 그림).
 * 좌표는 그 그림의 px이다. 날짜·이름 자리와 글자 색은 원래 그림의 글자에서 잰 값이다 (스크립트 출력).
 */
private class PlateArt(
    @DrawableRes val res: Int,
    val width: Int, val height: Int,
    val ink: Color,
    val date: Offset, val name: Offset,
    /** 이름이 이보다 넓으면 줄여서 넣는다 (곰 얼굴·세로줄과 오른쪽 테두리 사이) */
    val nameMaxWidth: Float,
)

private val LIGHT_DATE = Offset(590f, 62f)
private val LIGHT_NAME = Offset(612f, 160f)
private val DARK_DATE = Offset(616f, 62f)
private val DARK_NAME = Offset(623f, 161f)

private fun light(@DrawableRes res: Int) = PlateArt(res, 974, 270, KagomColor.TitlePlate.ink, LIGHT_DATE, LIGHT_NAME, 600f)
private fun dark(@DrawableRes res: Int, ink: Color) = PlateArt(res, 988, 278, ink, DARK_DATE, DARK_NAME, 610f)

/** 스킨별 번호판. 밝은 시트 = P05·P01·P02·P03, 어두운 시트 = P04·P06·P07·P08 (사용자 제공 시트의 위에서부터) */
private fun plateArt(skin: String): PlateArt = when (skin) {
    "P05" -> light(R.drawable.title_plate_p05)
    "P01" -> light(R.drawable.title_plate_p01)
    "P02" -> light(R.drawable.title_plate_p02)
    "P04" -> dark(R.drawable.title_plate_p04, KagomColor.TitlePlate.inkOnCharcoal)
    "P06" -> dark(R.drawable.title_plate_p06, KagomColor.TitlePlate.inkChocolate)
    "P07" -> dark(R.drawable.title_plate_p07, KagomColor.TitlePlate.inkTeal)
    "P08" -> dark(R.drawable.title_plate_p08, KagomColor.TitlePlate.inkRose)
    else -> light(R.drawable.title_plate_p03)
}

private val PLATE_DATE = DateTimeFormatter.ofPattern("yyMMdd")

/**
 * 번호판: 스킨 색 바탕 위에 날짜(yyMMdd)와 이름을 쓴다. 칭호 번호판(홈·옷장)과 달성한 도전과제 상세가 같이 쓴다.
 * 글자는 번호판 크기에 맞춰 같이 커지고 작아진다 (그림 px 기준 글자 크기 = KagomType.PlateDate·PlateName).
 * [colorFilter]는 잠긴 칭호를 탈색할 때 바탕과 글자에 같이 건다.
 */
@Composable
fun TitlePlate(name: String, epochDay: Long, skin: String, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val art = plateArt(skin)
    val bitmap = ImageBitmap.imageResource(art.res)
    val measurer = rememberTextMeasurer()
    val date = remember(epochDay) { "—  " + LocalDate.ofEpochDay(epochDay).format(PLATE_DATE) + "  —" }
    val dateLayout = remember(date, art.ink) { measurer.measure(date, KagomType.PlateDate.copy(color = art.ink), maxLines = 1, softWrap = false) }
    val nameLayout = remember(name, art.ink) { measurer.measure(name, KagomType.PlateName.copy(color = art.ink), maxLines = 1, softWrap = false) }
    val layer = remember(colorFilter) { colorFilter?.let { Paint().apply { this.colorFilter = it } } }
    // 그림의 모서리 반지름은 번호판 높이의 약 15%다: 고정 dp로 깎으면 크게 그릴 때 둥근 모서리 바깥의 시트 바탕이 보인다
    Canvas(modifier.aspectRatio(art.width.toFloat() / art.height).clip(RoundedCornerShape(15))
        .semantics { contentDescription = name }) {
        val k = size.width / art.width
        val draw: DrawScope.() -> Unit = {
            drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
            plateText(dateLayout, KagomType.PlateDate, art.date, Float.MAX_VALUE, k)
            plateText(nameLayout, KagomType.PlateName, art.name, art.nameMaxWidth, k)
        }
        if (layer == null) draw() else drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), layer)
            draw()
            canvas.restore()
        }
    }
}

/** 그림 px 좌표 [center]에 글자를 가운데 맞춰 쓴다. 글자 크기(sp)를 그림 px로 보고 번호판 배율 [k]만큼 키운다 */
private fun DrawScope.plateText(layout: TextLayoutResult, style: TextStyle, center: Offset, maxWidth: Float, k: Float) {
    val toArt = style.fontSize.value / style.fontSize.toPx()
    val fit = min(1f, maxWidth / (layout.size.width * toArt))
    val s = k * toArt * fit
    withTransform({
        translate(center.x * k - layout.size.width * s / 2f, center.y * k - layout.size.height * s / 2f)
        scale(s, s, Offset.Zero)
    }) { drawText(layout) }
}

/** 칭호 번호판 (홈·옷장): 장착하거나 고른 칭호의 이름과 오늘 날짜 */
@Composable
fun ClosetTitlePlate(titleId: String, skin: String, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val name = Items.byId(titleId)?.name ?: return
    TitlePlate(name, LocalToday.current, skin, modifier, colorFilter)
}

/** 번호판으로 그릴 수 있는 칭호인지. 바탕에 글자를 코드로 쓰므로 등록된 칭호는 모두 된다 (시연용 EX- 칭호는 빼고) */
fun hasClosetTitlePlate(titleId: String?): Boolean = Items.byId(titleId)?.category == Category.TITLE
