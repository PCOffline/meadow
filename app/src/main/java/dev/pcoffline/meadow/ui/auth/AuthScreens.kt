@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.pcoffline.meadow.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.FlowerAvatars
import dev.pcoffline.meadow.ui.components.BloomFlower
import dev.pcoffline.meadow.ui.components.ErrorNote
import dev.pcoffline.meadow.ui.components.FallingPetals
import dev.pcoffline.meadow.ui.components.FlowerAvatar
import dev.pcoffline.meadow.ui.components.MeadowCard
import dev.pcoffline.meadow.ui.components.SkyScene
import dev.pcoffline.meadow.ui.components.StalkBarChart
import dev.pcoffline.meadow.ui.components.pressBounce
import dev.pcoffline.meadow.ui.components.rememberSkyMood
import dev.pcoffline.meadow.ui.rememberAction
import dev.pcoffline.meadow.ui.theme.Garden
import kotlinx.coroutines.launch

private enum class Step { INTRO, NAME, SIGN_IN }

/**
 * First run. Nobody has to register to start: a name and a flower are enough. An email is
 * only asked for later, right before inviting an approver.
 */
@Composable
fun AuthScreen() {
    var step by rememberSaveable { mutableStateOf(Step.INTRO) }
    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            (slideInHorizontally(tween(380)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(380)))
                .togetherWith(slideOutHorizontally(tween(300)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(240)))
        },
        label = "onboarding",
    ) { current ->
        when (current) {
            Step.INTRO -> IntroStep(onStart = { step = Step.NAME }, onSignIn = { step = Step.SIGN_IN })
            Step.NAME -> NameStep(onBack = { step = Step.INTRO }, onSignIn = { step = Step.SIGN_IN })
            Step.SIGN_IN -> SignInStep(onBack = { step = Step.INTRO })
        }
    }
}

private data class IntroPage(val title: String, val body: String)

private val introPages = listOf(
    IntroPage("Plant gentle limits", "Choose how long each app may bloom every day. When its time is up, it rests until tomorrow."),
    IntroPage("Friends hold the watering can", "Only friends you choose can give you more time or loosen a limit. No quiet overrides."),
    IntroPage("Watch your days grow", "See your days, weeks and months take shape — and celebrate the calmer ones."),
)

