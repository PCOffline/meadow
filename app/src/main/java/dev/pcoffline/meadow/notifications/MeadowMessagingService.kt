package dev.pcoffline.meadow.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.AppGraph

class MeadowMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isNotEmpty()) AppGraph.notifier.fromPush(message.data)
    }

    override fun onNewToken(token: String) {
        if (AppGraph.backendReady) AppGraph.sync.onNewToken(token)
    }
}
