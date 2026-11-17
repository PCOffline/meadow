package dev.pcoffline.meadow.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.theme.Garden

/**
 * The five plants of the theme that shows, each on a card at the size that the garden draws it,
 * with [bloom] of its time left, or [asleep]. Each has its name under it, for the reader of the
 * pictures: the app shows no plant names.
 */
@Composable
internal fun PlantSheet(bloom: Float, asleep: Boolean = false) {
    Column(
        Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        plantsOf(Garden.theme).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { plant ->
                    MeadowCard(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            BloomFlower(bloom, plant, Modifier.size(90.dp, 120.dp), asleep = asleep)
                        }
                        Text(plant.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
                // An odd plant keeps the width of one card.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