@Composable
private fun IntroStep(onStart: () -> Unit, onSignIn: () -> Unit) {
    val pager = rememberPagerState { introPages.size }
    val scope = rememberCoroutineScope()
    val mood = rememberSkyMood()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SkyScene(mood, Modifier.fillMaxWidth().height(340.dp))
        FallingPetals(Modifier.fillMaxSize(), count = 10)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                "Meadow",
                style = MaterialTheme.typography.displaySmall,
                color = mood.onSky,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp),
            )
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(260.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        when (index) {
                            0 -> PlantIllustration()
                            1 -> FriendsIllustration()
                            else -> GrowthIllustration()
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(introPages[index].title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        introPages[index].body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            PageDots(count = introPages.size, current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            val last = pager.currentPage == introPages.lastIndex
            Button(
                onClick = { if (last) onStart() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().height(54.dp),
            ) { Text(if (last) "Let's begin 🌱" else "Next", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
                Text("I already have a garden")
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "dot")
            Box(
                Modifier.height(8.dp).width(width).clip(RoundedCornerShape(50))
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun PlantIllustration() {
    val garden = Garden.colors
    Row(verticalAlignment = Alignment.Bottom) {
        BloomFlower(0.55f, garden.petalLavender, Modifier.size(76.dp, 120.dp))
        BloomFlower(1f, garden.petalPink, Modifier.size(110.dp, 180.dp), petals = 7)
        BloomFlower(0.2f, garden.petalHoney, Modifier.size(76.dp, 120.dp), petals = 5)
    }
}

@Composable
private fun FriendsIllustration() {
    val transition = rememberInfiniteTransition(label = "heart")
    val beat by transition.animateFloat(
        0.9f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 40.dp)) {
        FlowerAvatar("🌻", size = 96.dp, tint = MaterialTheme.colorScheme.primaryContainer)
        Text("💗", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 14.dp).graphicsLayer { scaleX = beat; scaleY = beat })
        FlowerAvatar("🌷", size = 96.dp, tint = MaterialTheme.colorScheme.secondaryContainer)
    }
}

@Composable
private fun GrowthIllustration() {
    MeadowCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        StalkBarChart(
            values = listOf(140f, 120f, 95f, 110f, 80f, 60f, 45f),
            labels = listOf("M", "T", "W", "T", "F", "S", "S"),
            highlightIndex = 6,
            referenceLine = 93f,
            height = 150.dp,
        )
    }
}

@Composable
private fun NameStep(onBack: () -> Unit, onSignIn: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var flower by rememberSaveable { mutableStateOf(FlowerAvatars.first()) }
    val action = rememberAction()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("← Back") }
        BigFlower(flower)
        Text("Who's tending this garden?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text("Your name") }, singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowerPicker(flower) { flower = it }
        ErrorNote(action.error)
        Button(
            onClick = { action.run { AppGraph.auth.startWithoutEmail(name, flower) } },
            enabled = !action.busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(if (action.busy) "Planting…" else "Plant my garden 🌱", style = MaterialTheme.typography.titleMedium) }
        Text(
            "No email needed to start. You'll add one before inviting your first Petal Pal, so your garden can't get lost.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onSignIn) { Text("I already have a garden") }
    }
}

/** The chosen flower, popping a little whenever it changes. */
@Composable
private fun BigFlower(flower: String) {
    AnimatedContent(
        targetState = flower,
        transitionSpec = {
            (fadeIn(tween(200)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.45f), initialScale = 0.4f))
                .togetherWith(fadeOut(tween(120)) + androidx.compose.animation.scaleOut(targetScale = 0.6f))
        },
        label = "bigFlower",
    ) { f -> FlowerAvatar(f, size = 112.dp) }
}

@Composable
private fun SignInStep(onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val action = rememberAction()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text("Welcome back 🌼", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sign in with the email you added to your garden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmailFields(email, { email = it }, password, { password = it })
        ErrorNote(action.error)
        Button(
            onClick = { action.run { AppGraph.auth.signIn(email, password) } },
            enabled = !action.busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(if (action.busy) "One moment…" else "Come on in") }
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

@Composable
private fun EmailFields(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit) {
    OutlinedTextField(
        value = email, onValueChange = onEmail,
        label = { Text("Email") }, singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password, onValueChange = onPassword,
        label = { Text("Password") }, singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Adds an email to a garden started without one. */
@Composable
fun SecureAccountCard(modifier: Modifier = Modifier, reason: String) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val action = rememberAction()
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("🔐 Keep your garden safe", style = MaterialTheme.typography.titleMedium)
        Text(reason, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EmailFields(email, { email = it }, password, { password = it })
        }
        ErrorNote(action.error, Modifier.padding(top = 8.dp))
        Button(
            onClick = { action.run(successMessage = "Your garden is safe 🌿") { AppGraph.auth.addEmail(email, password) } },
            enabled = !action.busy && email.isNotBlank() && password.length >= 6,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text(if (action.busy) "Saving…" else "Add email") }
    }
}

@Composable
fun FlowerPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pick your flower", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FlowerAvatars) { f ->
                val chosen = f == selected
                val scale by animateFloatAsState(
                    if (chosen) 1.12f else 1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "flowerScale",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(50.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .pressBounce(interaction)
                        .clip(CircleShape)
                        .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(f) },
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigFlower(flower)
        Text("Almost there", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text("Your name") }, singleLine = true,
            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        FlowerPicker(flower) { flower = it }
        ErrorNote(action.error)
        Button(
            onClick = { action.run { AppGraph.auth.createProfile(name, flower) } },
            enabled = !action.busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Continue") }
        TextButton(onClick = { action.run { AppGraph.auth.signOut() } }) { Text("Use another account") }
    }
}
