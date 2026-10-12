@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.pcoffline.meadow.ui.auth

import dev.pcoffline.meadow.ui.components.rememberReducedMotion
import java.util.Locale
import java.time.DayOfWeek
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.ui.components.asHeading
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
import dev.pcoffline.meadow.ui.legal.ConsentLinks
import dev.pcoffline.meadow.ui.legal.LegalDialog
import dev.pcoffline.meadow.ui.legal.LegalDoc
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
    // Back walks back through onboarding instead of closing the app on a half-typed name.
    BackHandler(enabled = step != Step.INTRO) { step = Step.INTRO }
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

private data class IntroPage(@StringRes val title: Int, @StringRes val body: Int)

private val introPages = listOf(
    IntroPage(R.string.intro_plant_title, R.string.intro_plant_body),
    IntroPage(R.string.intro_bees_title, R.string.intro_bees_body),
    IntroPage(R.string.intro_growth_title, R.string.intro_growth_body),
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
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = mood.onSky,
                modifier = Modifier.asHeading().align(Alignment.CenterHorizontally).padding(top = 20.dp),
            )
            HorizontalPager(pager, Modifier.weight(1f)) { index ->
                // Scrolls when large text or a short screen can't fit the page.
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
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
                    Text(stringResource(introPages[index].title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.asHeading())
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(introPages[index].body),
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
            ) { Text(stringResource(if (last) R.string.intro_begin else R.string.intro_next), style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onSignIn, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) {
                Text(stringResource(R.string.intro_have_garden))
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
    val beat = if (rememberReducedMotion()) null else rememberInfiniteTransition(label = "heart").animateFloat(
        0.9f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 40.dp)) {
        FlowerAvatar("🌻", size = 96.dp, tint = MaterialTheme.colorScheme.primaryContainer)
        Text("🐝", fontSize = 34.sp, modifier = Modifier.padding(horizontal = 14.dp).graphicsLayer { val b = beat?.value ?: 1f; scaleX = b; scaleY = b })
        FlowerAvatar("🌷", size = 96.dp, tint = MaterialTheme.colorScheme.secondaryContainer)
    }
}

@Composable
private fun GrowthIllustration() {
    MeadowCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        StalkBarChart(
            values = listOf(140f, 120f, 95f, 110f, 80f, 60f, 45f),
            labels = DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) },
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
    var agreed by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf<LegalDoc?>(null) }
    val action = rememberAction()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text(stringResource(R.string.action_back)) }
        BigFlower(flower)
        Text(stringResource(R.string.onboarding_name_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.asHeading())
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text(stringResource(R.string.onboarding_name_label)) }, singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowerPicker(flower) { flower = it }
        ConsentCheckbox(agreed, onChange = { agreed = it }, onOpen = { reading = it })
        ErrorNote(action.error)
        Button(
            onClick = {
                action.run {
                    AppGraph.auth.startWithoutEmail(name, flower, LegalDoc.CURRENT_VERSION)
                    AppGraph.store.acceptedTermsVersion = LegalDoc.CURRENT_VERSION
                }
            },
            enabled = !action.busy && name.isNotBlank() && agreed,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(if (action.busy) R.string.onboarding_planting else R.string.onboarding_plant), style = MaterialTheme.typography.titleMedium) }
        Text(
            stringResource(R.string.onboarding_no_email),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onSignIn) { Text(stringResource(R.string.intro_have_garden)) }
    }
    reading?.let { LegalDialog(it, onDismiss = { reading = null }) }
}

/** "I've read and agree…" with links to read both documents first. */
@Composable
fun ConsentCheckbox(agreed: Boolean, onChange: (Boolean) -> Unit, onOpen: (LegalDoc) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().toggleable(value = agreed, role = Role.Checkbox, onValueChange = onChange),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.legal_agree), style = MaterialTheme.typography.bodyMedium)
        }
        ConsentLinks(onOpen)
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.asHeading())
        Text(
            stringResource(R.string.sign_in_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SignInOptions(
            passwordButton = R.string.sign_in_button,
            linkSent = R.string.auth_email_link_sent,
            offerPasswordReset = true,
            onPassword = { email, password -> AppGraph.auth.signIn(email, password) },
        )
    }
}

/**
 * The ways into a garden: a link emailed to you (no password), or a password. Used to sign in,
 * and to keep a garden started without an email: then they attach to that garden.
 */
