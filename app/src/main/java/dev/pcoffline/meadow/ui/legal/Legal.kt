package dev.pcoffline.meadow.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.model.markdownDimens
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.Loading
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.stateWhileVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** The legal documents, stored as Markdown in /legal and shipped as app assets. */
enum class LegalDoc(val asset: String, @StringRes val title: Int) {
    PRIVACY("privacy_notice.md", R.string.legal_privacy),
    TERMS("terms_of_use.md", R.string.legal_terms),
    LICENSES("licenses.md", R.string.legal_licenses);

    companion object {
        /** Bump when the privacy notice or terms change in a way people must accept again. */
        const val CURRENT_VERSION = 3

        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: PRIVACY
    }
}

class LegalViewModel(doc: LegalDoc) : ViewModel() {
    val markdown: StateFlow<String?> = flow {
        emit(AppGraph.app.assets.open(doc.asset).bufferedReader().use { it.readText() })
    }.flowOn(Dispatchers.IO).stateWhileVisible(this, null)
}

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(doc.title), onBack = onBack)
        LegalBody(doc, Modifier.fillMaxSize())
    }
}

/** A legal document over the current screen, for use before there's any navigation (onboarding). */
@Composable
fun LegalDialog(doc: LegalDoc, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LegalScreen(doc, onBack = onDismiss)
        }
    }
}

@Composable
private fun LegalBody(doc: LegalDoc, modifier: Modifier) {
    val viewModel = viewModel(key = "legal-${doc.name}") { LegalViewModel(doc) }
    val markdown by viewModel.markdown.collectAsStateWithLifecycle()
    val text = markdown
    if (text == null) {
        Loading(modifier)
    } else {
        Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            MarkdownText(text, Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Renders Markdown (GitHub flavour, with tables) in the app's typography and colours. Tables keep
 * readable columns that wrap their text, and scroll sideways when they're wider than the screen.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    Markdown(
        content = markdown,
        modifier = modifier,
        dimens = markdownDimens(tableCellWidth = TABLE_COLUMN_WIDTH),
        components = markdownComponents(
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
            table = { model ->
                MarkdownTable(
                    model.content,
                    model.node,
                    style = model.typography.table,
                    headerBlock = { content, header, width, style ->
                        MarkdownTableHeader(content, header, width, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                    },
                    rowBlock = { content, row, width, style ->
                        MarkdownTableRow(content, row, width, style, verticalAlignment = Alignment.Top, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                    },
                )
            },
        ),
    )
}

/** Wide enough for a few words a line; two columns are then wider than a phone, so the table scrolls. */
private val TABLE_COLUMN_WIDTH = 220.dp

/** Asks to accept the terms again after they change. It can't be dismissed without agreeing. */
@Composable
fun TermsUpdateDialog(onAccept: () -> Unit) {
    var reading by remember { mutableStateOf<LegalDoc?>(null) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.legal_update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.legal_update_body))
                ConsentLinks { reading = it }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.legal_update_accept)) } },
    )
    reading?.let { LegalDialog(it, onDismiss = { reading = null }) }
}

/** Shown once at sign-up, and again whenever the documents change version. */
@Composable
fun ConsentLinks(onOpen: (LegalDoc) -> Unit) {
    Row {
        TextButton(onClick = { onOpen(LegalDoc.TERMS) }) { Text(stringResource(R.string.legal_terms)) }
        TextButton(onClick = { onOpen(LegalDoc.PRIVACY) }) { Text(stringResource(R.string.legal_privacy)) }
    }
}
