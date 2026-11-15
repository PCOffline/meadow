package dev.pcoffline.meadow

import android.content.Intent
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pcoffline.meadow.data.ChatRepository
import dev.pcoffline.meadow.ui.navigation.DeepLinks
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end (#124): Meadow opened from a notification. A link to People opens the People tab, and
 * a link to a chat opens it on the People tab. Either way, the Garden tab then opens the garden.
 */
@RunWith(AndroidJUnit4::class)
class NotificationLinksJourneyTest : EndToEndTest() {
    private var sam: EmulatorFriend? = null

    @After
    fun closeSam() {
        sam?.close()
    }

    /** Opens Meadow as a notification with [link] does. */
    private fun fromNotification(link: String) =
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, link))

    /** The Garden tab, then the garden itself: its section and its one big button. */
    private fun backToTheGarden() {
        tab(R.string.tab_garden).performClick()
        waitFor(R.string.garden_title)
        compose.onNodeWithContentDescription(text(R.string.garden_plant)).assertExists()
    }

    @Test
    fun theGardenTabOpensTheGardenAfterANotificationOpenedAnotherScreen() {
        val sam = EmulatorFriend.create(app, "Sam", "🌻").also { sam = it }
        val chatId = ActivityScenario.launch(MainActivity::class.java).use {
            startAsGuest("Bee")
            ChatRepository.chatIdFor(acceptInviteFrom(sam, "Sam"), sam.uid)
        }

        // A request's notification opens People.
        fromNotification(DeepLinks.requests()).use {
            waitFor(R.string.circle_looking_after)
            backToTheGarden()
        }

        // A message's notification opens the chat, on the People tab: Back goes to People.
        sam.send(chatId, "Hello from the notification")
        fromNotification(DeepLinks.chat(chatId)).use {
            waitForText("Hello from the notification")
            compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
            waitFor(R.string.circle_looking_after)
            backToTheGarden()
        }
    }
}
