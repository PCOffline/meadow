package dev.pcoffline.meadow

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import dev.pcoffline.meadow.usage.queryActivities
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end, against the Auth emulator: someone with a garden asks for a sign-in link, and the
 * link, in the form Firebase emails it (wrapped in a firebaseapp.com address), opens Meadow and
 * signs them in. Whether Android verifies that domain for the app can only be seen on a phone
 * with the real project.
 */
@RunWith(AndroidJUnit4::class)
class SignInLinkTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    @Test
    fun anEmailedLinkOpensMeadowAndSignsIn() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(R.string.intro_have_garden)
            node(R.string.intro_have_garden).performClick()
            typeInto(hasSetTextAction() and hasText(text(R.string.field_email)), sam.email)
            node(R.string.auth_email_link).performClick()
            waitForText(text(R.string.auth_email_link_sent, sam.email))

            val link = Uri.parse("https://${BuildConfig.AUTH_LINK_HOST}/__/auth/links?link=${Uri.encode(emailedLink(sam.email))}")
            val open = Intent(Intent.ACTION_VIEW, link).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)
            assertEquals(MainActivity::class.java.name, app.packageManager.queryActivities(open).single().activityInfo.name)
            app.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            // Signed in to Sam's garden; on a phone new to Meadow, setup comes next.
            waitFor(R.string.link_done)
            assertEquals(sam.uid, FirebaseAuth.getInstance().currentUser?.uid)
            waitFor(R.string.setup_later)
        }
    }
}
