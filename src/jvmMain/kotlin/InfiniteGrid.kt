@file:Suppress("OPT_IN_IS_NOT_ENABLED")

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
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
fun <T> InfiniteGrid(
    dimensions: IntOffset,
    loadContext: CoroutineScope,
    load: suspend (Int, Int) -> T,
    show: @Composable (T?, Int, Int, Modifier) -> Unit
) {
    var rowCount by remember { mutableStateOf(2) }
    var columnCount by remember { mutableStateOf(2) }

    var contentHeight by remember { mutableStateOf(dimensions.y) }
    var contentWidth by remember { mutableStateOf(dimensions.x) }

    var topLeftPos by remember { mutableStateOf(IntOffset(0, 0)) }
    val topLeftTileIdx by remember {
        derivedStateOf { offs2Idx(topLeftPos, contentWidth, contentHeight) }
    }

    val idx2Items = remember { mutableStateMapOf<IntOffset, T?>() }
    val idxLoadStarted = remember { mutableStateMapOf<IntOffset, Boolean>() }

    val contentModifierZoomed = remember {
        derivedStateOf {
            Modifier.size(
                height = contentHeight.dp,
                width = contentWidth.dp
            )
        }
    }

    val contentBuilder = @Composable {
        println("composing tiles ${topLeftTileIdx.x}:${topLeftTileIdx.y} -- ${columnCount + topLeftTileIdx.x}:${rowCount + topLeftTileIdx.y} r: $rowCount, c: $columnCount")
        for (i in 0 until rowCount) {
            for (j in 0 until columnCount) {
                val x = j + topLeftTileIdx.x
                val y = i + topLeftTileIdx.y
                val offs = IntOffset(x, y)

                LaunchedEffect(offs) {
                    if (!idxLoadStarted.contains(offs)) {
                        idx2Items[offs] = null
                        withContext(loadContext.coroutineContext) {
                            idxLoadStarted[offs] = true
                            idx2Items[offs] = load(x, y)
                        }
                    }
                }

                Box(modifier = contentModifierZoomed.value) {
                    show(idx2Items[offs], x, y, contentModifierZoomed.value)
                }
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


        columnCount = ceil(constraints.maxWidth.toDouble() / contentWidth).toInt() + 1
        rowCount = ceil(constraints.maxHeight.toDouble() / contentHeight).toInt() + 1

        if (measurables.count() != columnCount * rowCount) {
            layout(
                constraints.maxWidth,
                constraints.maxHeight
            ) { }  //skip that layout due to change in observed column/row counts
        } else {
            val placeables = measurables.map { measurable ->
                measurable.measure(Constraints())
            }

            val startPos =
                IntOffset(topLeftTileIdx.x * contentWidth, topLeftTileIdx.y * contentHeight).minus(topLeftPos)

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.chunked(columnCount).chunked(rowCount).map { rows ->
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
}