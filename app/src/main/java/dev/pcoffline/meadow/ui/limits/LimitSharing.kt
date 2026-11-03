package dev.pcoffline.meadow.ui.limits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.ChangeOutcome
import dev.pcoffline.meadow.data.ChatMessage
import dev.pcoffline.meadow.data.LimitCardMode
import dev.pcoffline.meadow.data.Sites
import dev.pcoffline.meadow.ui.components.AppIcon
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.limitText
import dev.pcoffline.meadow.ui.rememberAction
import kotlinx.serialization.json.Json

/** A limit carried through navigation as text: the starting point of a copy or a suggestion. */
object LimitTemplate {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(limit: AppLimit): String = json.encodeToString(AppLimit.serializer(), limit)

    fun decode(text: String?): AppLimit? = text?.let { runCatching { json.decodeFromString(AppLimit.serializer(), it) }.getOrNull() }

    /** [limit] moved to [packageName], named [appName]: what a copy keeps when its app changes. */
    fun forApp(limit: AppLimit, packageName: String, appName: String): AppLimit = limit.copy(packageName = packageName, appName = appName)
}

/** What Apply does with a suggested limit, before anything is saved. */
sealed interface ApplyStep {
    /** The app isn't on this phone: nothing is saved. */
    data object NotOnPhone : ApplyStep

    /** It changes [current]: show what changes, then apply. */
    data class Confirm(val current: AppLimit) : ApplyStep

    /** No limit for the app yet: apply at once. */
    data object Apply : ApplyStep

    companion object {
        /** The step for [limit], with [current] the limit here for its app, and [onPhone] whether the app is here. */
        fun of(limit: AppLimit, current: AppLimit?, onPhone: Boolean): ApplyStep = when {
            !onPhone -> NotOnPhone
            current != null -> Confirm(current)
            else -> Apply
        }
    }
}

/** Whether [packageName] can have a limit on this phone: a website always can, an app only when installed. */
fun onThisPhone(packageName: String, installed: Set<String>): Boolean = Sites.isSite(packageName) || packageName in installed

/**
 * Saves a suggested limit. From one of my Bumblebees ([fromBumblebee]), it is their approval, and the
 * backend saves it. From anyone else, it is saved as I would save it myself: a looser one asks.
 * Once it applies, my other open questions to change or remove that limit are withdrawn.
 * Either way it keeps my name for the app ([myName]): the sender's name is only theirs.
 */
suspend fun applySuggested(
    chatId: String,
    message: ChatMessage,
    fromBumblebee: Boolean,
    myName: String,
    applyAsApproval: suspend (chatId: String, messageId: String) -> Unit = { c, m -> AppGraph.limits.applySuggestion(c, m) },
    save: suspend (AppLimit) -> ChangeOutcome = { AppGraph.limits.save(it) },
    withdrawChanges: suspend (packageName: String) -> Unit = { AppGraph.requests.withdrawChanges(it) },
): ChangeOutcome {
    val limit = message.limit ?: return ChangeOutcome.APPLIED
    if (fromBumblebee) {
        // The backend also withdraws my other open questions about this limit.
        applyAsApproval(chatId, message.id)
        return ChangeOutcome.APPLIED
    }
    val outcome = save(limit.copy(appName = myName))
    // Applied at once: my other open questions about this limit are settled by it.
    if (outcome == ChangeOutcome.APPLIED) withdrawChanges(limit.packageName)
    return outcome
}

/** The name to show for [limit]'s app: this phone's own when the app is here, else the name it came with. */
fun localName(limit: AppLimit, onPhone: Boolean): String = if (onPhone && !Sites.isSite(limit.packageName)) AppGraph.catalog.label(limit.packageName) else limit.appName

/**
 * A limit card in a chat: which app, the whole limit, and what can be done with it. Only the
 * person it was sent to gets the buttons: [onCopy] for a shared limit, Apply and Change ([onCopy]
 * again, to change it first) for a suggested one.
 */
@Composable
fun LimitCardMessage(chatId: String, message: ChatMessage, mine: Boolean, fromBumblebee: Boolean, onCopy: (AppLimit) -> Unit) {
    val limit = message.limit ?: return
    val mode = message.limitMode ?: return
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        MeadowCard(Modifier.widthIn(max = 320.dp)) {
            LimitCardContent(chatId, message, limit, mode, mine, fromBumblebee, onCopy)
        }
    }
}

