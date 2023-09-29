import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.random.Random
import java.net.URI
import androidx.compose.ui.res.loadImageBitmap
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit

// blocking image loading wrapped in suspend
//fun loadImage(url: String): ImageBitmap {
//    return URI(url).toURL().openStream().buffered().use(::loadImageBitmap)
//}
//
//fun loadImage(id: Int, dimensions: IntOffset): ImageBitmap {
//    return loadImage("https://picsum.photos/id/$id/${dimensions.x}/${dimensions.y}")
//}
//
//suspend fun loadImageSuspend(id: Int, dimensions: IntOffset): ImageBitmap {
//    return withContext(Dispatchers.IO) {
//        loadImage(id, dimensions)
//    }
//}

// OkHttp is used because of socks proxy support (which is needed on my machine to bypass cloudflare protection)
val client = HttpClient(OkHttp) {
    engine {
        config {
            followRedirects(true)
            val d = Dispatcher()
            d.maxRequests = 100
            d.maxRequestsPerHost = 100
            dispatcher(dispatcher = d)
        }
//        proxy = ProxyBuilder.socks("192.168.8.1", 1081)
    }
}


suspend fun loadImageSuspend(id: Int, dimensions: IntOffset): ImageBitmap {
    return withContext(Dispatchers.IO) {
        val response = client.get("https://picsum.photos/id/$id/${dimensions.x}/${dimensions.y}")
        if (!response.status.isSuccess()) {
            throw ResponseException(response, "")
        }
        org.jetbrains.skia.Image.makeFromEncoded(response.readBytes()).toComposeImageBitmap()
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
fun ShowImage(
    offs2id: ConcurrentHashMap<IntOffset, Id>,
    idLoadStates: SnapshotStateMap<Id, PersistentMap<Width, LoadState<ImageBitmap>>>,
    loadSizes: Map<Int, Int>,
    showDebugInfo: Boolean,
    gridDimensions: IntOffset,
    offs: IntOffset,
    dimensions: IntOffset,
    modifier: Modifier
) {

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
        // LaunchedEffect doesn't work because of and difficulties with tracking composables tree and hence frequent recompositions
        // probably that happens because

        // the same hack as in `LaunchedEffect`, but without `remember`
        // cant just use CoroutineScope(Dispatchers.IO) because in that case there is high possibility of getting
        // "Exception in thread "AWT-EventQueue-0" java.lang.IllegalStateException: Unsupported concurrent change during composition.
        //  A state object was modified by composition as well as being modified outside composition."
        // it is because I need to modify `idLoadStates` from both composition thread and from loading coroutine
        CoroutineScope(currentComposer.applyCoroutineContext).launch {
            try {
                val res = loadImageSuspend(id, desiredDimensions)
                val stateMap = idLoadStates[id]!!
                idLoadStates[id] = stateMap.put(desiredDimensions.x, LoadState.Loaded(res))
            } catch (ex: Exception) {
//                println("ex: $ex")
                val stateMap = idLoadStates[id]!!
                idLoadStates[id] = stateMap.remove(desiredDimensions.x)
                offs2id.remove(offs)    // if there is a trouble with loading an image with specific id, we would just try to load next random one
            }
        }
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


/** @param imageLoadSizesMultiplier images of which sizes should we load. e.g. for `gridDimensions` of `400x400` multiplier `0.5` would mean that we can load images of `200x200` size and multiplier of `3` -> `1200x1200` */
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
            ShowImage(offs2id, idLoadStates, loadSizes, showDebugInfo, gridDimensions, offs, dimensions, modifier)
        }
    )
}