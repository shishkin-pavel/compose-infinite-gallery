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
import kotlin.math.abs
import kotlin.random.Random


fun loadImageBitmap(file: File): ImageBitmap =
    file.inputStream().buffered().use(::loadImageBitmap)

fun loadImageBitmap(url: String): ImageBitmap {
    return URI(url).toURL().openStream().buffered().use(::loadImageBitmap)
}

suspend fun loadImage(id: Int, dimensions: IntOffset): ImageBitmap {
    return withContext(Dispatchers.IO) {
        loadImageBitmap("https://picsum.photos/id/$id/${dimensions.x}/${dimensions.y}")
//        loadImageBitmap("https://random.imagecdn.app/${dimensions.x}/${dimensions.y}")
    }
}

sealed class LoadState {
    object Cancelled : LoadState()
    data class Loading(val id: Int) : LoadState()
    data class Loaded(val id: Int) : LoadState()
}

@Composable
fun InfiniteGallery(
    dimensions: IntOffset,
    showDebugInfo: Boolean,
) {
    val defaultPainter = remember { BitmapPainter(loadImageBitmap(File("sample.png"))) }
    val id2Items = remember { mutableStateMapOf<Int, ImageBitmap?>() }
    val offsLoadState = remember { ConcurrentHashMap<IntOffset, LoadState>() } // TODO offs->id + idLoadState

    val ioScope = CoroutineScope(Dispatchers.IO)

    InfiniteGrid(
        dimensions,
        show = @Composable { offs, modifier ->
            var id = -1
            var needLoad = false
            val loadState = offsLoadState.compute(offs) { _, state ->
                when (state) {
                    is LoadState.Loading -> {
                        id = state.id
                        state
                    }
                    is LoadState.Loaded -> {
                        id = state.id
                        state
                    }
                    else -> {
                        id = abs(Random.nextInt()) % 1085 // pixsum has only 1084 images
                        if (!id2Items.containsKey(id)) {
                            needLoad = true
                            LoadState.Loading(id)
                        } else {
                            LoadState.Loaded(id)
                        }

                    }
                }
            }
            if (needLoad) {
                ioScope.launch {
                    try {
                        val res = loadImage(id, dimensions)
                        id2Items[id] = res
                        offsLoadState[offs] = LoadState.Loaded(id)
                    } catch (ex: Exception) {
//                        ex.printStackTrace()
                        offsLoadState[offs] = LoadState.Cancelled   // we do not provide `id` for that case => id would be picked at random (there are some id's missing on picture provider side)
                    }
                }

//                LaunchedEffect(offs) {    // TODO? that approach doesnt work for me because of recompositions. effects are spontaneously cancelled
//                    try {
//                        val res = loadImage(id, dimensions)
//                        id2Items[id] = res
//                        offsLoadState[offs] = LoadState.Loaded(id)
//                    } catch (ex: Exception) {
////                        ex.printStackTrace()
//                        offsLoadState[offs] = LoadState.Cancelled
//                    }
//                }
            }
            val el = id2Items[id]
            if (el == null) {
//                    Image(
//                        painter = defaultPainter,
//                        contentDescription = "image stub",
//                        contentScale = ContentScale.Fit,
//                        modifier = modifier.fillMaxSize()
//                    )
                if (loadState is LoadState.Loading) {
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
    )
}