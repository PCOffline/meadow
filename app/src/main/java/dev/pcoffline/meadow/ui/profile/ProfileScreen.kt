@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.FlowerAvatars
import dev.pcoffline.meadow.data.ProfileRepository
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.AvatarColor
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.Garden
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme

@Composable
fun ProfileScreen(profile: UserProfile, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var emoji by rememberSaveable { mutableStateOf(profile.avatar.emoji) }
    var color by rememberSaveable { mutableStateOf(profile.avatar.color) }
    val save = rememberAction()
    val photo = rememberAction()
    val celebrate = LocalCelebrate.current
    val saved = stringResource(R.string.profile_saved)
    val photoSaved = stringResource(R.string.profile_photo_saved)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo.run(successMessage = photoSaved) { AppGraph.profiles.setPhoto(uri) }
    }
    val preview = Avatar(emoji, color, profile.avatar.photoUrl)
    val changed = name != profile.displayName || bio != profile.bio || emoji != profile.avatar.emoji || color != profile.avatar.color

    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.profile_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PersonAvatar(preview, size = 112.dp, contentDescription = stringResource(R.string.profile_photo_description))
                Spacer(Modifier.height(8.dp))
                Text(name.ifBlank { profile.displayName }, style = MaterialTheme.typography.headlineSmall)
                if (bio.isNotBlank()) {
                    Text(bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.onboarding_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(ProfileRepository.BIO_MAX) },
                    label = { Text(stringResource(R.string.profile_bio)) },
                    placeholder = { Text(stringResource(R.string.profile_bio_placeholder)) },
                    supportingText = { Text("${bio.length}/${ProfileRepository.BIO_MAX}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            SectionTitle(stringResource(R.string.profile_avatar))
            MeadowCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_avatar_emoji), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowerAvatars.forEach { option ->
                        Choice(selected = option == emoji, label = stringResource(R.string.flower_picker_item, option), onClick = { emoji = option }) {
                            PersonAvatar(Avatar(option, color, null), size = 44.dp)
                        }
                    }
                }
                Text(stringResource(R.string.profile_avatar_color), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarColor.entries.forEach { option ->
                        val swatch = if (LocalDarkTheme.current) option.dark else option.light
                        Choice(selected = option.key == color, label = option.key, onClick = { color = option.key }) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(swatch))
                        }
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !photo.busy,
                    ) { Text(stringResource(if (photo.busy) R.string.profile_photo_uploading else R.string.profile_photo_choose)) }
                    if (profile.avatar.photoUrl != null) {
                        TextButton(onClick = { photo.run { AppGraph.profiles.removePhoto() } }, enabled = !photo.busy) {
                            Text(stringResource(R.string.profile_photo_remove))
                        }
                    }
                }
                Text(
                    stringResource(R.string.profile_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ErrorNote(photo.error, Modifier.padding(top = 8.dp))
            }

            ErrorNote(save.error)
            Button(
                onClick = { save.run(successMessage = saved, onSuccess = celebrate) { AppGraph.profiles.update(name, emoji, color, bio) } },
                enabled = changed && name.isNotBlank() && !save.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.action_save)) }

            SectionTitle(stringResource(R.string.profile_theme))
            AppearancePicker()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Live previews of each theme, plus light / dark / system. Changes apply at once. */
@Composable
private fun AppearancePicker() {
    val themeKey by AppGraph.store.theme.collectAsStateWithLifecycle()
    val darkKey by AppGraph.store.darkMode.collectAsStateWithLifecycle()
    val current = GardenTheme.fromKey(themeKey)
    val darkMode = DarkMode.fromKey(darkKey)
    val action = rememberAction()

    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
        GardenTheme.entries.forEach { theme ->
            MeadowTheme(theme, darkMode) {
                ThemeCard(theme, selected = theme == current, modifier = Modifier.weight(1f)) {
                    action.run { AppGraph.profiles.setAppearance(theme.key, darkMode.key) }
                }
            }
        }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        DarkMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = mode == darkMode,
                onClick = { action.run { AppGraph.profiles.setAppearance(current.key, mode.key) } },
                shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size),
            ) { Text(stringResource(mode.label)) }
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, Garden.colors.petalPink).forEach {
                Box(Modifier.size(12.dp).clip(CircleShape).background(it))
            }
        }
        BloomFlower(1f, Garden.colors.petalPink, Modifier.size(44.dp, 60.dp), sway = false, growIn = false)
        Text("${theme.emoji} $label", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** A selectable item with a ring when chosen. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .padding(3.dp),
    ) { content() }
}
