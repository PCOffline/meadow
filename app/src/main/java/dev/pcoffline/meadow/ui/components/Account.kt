package dev.pcoffline.meadow.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Avatar

/** Who's signed in, and the way to their profile. The main screens' scaffold provides it. */
class AccountEntry(val avatar: Avatar, val open: () -> Unit)

val LocalAccount = compositionLocalOf<AccountEntry?> { null }

/** The person's own avatar: the way to their name, photo and look, in the same corner of every tab. */
@Composable
fun AccountButton() {
    val account = LocalAccount.current ?: return
    IconButton(onClick = account.open) {
        PersonAvatar(account.avatar, size = 36.dp, contentDescription = stringResource(R.string.profile_open))
    }
}

/** A tab's title, with [AccountButton] at its end. */
@Composable
fun TabTitle(title: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.asHeading().weight(1f))
        AccountButton()
    }
}