@Composable
private fun SignInOptions(
    @StringRes passwordButton: Int,
    @StringRes linkSent: Int,
    offerPasswordReset: Boolean,
    onPassword: suspend (email: String, password: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var sentTo by rememberSaveable { mutableStateOf<String?>(null) }
    val action = rememberAction()
    val resetSent = stringResource(R.string.sign_in_reset_sent)
    val canSendLink = !action.busy && email.isNotBlank()
    val canUsePassword = !action.busy && email.isNotBlank() && password.isNotBlank()
    val sendLink = { action.run(onSuccess = { sentTo = email.trim() }) { AppGraph.auth.sendSignInLink(email) } }
    val usePasswordNow = { action.run { onPassword(email, password) } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = email, onValueChange = { email = it; sentTo = null },
            label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (usePassword) ImeAction.Next else ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSendLink) sendLink() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (usePassword) {
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text(stringResource(R.string.field_password)) }, singleLine = true,
                shape = RoundedCornerShape(18.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canUsePassword) usePasswordNow() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ErrorNote(action.error)
        sentTo?.let {
            Text(stringResource(linkSent, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        if (usePassword) {
            Button(
                onClick = usePasswordNow,
                enabled = canUsePassword,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(if (action.busy) R.string.sign_in_busy else passwordButton)) }
            if (offerPasswordReset) {
                TextButton(onClick = { action.run(successMessage = resetSent) { AppGraph.auth.sendPasswordReset(email) } }) {
                    Text(stringResource(R.string.sign_in_forgot))
                }
            }
            TextButton(onClick = { usePassword = false }) { Text(stringResource(R.string.auth_link_instead)) }
        } else {
            Button(
                onClick = sendLink,
                enabled = canSendLink,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.auth_email_link)) }
            TextButton(onClick = { usePassword = true }) { Text(stringResource(R.string.auth_password_instead)) }
        }
    }
}

/** Keeps a garden started without an email: attaches an email link or a password to it. */
@Composable
fun SecureAccountCard(@StringRes reason: Int, modifier: Modifier = Modifier) {
    MeadowCard(modifier.fillMaxWidth(), tint = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(stringResource(R.string.secure_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.asHeading())
        Text(stringResource(reason), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        SignInOptions(
            passwordButton = R.string.secure_button,
            linkSent = R.string.secure_link_sent,
            offerPasswordReset = false,
            onPassword = { email, password -> AppGraph.auth.addEmail(email, password) },
        )
    }
}

/** Finishes signing in from an emailed link, asking which address it went to if this phone doesn't know. */
@Composable
fun EmailLinkDialog(link: String) {
    val known = remember { AppGraph.auth.linkEmail }
    var email by rememberSaveable { mutableStateOf(known.orEmpty()) }
    val action = rememberAction()
    val done = stringResource(R.string.link_done)
    // Tried once on its own; a rotation mustn't spend the one-time link a second time.
    var tried by rememberSaveable(link) { mutableStateOf(false) }
    LaunchedEffect(link) {
        if (known != null && !tried) {
            tried = true
            action.run(successMessage = done) { AppGraph.auth.finishSignInLink(link, known) }
        }
    }
    // If the address this phone remembers didn't work, the link may have gone to another one.
    val askEmail = known == null || action.error != null
    AlertDialog(
        onDismissRequest = { if (!action.busy) AppGraph.auth.dismissLink() },
        title = { Text(stringResource(R.string.link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (askEmail) {
                    if (known == null) Text(stringResource(R.string.link_ask_email))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(stringResource(R.string.field_email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (action.busy) {
                    Text(stringResource(R.string.link_busy))
                }
                ErrorNote(action.error)
            }
        },
        confirmButton = {
            if (askEmail || (!action.busy && tried)) {
                TextButton(
                    onClick = { action.run(successMessage = done) { AppGraph.auth.finishSignInLink(link, email) } },
                    enabled = !action.busy && email.isNotBlank(),
                ) { Text(stringResource(R.string.link_continue)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { AppGraph.auth.dismissLink() }, enabled = !action.busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun FlowerPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.flower_picker_title), style = MaterialTheme.typography.labelLarge)
        LazyRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FlowerAvatars) { f ->
                val chosen = f == selected
                val pickLabel = stringResource(R.string.flower_picker_item, f)
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
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClickLabel = pickLabel,
                            role = Role.RadioButton,
                        ) { onSelect(f) }
                        .semantics { this.selected = chosen },
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
    var agreed by rememberSaveable { mutableStateOf(false) }
    var reading by remember { mutableStateOf<LegalDoc?>(null) }
    val action = rememberAction()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BigFlower(flower)
        Text(stringResource(R.string.profile_setup_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.asHeading())
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(30) },
            label = { Text(stringResource(R.string.onboarding_name_label)) }, singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowerPicker(flower) { flower = it }
        ConsentCheckbox(agreed, onChange = { agreed = it }, onOpen = { reading = it })
        ErrorNote(action.error)
        Button(
            onClick = {
                action.run {
                    AppGraph.auth.createProfile(name, flower, LegalDoc.CURRENT_VERSION)
                    AppGraph.store.acceptedTermsVersion = LegalDoc.CURRENT_VERSION
                }
            },
            enabled = !action.busy && name.isNotBlank() && agreed,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text(stringResource(R.string.profile_setup_continue)) }
        TextButton(onClick = { action.run { AppGraph.auth.signOut() } }) { Text(stringResource(R.string.profile_setup_other_account)) }
    }
    reading?.let { LegalDialog(it, onDismiss = { reading = null }) }
}
