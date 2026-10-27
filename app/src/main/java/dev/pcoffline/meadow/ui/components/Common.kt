@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Connectivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.Photos
import dev.pcoffline.meadow.ui.theme.AvatarColor
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MeadowCard(
    modifier: Modifier = Modifier,
    tint: Color = Garden.colors.cardTint,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = tint)
    val shape = RoundedCornerShape(24.dp)
    val border = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape)
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Card(
            onClick = onClick,
            modifier = modifier.pressBounce(interaction).then(border),
            shape = shape,
            colors = colors,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Card(modifier = modifier.then(border), shape = shape, colors = colors) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

/** Marks a title as a heading, so screen reader users can jump between sections. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Daisy(Modifier.size(14.dp), petal = Garden.colors.petalPink)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.asHeading().weight(1f))
        trailing?.invoke()
    }
}

/** A short, quiet line that says what the screen around it can't show by itself. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun EmptyState(emoji: String, title: String, body: String? = null, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bob = if (rememberReducedMotion()) null else rememberInfiniteTransition(label = "float").animateFloat(
            -4f, 4f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob",
        )
        Text(emoji, fontSize = 44.sp, modifier = Modifier.graphicsLayer { translationY = (bob?.value ?: 0f).dp.toPx() })
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.asHeading())
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) = SproutLoader(modifier)

@Composable
fun FlowerAvatar(flower: String, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(flower, fontSize = (size.value * 0.5f).sp)
    }
}

/** A person: their photo, or their emoji on the soft colour they picked. */
@Composable
fun PersonAvatar(avatar: Avatar, modifier: Modifier = Modifier, size: Dp = 44.dp, contentDescription: String? = null) {
    val circle = AvatarColor.fromKey(avatar.color).let { if (LocalDarkTheme.current) it.dark else it.light }
    // With a description, the avatar reads as that one label (photo or emoji); without, as its emoji.
    val semantics = if (contentDescription != null) Modifier.clearAndSetSemantics { this.contentDescription = contentDescription } else Modifier
    Box(modifier.size(size).clip(CircleShape).background(circle).then(semantics), contentAlignment = Alignment.Center) {
        Text(avatar.emoji, fontSize = (size.value * 0.5f).sp)
        val photo = remember(avatar.photoUrl) { Photos.safe(avatar.photoUrl) }
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    // Keyed by the app, not just restarted: produceState keeps its last value across a new key, so
    // a reused screen (the block screen after onNewIntent) would go on showing the last app's icon.
    val icon by key(packageName) {
        produceState(AppGraph.catalog.cachedIcon(packageName)) {
            if (value == null) value = withContext(Dispatchers.IO) { AppGraph.catalog.icon(packageName) }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
    } else {
        Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("🌱", fontSize = (size.value * 0.45f).sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * What went wrong, scrolled into view when it appears: it often sits below the button that
 * failed, under the keyboard or the fold, where a tap would otherwise seem to do nothing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ErrorNote(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { requester.bringIntoView() }
    Text(
        "🍂 $message",
        modifier = modifier.bringIntoViewRequester(requester).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** Says that what's on screen needs a connection the phone doesn't have right now. */
@Composable
fun OfflineNote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.offline_needs_connection),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** A thin strip across the top while the phone is offline. */
@Composable
fun OfflineBar(modifier: Modifier = Modifier) {
    val online by Connectivity.online.collectAsStateWithLifecycle()
    AnimatedVisibility(!online, modifier, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.offline_bar), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
