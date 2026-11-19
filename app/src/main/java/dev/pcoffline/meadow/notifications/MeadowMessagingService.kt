package dev.pcoffline.meadow.notifications

import android.annotation.SuppressLint
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.pcoffline.meadow.AppGraph

// Push goes to the installation ID (register/onRegistered), not FCM tokens, so there's no
// onNewToken to implement; lint's check predates that API.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MeadowMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty()) return
        val signedInUid = if (AppGraph.backendReady) AppGraph.backend.uid else null
        AppGraph.notifier.fromPush(message.data, signedInUid)
    }

    override fun onRegistered(installationId: String) {
        if (AppGraph.backendReady) AppGraph.sync.onPushRegistered(installationId)
    }
}
