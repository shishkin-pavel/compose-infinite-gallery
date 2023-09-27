import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.*
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap


fun loadImageBitmap(file: File): ImageBitmap =
    file.inputStream().buffered().use(::loadImageBitmap)

fun loadImageBitmap(url: String): ImageBitmap {
    return URI(url).toURL().openStream().buffered().use(::loadImageBitmap)
}

suspend fun loadRandomImage(dimensions: IntOffset): ImageBitmap {
    return withContext(Dispatchers.IO) {
        loadImageBitmap("https://picsum.photos/${dimensions.x}/${dimensions.y}")
//        loadImageBitmap("https://random.imagecdn.app/${dimensions.x}/${dimensions.y}")
    }
}

sealed class LoadState {
    object Loading : LoadState()
    object Cancelled : LoadState()
    object Loaded : LoadState()
}

@Composable
fun InfiniteGallery(
    dimensions: IntOffset,
    showDebugInfo: Boolean,
) {
    val defaultPainter = remember { BitmapPainter(loadImageBitmap(File("sample.png"))) }
    val idx2Items = remember { mutableStateMapOf<IntOffset, ImageBitmap?>() }
    val idxLoadState = remember { ConcurrentHashMap<IntOffset, LoadState>() }

    val ioScope = CoroutineScope(Dispatchers.IO)

    InfiniteGrid(
        dimensions,
        show = @Composable { offs, modifier ->
            var shouldStartLoading = false
            val loadState = idxLoadState.compute(offs) { _, state ->
                when (state) {
                    LoadState.Loading -> {
                        state
                    }
                    LoadState.Loaded -> {
                        state
                    }
                    else -> {
                        shouldStartLoading = true
                        LoadState.Loading
                    }
                }
            }
            if (shouldStartLoading) {
                ioScope.launch {
                    try {
                        val res = loadRandomImage(dimensions)
                        idx2Items[offs] = res
                        idxLoadState[offs] = LoadState.Loaded
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        idxLoadState[offs] = LoadState.Cancelled
                    }
                }

//                LaunchedEffect(offs) {
//                    try {
//                        val res = withContext(Dispatchers.IO) {
//                            loadRandomImage(dimensions)
//                        }
//                        idx2Items[offs] = res
//                        idxLoadState[offs] = LoadState.Loaded
//                    } catch (ex: Exception) {
//                        println("ex for $offs: ${ex.message}")
//                        idxLoadState[offs] = LoadState.Cancelled
//                    }
//                }
            }
            val el = idx2Items[offs]
            if (el == null) {
//                    Image(
//                        painter = defaultPainter,
//                        contentDescription = "image stub",
//                        contentScale = ContentScale.Fit,
//                        modifier = modifier.fillMaxSize()
//                    )
                if (loadState == LoadState.Loading) {
                    Box(modifier.background(Color.Blue)) {}
                } else {
                    Box(modifier.background(Color.Red)) {}
                }
            } else {
                Image(
                    painter = BitmapPainter(el),
                    contentDescription = "random image!",
                    contentScale = ContentScale.Fit,
                    modifier = modifier.fillMaxSize()
                )
            }
            if (showDebugInfo) {
                Text("${offs.x}, ${offs.y}")
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