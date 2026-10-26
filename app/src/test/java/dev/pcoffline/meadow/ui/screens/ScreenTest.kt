package dev.pcoffline.meadow.ui.screens

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.a11y.assertAccessible
import dev.pcoffline.meadow.ui.LocalSnackbar
import dev.pcoffline.meadow.ui.theme.DarkMode
import dev.pcoffline.meadow.ui.theme.GardenTheme
import dev.pcoffline.meadow.ui.theme.MeadowTheme
import org.junit.Before
import org.junit.Rule

/** Shared set-up for screen tests: a phone-sized screen, the app graph, strings and a11y checks. */
abstract class ScreenTest {
    /**
     * Screens sit on the page colour in the app (MeadowRoot's Scaffold), so they do here too. The
     * app always gives them somewhere for snackbars, so this does too (without drawing it).
     */
    @Composable
    protected fun AppBackground(content: @Composable () -> Unit) {
        val snackbar = remember { SnackbarHostState() }
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }

    @get:Rule val compose: ComposeContentTestRule = createComposeRule()
    protected val app: Application = ApplicationProvider.getApplicationContext()

    @Before
    fun initGraph() = AppGraph.init(app)

    protected fun text(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    protected fun node(@StringRes id: Int, vararg args: Any) = compose.onNodeWithText(text(id, *args))

    protected fun show(theme: GardenTheme = GardenTheme.MEADOW, mode: DarkMode = DarkMode.LIGHT, content: @Composable () -> Unit) =
        compose.setContent { MeadowTheme(theme, mode) { AppBackground(content) } }

    protected fun assertAccessible(requireHeading: Boolean = true) = compose.assertAccessible(compose.density, requireHeading)
}
