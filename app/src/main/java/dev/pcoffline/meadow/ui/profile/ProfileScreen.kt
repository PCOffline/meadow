@file:OptIn(ExperimentalLayoutApi::class)

package dev.pcoffline.meadow.ui.profile

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
import dev.pcoffline.meadow.data.Avatar
import dev.pcoffline.meadow.data.FlowerAvatars
import dev.pcoffline.meadow.data.ProfileRepository
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.LocalCelebrate
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.PersonAvatar
import dev.pcoffline.meadow.ui.components.SectionTitle
import dev.pcoffline.meadow.ui.limits.BackTopBar
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.AvatarColor
import dev.pcoffline.meadow.ui.theme.LocalDarkTheme

/** What Save sends: the card's text and look, and whether the photo goes in favour of the flower. */
data class ProfileEdit(val name: String, val emoji: String, val color: String, val bio: String, val removePhoto: Boolean)

private suspend fun saveProfile(edit: ProfileEdit) {
    AppGraph.profiles.update(edit.name, edit.emoji, edit.color, edit.bio)
    if (edit.removePhoto) AppGraph.profiles.removePhoto()
}

@Composable
fun ProfileScreen(
    profile: UserProfile,
    onBack: () -> Unit,
    onSave: suspend (ProfileEdit) -> Unit = ::saveProfile,
    onRemovePhoto: suspend () -> Unit = { AppGraph.profiles.removePhoto() },
) {
    var name by rememberSaveable { mutableStateOf(profile.displayName) }
    var bio by rememberSaveable { mutableStateOf(profile.bio) }
    var emoji by rememberSaveable { mutableStateOf(profile.avatar.emoji) }
    var color by rememberSaveable { mutableStateOf(profile.avatar.color) }
    val hasPhoto = profile.avatar.photoUrl != null
    // A photo, while there is one, until a flower or colour is picked instead.
    var usePhoto by rememberSaveable(hasPhoto) { mutableStateOf(hasPhoto) }
    val save = rememberAction()
    val photo = rememberAction()
    val celebrate = LocalCelebrate.current
    val saved = stringResource(R.string.profile_saved)
    val photoSaved = stringResource(R.string.profile_photo_saved)
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo.run(successMessage = photoSaved, onSuccess = { usePhoto = true }) { AppGraph.profiles.setPhoto(uri) }
    }
    val dropPhoto = hasPhoto && !usePhoto
    val preview = Avatar(emoji, color, profile.avatar.photoUrl.takeIf { usePhoto })
    val changed = name != profile.displayName || bio != profile.bio || emoji != profile.avatar.emoji ||
        color != profile.avatar.color || dropPhoto

    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.profile_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PersonAvatar(preview, size = 112.dp, contentDescription = stringResource(R.string.profile_photo_description))
                Spacer(Modifier.height(8.dp))
                Text(name.ifBlank { profile.displayName }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
                if (bio.isNotBlank()) {
                    Text(bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                // The photo is changed where it's seen, right under it.
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !photo.busy,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    photo.busy -> R.string.profile_photo_uploading
                                    usePhoto -> R.string.profile_photo_change
                                    else -> R.string.profile_photo_choose
                                },
                            ),
                        )
                    }
                    if (usePhoto) {
                        OutlinedButton(
                            onClick = { photo.run(onSuccess = { usePhoto = false }) { onRemovePhoto() } },
                            enabled = !photo.busy,
                        ) { Text(stringResource(R.string.profile_photo_remove)) }
                    }
                }
                ErrorNote(photo.error, Modifier.padding(top = 8.dp))
            }

            MeadowCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.onboarding_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(ProfileRepository.BIO_MAX) },
                    label = { Text(stringResource(R.string.profile_bio)) },
                    placeholder = { Text(stringResource(R.string.profile_bio_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    supportingText = { Text("${bio.length}/${ProfileRepository.BIO_MAX}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            SectionTitle(stringResource(R.string.profile_avatar))
            MeadowCard(Modifier.fillMaxWidth()) {
                // Picking a flower or a colour uses the flower instead of the photo, from Save on.
                Text(stringResource(R.string.profile_avatar_emoji), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.padding(top = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowerAvatars.forEach { option ->
                        Choice(
                            selected = !usePhoto && option == emoji,
                            label = stringResource(R.string.flower_picker_item, option),
                            onClick = {
                                emoji = option
                                usePhoto = false
                            },
                        ) {
                            PersonAvatar(Avatar(option, color, null), size = 44.dp)
                        }
                    }
                }
                Text(stringResource(R.string.profile_avatar_color), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp))
                FlowRow(Modifier.padding(top = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarColor.entries.forEach { option ->
                        val swatch = if (LocalDarkTheme.current) option.dark else option.light
                        Choice(
                            selected = !usePhoto && option.key == color,
                            label = stringResource(option.label),
                            onClick = {
                                color = option.key
                                usePhoto = false
                            },
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(swatch))
                        }
                    }
                }
            }

            ErrorNote(save.error)
            Button(
                onClick = { save.run(successMessage = saved, onSuccess = celebrate) { onSave(ProfileEdit(name, emoji, color, bio, removePhoto = dropPhoto)) } },
                enabled = changed && name.isNotBlank() && !save.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.action_save)) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A selectable item with a ring when chosen. */
@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
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
