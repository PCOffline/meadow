package dev.pcoffline.meadow.ui.limits

import dev.pcoffline.meadow.usage.IconColour
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.flora.defaultPlace
import dev.pcoffline.meadow.ui.components.flora.plantsOf
import dev.pcoffline.meadow.ui.theme.Garden

/**
 * The plant of the limit for [packageName]: one of the theme's five. [chosen] is its place in
 * them, or null for the plant nearest in colour to the app's [icon]. Choosing that plant gives null.
 */
@Composable
internal fun PlantPicker(packageName: String, icon: IconColour?, chosen: Int?, enabled: Boolean, onChoose: (Int?) -> Unit) {
    val named = defaultPlace(packageName, Garden.theme, icon)
    val selected = chosen ?: named
    MeadowCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.limit_editor_plant_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            plantsOf(Garden.theme).forEachIndexed { place, plant ->
                val on = place == selected
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.weight(1f).heightIn(min = 72.dp).clip(shape)
                        .border(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
                        .selectable(on, enabled = enabled, role = Role.RadioButton) { onChoose(place.takeIf { it != named }) }
                        .semantics { contentDescription = plant.name },
                    contentAlignment = Alignment.Center,
                ) {
                    BloomFlower(1f, plant, Modifier.size(40.dp, 60.dp), sway = false, growIn = false)
                }
            }
        }
    }
}
