package dev.pcoffline.meadow.ui.legal

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        const val CURRENT_VERSION = 1

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
            MarkdownText(text)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Renders the small Markdown subset used by the legal documents: headings, lists, tables, bold. */
@Composable
fun MarkdownText(markdown: String) {
    val blocks = MarkdownBlocks.parse(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlocks.Block.Heading -> Text(
                    block.text,
                    style = if (block.level == 1) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = if (block.level == 1) 0.dp else 8.dp).semantics { heading() },
                )
                is MarkdownBlocks.Block.Paragraph -> Text(inline(block.text), style = MaterialTheme.typography.bodyMedium)
                is MarkdownBlocks.Block.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(inline(block.text), style = MaterialTheme.typography.bodyMedium)
                }
                is MarkdownBlocks.Block.Table -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    block.rows.forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            row.forEachIndexed { c, cell ->
                                Text(
                                    inline(cell),
                                    style = if (i == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(if (c == 0) 1f else 1.2f).padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    MarkdownBlocks.spans(text).forEach { (part, bold) ->
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

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
