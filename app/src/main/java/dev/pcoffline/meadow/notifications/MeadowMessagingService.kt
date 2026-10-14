package dev.pcoffline.meadow.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.data.Trace

class MeadowMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Trace.log { "push received: ${message.data}" }
        if (message.data.isEmpty()) return
        val signedInUid = if (AppGraph.backendReady) AppGraph.backend.uid else null
        AppGraph.notifier.fromPush(message.data, signedInUid)
    }

    override fun onRegistered(installationId: String) {
        Trace.log { "push registered as $installationId" }
        if (AppGraph.backendReady) AppGraph.sync.onPushRegistered(installationId)
    }
}
