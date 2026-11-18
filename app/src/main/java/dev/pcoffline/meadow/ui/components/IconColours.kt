package dev.pcoffline.meadow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Sites
import dev.pcoffline.meadow.usage.IconColour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour of the icon of [packageName]: the one kept on the phone at once, and then the one
 * read from the icon, when it differs. Null until it is first read, and for a website.
 */
@Composable
fun rememberIconColour(packageName: String): IconColour? {
    val store = AppGraph.store
    val kept by store.iconColours.collectAsStateWithLifecycle()
    LaunchedEffect(packageName) {
        if (Sites.isSite(packageName)) return@LaunchedEffect
        val read = withContext(Dispatchers.Default) { AppGraph.catalog.iconColour(packageName) } ?: return@LaunchedEffect
        if (store.iconColours.value[packageName] != read) store.setIconColour(packageName, read)
    }
    return kept[packageName]
}
