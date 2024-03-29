import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor

fun offs2Idx(offs: IntOffset, width: Int, height: Int): IntOffset {
    return IntOffset(
        floor(offs.x / width.toDouble()).toInt(),
        floor(offs.y / height.toDouble()).toInt()
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InfiniteGrid(
    dimensions: IntOffset,
    show: @Composable (IntOffset, IntOffset, Modifier) -> Unit
) {
    var rowCount by remember { mutableStateOf(2) }
    var columnCount by remember { mutableStateOf(2) }

    var contentHeight by remember { mutableStateOf(dimensions.y) }
    var contentWidth by remember { mutableStateOf(dimensions.x) }

    var topLeftPos by remember { mutableStateOf(IntOffset(0, 0)) }
    val topLeftTileIdx by remember {
        derivedStateOf { offs2Idx(topLeftPos, contentWidth, contentHeight) }
    }

    val contentModifierZoomed = remember {
        derivedStateOf {
            Modifier.size(
                height = contentHeight.dp,
                width = contentWidth.dp
            )
        }
    }

    val contentBuilder = @Composable {
        for (i in 0 until rowCount) {
            for (j in 0 until columnCount) {
                val x = j + topLeftTileIdx.x
                val y = i + topLeftTileIdx.y
                val offs = IntOffset(x, y)

//                key(offs) {   // tried to use it with LaunchedEffect in show function, but frequent recompositions still happening
                Box(modifier = contentModifierZoomed.value) {
                    show(offs, IntOffset(contentWidth, contentHeight), contentModifierZoomed.value)
                }
//                }
            }
        }
    }

    var mousePosition by remember { mutableStateOf(IntOffset(0, 0)) }
    var mousePress by remember { mutableStateOf<IntOffset?>(null) }
    var zoom by remember { mutableStateOf(1f) }

    val layoutMouseEventsModifier =
        Modifier.onPointerEvent(PointerEventType.Move) {
            val curr = it.changes.first().position
            mousePosition = IntOffset(curr.x.toInt(), curr.y.toInt())
            if (mousePress != null) {
                topLeftPos = topLeftPos.plus(mousePress!!).minus(mousePosition)
                mousePress = mousePosition
            }
        }.onPointerEvent(PointerEventType.Press) {
            mousePress = mousePosition
        }.onPointerEvent(PointerEventType.Release) {
            mousePress = null
        }.onPointerEvent(PointerEventType.Scroll) {
            val zoomMultiplier = 1f - (it.changes.first().scrollDelta.y / 10)
            val newZoom = zoom * zoomMultiplier
            if (newZoom > 0) {
                zoom = newZoom
                contentHeight = (dimensions.y * zoom).toInt()
                contentWidth = (dimensions.x * zoom).toInt()
                topLeftPos = topLeftPos.plus(mousePosition).times(zoomMultiplier).minus(mousePosition)
            }
        }

    Layout(
        modifier = layoutMouseEventsModifier.clipToBounds(),
        content = contentBuilder
    ) { measurables, constraints ->
        var currColumnCount = columnCount
        var currRowCount = rowCount

        columnCount = ceil(constraints.maxWidth.toDouble() / contentWidth).toInt() + 1
        rowCount = ceil(constraints.maxHeight.toDouble() / contentHeight).toInt() + 1

        if (measurables.count() == columnCount * rowCount) {
            currColumnCount = columnCount
            currRowCount = rowCount
        }

        val placeables = measurables.map { measurable ->
            measurable.measure(Constraints())
        }

        val startPos = IntOffset(topLeftTileIdx.x * contentWidth, topLeftTileIdx.y * contentHeight).minus(topLeftPos)

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.chunked(currColumnCount).chunked(currRowCount).map { rows ->
                rows.fold(startPos) { yoffs, row ->
                    row.fold(yoffs) { xoffs, item ->
                        item.placeRelative(xoffs)
                        xoffs.plus(IntOffset(contentWidth, 0))
                    }
                    yoffs.plus(IntOffset(0, contentHeight))
                }
            }
        }
    }
}