@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.SkyPreview
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * How Meadow looks: live previews of each theme, plus light / dark / system. Changes apply at once.
 * It's in Settings, where people look for it; it still follows the account to a new phone.
 */
@Composable
internal fun AppearanceCard() {
    val themeKey by AppGraph.store.theme.collectAsStateWithLifecycle()
    val darkKey by AppGraph.store.darkMode.collectAsStateWithLifecycle()
    val current = GardenTheme.fromKey(themeKey)
    val darkMode = DarkMode.fromKey(darkKey)
    // Applied here at once and saved to the account in the background: nothing to wait for, so a
    // theme then a night mode picked straight after both count. Each takes the other from the
    // store at the tap, not as last drawn.
    fun pick(theme: String = AppGraph.store.theme.value ?: current.key, mode: String = AppGraph.store.darkMode.value ?: darkMode.key) {
        AppGraph.scope.launch(Dispatchers.Main.immediate) { AppGraph.profiles.setAppearance(theme, mode) }
    }

    MeadowCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 3,
            ) {
                GardenTheme.entries.forEach { theme ->
                    MeadowTheme(theme, darkMode) {
                        ThemeCard(theme, selected = theme == current, modifier = Modifier.weight(1f)) {
                            pick(theme = theme.key)
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DarkMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = mode == darkMode,
                        onClick = { pick(mode = mode.key) },
                        shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size),
                    ) { Text(stringResource(mode.label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            WeatherRow()
        }
    }
}

/**
 * The weather in the sky, off unless turned on. Turning it on asks for approximate location (never
 * precise); without it, there's a way to Android's settings. Taking the permission away there
 * shows it off again.
 */
@Composable
private fun WeatherRow() {
    val context = LocalContext.current
    val on by AppGraph.store.weatherOn.collectAsStateWithLifecycle()
    var permitted by remember { mutableStateOf(AppGraph.weather.hasPermission()) }
    var denied by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        permitted = AppGraph.weather.hasPermission()
        if (permitted) denied = false
        onPauseOrDispose { }
    }
    fun turnOn() {
        AppGraph.weather.turnOn()
        AppGraph.scope.launch { AppGraph.weather.refreshIfStale() }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = granted
        denied = !granted
        if (granted) turnOn()
    }
    SwitchRow(
        title = stringResource(R.string.settings_weather),
        body = stringResource(R.string.settings_weather_body),
        checked = on && permitted,
    ) { want ->
        when {
            !want -> AppGraph.weather.turnOff()
            permitted -> turnOn()
            else -> ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    if (denied) {
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            }
        }) { Text(stringResource(R.string.settings_weather_allow)) }
    }
}

@Composable
private fun ThemeCard(theme: GardenTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(theme.label)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The theme itself, small: its sky, sun, hills and a flower (a toadstool in the mushroom forest).
        Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(12.dp))) {
            SkyPreview(Modifier.matchParentSize())
            BloomFlower(1f, Garden.colors.petalPink, Modifier.align(Alignment.BottomCenter).size(38.dp, 54.dp), sway = false, growIn = false)
        }
        Spacer(Modifier.height(6.dp))
        // Room for two lines on every card, so a long name doesn't make its card taller than the rest.
        val style = MaterialTheme.typography.labelMedium
        Box(Modifier.heightIn(min = with(LocalDensity.current) { (style.lineHeight * 2).toDp() }), contentAlignment = Alignment.Center) {
            Text("${theme.emoji} $label", style = style, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
