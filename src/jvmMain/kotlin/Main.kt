import androidx.compose.foundation.layout.*
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.singleWindowApplication


fun main() = singleWindowApplication {
    var showDebugInfo by remember { mutableStateOf(false) }

    Column {
        Row {
            Text("show debug info:")
            Switch(checked = showDebugInfo, onCheckedChange = { newValue -> showDebugInfo = newValue })
        }
        InfiniteGallery(IntOffset(300, 300), listOf(0.01F, 0.1F, 0.5F, 1F, 2F, 4F, 8F), showDebugInfo)
    }
}