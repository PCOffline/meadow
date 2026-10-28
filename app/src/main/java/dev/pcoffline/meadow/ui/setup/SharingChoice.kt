package dev.pcoffline.meadow.ui.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.AppLimit
import dev.pcoffline.meadow.data.Days
import dev.pcoffline.meadow.data.Privacy
import dev.pcoffline.meadow.data.Progress
import dev.pcoffline.meadow.data.SharedAppUsage
import dev.pcoffline.meadow.data.SharedUsage
import dev.pcoffline.meadow.data.Sharing
import dev.pcoffline.meadow.data.UserProfile
import dev.pcoffline.meadow.ui.circle.FriendPage
import dev.pcoffline.meadow.ui.settings.PrivacySwitches
import dev.pcoffline.meadow.ui.settings.rememberPrivacyChoices

/** Made-up numbers for the example page, so each choice shows what it reveals before there's a day to share. */
internal object SharingExample {
    val today = SharedUsage(
        Days.todayKey(),
        totalMinutes = 128,
        apps = listOf(
            SharedAppUsage("com.google.android.youtube", "YouTube", 52),
            SharedAppUsage("com.instagram.android", "Instagram", 41),
            SharedAppUsage("com.google.android.apps.maps", "Maps", 18),
        ),
    )
    val limits = listOf(AppLimit("com.google.android.youtube", "YouTube", 60, 0), AppLimit("com.instagram.android", "Instagram", 45, 0))
    val progress = Progress(weekChange = -15, calmerWeeks = 2, keptDays = 5, limitDays = 7)
}

/**
 * The privacy switches during setup, with an example of the person's page as a Bumblebee would
 * see it, redrawn as each switch moves. Each change is saved as in Settings; [onDone] settles it.
 */
@Composable
internal fun SharingChoice(
    profile: UserProfile,
    onDone: () -> Unit,
    save: suspend (Privacy) -> Unit = { AppGraph.profiles.setPrivacy(it) },
) {
    val choices = rememberPrivacyChoices(profile.privacy, save)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column { PrivacySwitches(choices) }
        Text(
            stringResource(R.string.setup_sharing_example),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Framed, so it reads as a look at another screen rather than part of this one.
        val frame = RoundedCornerShape(20.dp)
        Column(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, frame).padding(12.dp)) {
            val page = Sharing.page(
                profile.copy(privacy = choices.current),
                { SharingExample.today },
                { SharingExample.progress },
                SharingExample.limits,
            )
            FriendPage(page)
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(stringResource(R.string.setup_sharing_done))
        }
    }
}
