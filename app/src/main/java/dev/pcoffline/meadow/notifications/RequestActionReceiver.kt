package dev.pcoffline.meadow.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.Backend
import dev.pcoffline.meadow.data.runCatchingCancellable
import dev.pcoffline.meadow.ui.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lets Bumblebees answer a request straight from the notification. The answer is sent by an
 * expedited worker, since a broadcast receiver only has a few seconds to live.
 */
class RequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val request = answer(requestId, intent.getBooleanExtra(EXTRA_APPROVE, false), intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        WorkManager.getInstance(context).enqueueUniqueWork("answer-$requestId", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        internal const val EXTRA_REQUEST = "request_id"
        internal const val EXTRA_APPROVE = "approve"
        internal const val EXTRA_NOTIFICATION = "notification_id"

        /** The work that sends an answer: it waits for a connection, so an answer given offline isn't lost. */
        internal fun answer(requestId: String, approve: Boolean, notificationId: Int): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<AnswerRequestWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(EXTRA_REQUEST to requestId, EXTRA_APPROVE to approve, EXTRA_NOTIFICATION to notificationId))
                .build()

        fun intent(context: Context, requestId: String, notificationId: Int, approve: Boolean): PendingIntent {
            val intent = Intent(context, RequestActionReceiver::class.java)
                .putExtra(EXTRA_REQUEST, requestId)
                .putExtra(EXTRA_APPROVE, approve)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
            return PendingIntent.getBroadcast(
                context,
                (requestId + approve).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

class AnswerRequestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** Only used below Android 12, where expedited work runs as a short foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(
        ANSWER_NOTIFICATION_ID,
        AppGraph.notifier.guardianNotification(applicationContext.getString(R.string.request_sending_answer)),
    )

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(RequestActionReceiver.EXTRA_REQUEST) ?: return Result.failure()
        val approve = inputData.getBoolean(RequestActionReceiver.EXTRA_APPROVE, false)
        AppGraph.notifier.cancel(inputData.getInt(RequestActionReceiver.EXTRA_NOTIFICATION, 0))
        val result = runCatchingCancellable { AppGraph.requests.decide(requestId, approve) }
        val failure = result.exceptionOrNull()
        if (failure is Backend.CallFailed && failure.unreachable && runAttemptCount < 3) return Result.retry()
        val message = failure?.friendlyMessage(applicationContext)
            ?: applicationContext.getString(if (approve) R.string.request_sent_yes else R.string.request_sent_no)
        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
        return if (failure == null) Result.success() else Result.failure()
    }

    private companion object {
        const val ANSWER_NOTIFICATION_ID = 3
    }
}
