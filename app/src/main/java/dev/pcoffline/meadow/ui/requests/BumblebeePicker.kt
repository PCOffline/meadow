@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.ProfileState
import dev.pcoffline.meadow.data.UserProfile

/**
 * Which of your Bumblebees a request goes to. Starts with whoever you asked last time, or all of
 * them; nobody asked learns who else was.
 */
object BeePicks {
    /** The Bumblebees picked, never empty while there are any. */
    fun effective(picked: List<String>?, all: List<String>, remembered: Set<String>): List<String> {
        val start = picked ?: remembered.filter { it in all }.ifEmpty { all }
        return start.filter { it in all }.ifEmpty { all }
    }

    /** What to send: null asks everyone, which also covers Bumblebees added meanwhile. */
    fun toSend(picked: List<String>?, all: List<String>, remembered: Set<String>): List<String>? =
        effective(picked, all, remembered).takeIf { it.size < all.size }

    /** The ids to send for the current choice, remembering it for next time. */
    fun commit(picked: List<String>?): List<String>? {
        val all = (AppGraph.sync.profile.value as? ProfileState.Ready)?.profile?.approverIds.orEmpty()
        if (all.isEmpty()) return null
        val remembered = AppGraph.store.lastAskedBees
        AppGraph.store.lastAskedBees = effective(picked, all, remembered).toSet()
        return toSend(picked, all, remembered)
    }
}

@Composable
fun rememberBeePicks(): MutableState<List<String>?> = rememberSaveable { mutableStateOf(null) }

/** Chips for choosing who to ask. Shown only with more than one Bumblebee. */
@Composable
fun BumblebeePicker(picks: MutableState<List<String>?>, modifier: Modifier = Modifier) {
    val state by AppGraph.sync.profile.collectAsStateWithLifecycle()
    val all = (state as? ProfileState.Ready)?.profile?.approverIds.orEmpty()
    if (all.size < 2) return
    val bees by remember(all) { AppGraph.social.profiles(all) }.collectAsStateWithLifecycle(emptyList())
    BumblebeeChips(bees, BeePicks.effective(picks.value, all, AppGraph.store.lastAskedBees), modifier) { picks.value = it }
}

/** The chips themselves, drawn from plain state (also used for tests). */
@Composable
fun BumblebeeChips(bees: List<UserProfile>, chosen: List<String>, modifier: Modifier = Modifier, onChange: (List<String>) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.ask_who), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bees.forEach { bee ->
                val on = bee.uid in chosen
                FilterChip(
                    selected = on,
                    // At least one Bumblebee stays picked.
                    onClick = { if (!on) onChange(chosen + bee.uid) else if (chosen.size > 1) onChange(chosen - bee.uid) },
                    label = { Text("${bee.avatar.emoji} ${bee.displayName}") },
                )
            }
        }
    }
}
