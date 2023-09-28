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
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf


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

sealed class LoadState<T> {
    class Loading<T> : LoadState<T>()
    data class Loaded<T>(val data: T) : LoadState<T>()
}

typealias Id = Int
typealias Width = Int

fun Collection<Int>.closest(key: Int): Int? {
    return minByOrNull { abs(it - key) }
}

fun Collection<Int>.closestBigger(key: Int): Int? {
    return map { Pair(it, it - key) }.filter { (k, d) -> d >= 0 }.minByOrNull { (k, d) -> d }?.first
}

@OptIn(InternalComposeApi::class)
@Composable
fun InfiniteGallery(
    gridDimensions: IntOffset,
    imageLoadSizesMultiplier: Collection<Float>,
    showDebugInfo: Boolean,
) {
    val loadSizes = remember {
        imageLoadSizesMultiplier.associate {
            Pair(
                (gridDimensions.x * it).toInt(),
                (gridDimensions.y * it).toInt()
            )
        }
    }
    val offs2id = remember { ConcurrentHashMap<IntOffset, Id>() }
    val idLoadStates = remember { mutableStateMapOf<Id, PersistentMap<Width, LoadState<ImageBitmap>>>() }

    InfiniteGrid(
        gridDimensions,
        show = @Composable { offs, dimensions, modifier ->
            val id = offs2id.compute(offs) { _, prevId ->
                prevId ?: (abs(Random.nextInt()) % 1085) // pixsum has only 1084 images
            }!!

            val desiredDimensions = run {
                val w = loadSizes.keys.closestBigger(dimensions.x) ?: loadSizes.keys.closest(dimensions.x)
                if (w == null) {
                    IntOffset(gridDimensions.x, gridDimensions.y)
                } else {
                    IntOffset(w, loadSizes[w]!!)
                }
            }

            var loadNeeded = false
            idLoadStates.compute(id) { _, stateMap ->
                if (stateMap == null) {
                    loadNeeded = true
                    persistentMapOf(desiredDimensions.x to LoadState.Loading())
                } else {
                    if (stateMap.containsKey(desiredDimensions.x)) {
                        stateMap
                    } else {
                        loadNeeded = true
                        stateMap.put(desiredDimensions.x, LoadState.Loading())
                    }
                }
            }

            if (loadNeeded) {
                // the same hack as in `LaunchedEffect`, but without `remember`
                // cant just use CoroutineScope(Dispatchers.IO) because in that case there is high possibility of getting
                // Exception in thread "AWT-EventQueue-0" java.lang.IllegalStateException: Unsupported concurrent change during composition. A state object was modified by composition as well as being modified outside composition.
                // it is because I need to modify `idLoadStates` from both composition thread and from loading coroutine
                CoroutineScope(currentComposer.applyCoroutineContext).launch {
                    try {
                        val res = loadImage(id, desiredDimensions)
                        val stateMap = idLoadStates[id]!!
                        idLoadStates[id] = stateMap.put(desiredDimensions.x, LoadState.Loaded(res))
                    } catch (ex: Exception) {
                        val stateMap = idLoadStates[id]!!
                        idLoadStates[id] = stateMap.remove(desiredDimensions.x)
                        offs2id.remove(offs)
                    }
                }

                // that approach doesn't work for me because of recompositions & remember inside `LaunchedEffect`
                // effects are spontaneously cancelled (even if I try to specify `key()` around `Box` in `InfiniteGrid`)

//                LaunchedEffect(Unit) {
//                    try {
//                        val res = loadImage(id, desiredDimensions)
//                        val stateMap = idLoadStates[id]!!
//                        idLoadStates[id] = stateMap.put(desiredDimensions.x, LoadState.Loaded(res))
//                    } catch (ex: Exception) {
//                        val stateMap = idLoadStates[id]!!
//                        idLoadStates[id] = stateMap.remove(desiredDimensions.x)
//                        offs2id.remove(offs)
//                    }
//                }
            }

            val stateMap = idLoadStates[id]!!
            val alreadyLoaded =
                stateMap.filter { it.value is LoadState.Loaded } // getting all loaded images for current id
            val closestWidth =
                alreadyLoaded.keys.closestBigger(desiredDimensions.x) ?: alreadyLoaded.keys.closest(desiredDimensions.x)
            if (closestWidth != null) {
                val el = alreadyLoaded[closestWidth] as LoadState.Loaded
                Image(
                    painter = BitmapPainter(el.data),
                    contentDescription = "random image!",
                    contentScale = ContentScale.Fit,
                    modifier = modifier.fillMaxSize()
                )
            } else {
                Box(modifier.background(Color.Red)) {}
            }

            if (showDebugInfo) {
                Text("${offs.x}, ${offs.y}")
            }
        }
    )
}