@Composable
private fun LimitCardContent(
    chatId: String,
    message: ChatMessage,
    limit: AppLimit,
    mode: LimitCardMode,
    mine: Boolean,
    fromBumblebee: Boolean,
    onCopy: (AppLimit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(if (mode == LimitCardMode.SHARED) R.string.limit_card_shared else R.string.limit_card_suggested),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        // This phone's name for the app, when it's here, not one the sender typed.
        val name = remember(limit) { localName(limit, onThisPhone(limit.packageName, AppGraph.catalog.launchablePackages())) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(limit.packageName, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
        Text(limitText(limit), style = MaterialTheme.typography.bodyMedium)
        if (mine) return@Column
        when (mode) {
            LimitCardMode.SHARED -> OutlinedButton(onClick = { onCopy(limit) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.limit_menu_copy))
            }
            LimitCardMode.SUGGESTED -> ApplyButtons(chatId, message, limit, fromBumblebee, onChange = { onCopy(limit) })
        }
    }
}

@Composable
private fun ApplyButtons(chatId: String, message: ChatMessage, limit: AppLimit, fromBumblebee: Boolean, onChange: () -> Unit) {
    val limits by AppGraph.store.limits.collectAsStateWithLifecycle()
    val action = rememberAction()
    val res = LocalResources.current
    var step by remember { mutableStateOf<ApplyStep?>(null) }
    val applied = stringResource(R.string.limit_apply_done, limit.appName)
    val asked = stringResource(R.string.limit_editor_requested)
    val notOnPhone = stringResource(R.string.limit_apply_not_on_phone, limit.appName)
    val current = limits.firstOrNull { it.packageName == limit.packageName }
    fun apply() = action.run {
        val name = current?.appName ?: localName(limit, onThisPhone(limit.packageName, AppGraph.catalog.launchablePackages()))
        val outcome = applySuggested(chatId, message, fromBumblebee, name)
        action.toast(if (outcome == ChangeOutcome.APPLIED) applied else asked)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                when (val next = ApplyStep.of(limit, current, onThisPhone(limit.packageName, AppGraph.catalog.launchablePackages()))) {
                    ApplyStep.NotOnPhone -> action.toast(notOnPhone)
                    ApplyStep.Apply -> apply()
                    is ApplyStep.Confirm -> step = next
                }
            },
            enabled = !action.busy,
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.limit_apply)) }
        OutlinedButton(onClick = onChange, enabled = !action.busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.limit_change))
        }
    }
    ErrorNote(action.error)
    (step as? ApplyStep.Confirm)?.let { confirm ->
        AlertDialog(
            onDismissRequest = { step = null },
            title = { Text(stringResource(R.string.limit_apply_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(confirm.current.appName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.limit_apply_now), style = MaterialTheme.typography.titleSmall)
                    Text(res.limitText(confirm.current), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.limit_apply_after), style = MaterialTheme.typography.titleSmall)
                    Text(res.limitText(limit), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    step = null
                    apply()
                }) { Text(stringResource(R.string.limit_apply)) }
            },
            dismissButton = { TextButton(onClick = { step = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** What the limit menu can do: copy a limit, and suggest one for an app (with its limit, if any). */
class LimitMenuActions(
    val onCopy: (AppLimit) -> Unit,
    val onSuggest: (packageName: String, appName: String, current: AppLimit?) -> Unit,
)

/**
 * [content] that opens the limit menu when tapped: Copy this limit when there is a [limit], and
 * Suggest a change (or Suggest a limit, for an app with none).
 */
@Composable
fun LimitMenu(
    packageName: String,
    appName: String,
    limit: AppLimit?,
    actions: LimitMenuActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val more = stringResource(R.string.limit_menu_open, appName)
    Box(modifier) {
        Box(Modifier.fillMaxWidth().clickable(onClickLabel = more, role = Role.Button) { open = true }) { content() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (limit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.limit_menu_copy)) },
                    onClick = {
                        open = false
                        actions.onCopy(limit)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (limit != null) R.string.limit_menu_suggest_change else R.string.limit_menu_suggest_new)) },
                onClick = {
                    open = false
                    actions.onSuggest(packageName, appName, limit)
                },
            )
        }
    }
}
