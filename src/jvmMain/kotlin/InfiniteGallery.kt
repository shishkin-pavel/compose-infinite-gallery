import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.URL


fun loadImageBitmap(file: File): ImageBitmap =
    file.inputStream().buffered().use(::loadImageBitmap)

fun loadImageBitmap(url: String): ImageBitmap =
    URL(url).openStream().buffered().use(::loadImageBitmap)


@Composable
fun InfiniteGallery(
    dimensions: IntOffset,
    showDebugInfo: Boolean,
) {
    val defaultPainter = remember { BitmapPainter(loadImageBitmap(File("sample.png"))) }

    InfiniteGrid(
        dimensions, CoroutineScope(Dispatchers.IO),
        load = { i, j ->
            try {
                loadImageBitmap("https://picsum.photos/200/200")
//                    loadImageBitmap("https://random.imagecdn.app/200/200")
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        },
        show = @Composable { el, x, y, modifier ->
            if (el == null) {
//                    Image(
//                        painter = defaultPainter,
//                        contentDescription = "image stub",
//                        contentScale = ContentScale.Fit,
//                        modifier = modifier.fillMaxSize()
//                    )
                Box(modifier.background(Color.Red)) {}
            } else {
                Image(
                    painter = BitmapPainter(el),
                    contentDescription = "random image!",
                    contentScale = ContentScale.Fit,
                    modifier = modifier.fillMaxSize()
                )
            }
            if (showDebugInfo) {
                Text("[$x, $y]")
            }
        }
//            load = { i, j ->
//                "\nImg $i:$j"
//            },
//            show = @Composable { el, x, y, modifier ->
//                if (el == null) {
//                    Box(modifier.background(Color.Red)) {}
//                    Text("\nNONE")
//                } else {
//                    Text(el)
//                }
//                if (showDebugInfo) {
//                    Text("[$x, $y]")
//                }
//            }
    )
}