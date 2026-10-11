@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.pcoffline.meadow.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.FlowerAvatars
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.MeadowHills
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.Garden

@Composable
private fun GardenHeader(title: String, subtitle: String) {
    val garden = Garden.colors
    Box(Modifier.fillMaxWidth().height(260.dp).background(garden.sky)) {
        MeadowHills(Modifier.fillMaxSize())
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BloomFlower(0.85f, garden.petalLavender, Modifier.size(70.dp, 110.dp))
            BloomFlower(1f, garden.petalPink, Modifier.size(96.dp, 150.dp), petals = 7)
            BloomFlower(0.95f, garden.petalHoney, Modifier.size(70.dp, 110.dp), petals = 5)
        }
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AuthScreen() {
    var signUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var flower by rememberSaveable { mutableStateOf(FlowerAvatars.first()) }
    val action = rememberAction()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        GardenHeader("Meadow", "Grow calmer days, together")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !signUp, onClick = { signUp = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Sign in") }
                SegmentedButton(
                    selected = signUp, onClick = { signUp = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("New here") }
            }
            if (signUp) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) },
                    label = { Text("What should friends call you?") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                FlowerPicker(flower) { flower = it }
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorNote(action.error)
            Button(
                onClick = {
                    action.run {
                        if (signUp) {
                            require(name.isNotBlank()) { "Tell us your name first." }
                            require(password.length >= 6) { "Use at least 6 characters for your password." }
                            AppGraph.auth.signUp(email, password)
                            AppGraph.auth.createProfile(name, flower)
                        } else {
                            AppGraph.auth.signIn(email, password)
                        }
                    }
                },
                enabled = !action.busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (action.busy) "One moment…" else if (signUp) "Plant my garden 🌱" else "Come on in 🌼") }
            if (!signUp) {
                TextButton(
                    onClick = {
                        action.run(successMessage = "Check your inbox for a reset link 💌") {
                            require(email.isNotBlank()) { "Enter your email first." }
                            AppGraph.auth.sendPasswordReset(email)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Forgot your password?") }
            }
        }
    }
}

@Composable
fun FlowerPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pick your flower", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FlowerAvatars) { f ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(
                            if (f == selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(f) },
                    contentAlignment = Alignment.Center,
                ) { Text(f, fontSize = 24.sp) }
            }
        }
    }
}

/** Shown when someone is signed in but their profile was never created. */
@Composable
fun ProfileSetupScreen() {
    var name by rememberSaveable { mutableStateOf("") }
    var flower by rememberSaveable { mutableStateOf(FlowerAvatars.first()) }
    val action = rememberAction()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        GardenHeader("Almost there", "Let's finish your garden")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(30) },
                label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            FlowerPicker(flower) { flower = it }
            ErrorNote(action.error)
            Button(
                onClick = { action.run { AppGraph.auth.createProfile(name, flower) } },
                enabled = !action.busy && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Continue") }
            TextButton(onClick = { action.run { AppGraph.auth.signOut() } }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Use another account")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Your name and flower are what your approvers will see.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
