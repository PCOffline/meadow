package dev.pcoffline.meadow.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.pcoffline.meadow.AppGraph
import dev.pcoffline.meadow.BuildConfig
import dev.pcoffline.meadow.R
import dev.pcoffline.meadow.data.FriendlyException
import dev.pcoffline.meadow.notifications.Notifier
import dev.pcoffline.meadow.usage.packageArchiveInfo
import dev.pcoffline.meadow.usage.packageInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String, val update: AvailableUpdate?) : UpdateState
}

/**
 * Keeps Meadow up to date from its GitHub releases.
 *
 * Checks read the public releases API (no account or token). Downloads are verified against
 * GitHub's published SHA-256 and must be signed with the same key as the installed app;
 * Android's installer enforces the signature too and always asks before installing.
 */
class Updater(
    private val context: Context,
    private val notifier: () -> Notifier = { AppGraph.notifier },
    /** How long to wait for Android's answer once the update is handed over. */
    private val installTimeoutMs: Long = INSTALL_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * Asks GitHub for the latest release. Returns the update, or null if Meadow is current.
     * While an update is already downloading or installing, that one is returned untouched.
     */
    suspend fun check(): AvailableUpdate? {
        when (val current = state.value) {
            is UpdateState.Downloading -> return current.update
            is UpdateState.Installing -> return current.update
            else -> Unit
        }
        _state.value = UpdateState.Checking
        return try {
            val update = withContext(Dispatchers.IO) { fetchLatest() }
            _state.value = update?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            update
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: IOException) {
            _state.value = UpdateState.Failed(context.getString(R.string.error_offline), null)
            null
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(context.getString(R.string.update_failed_check), null)
            null
        }
    }

    /** Checks in the background, so leaving the screen doesn't leave the check hanging. */
    fun checkInBackground() {
        AppGraph.scope.launch { check() }
    }

    private var installJob: Job? = null

    /**
     * Downloads and installs in the background: leaving the screen doesn't stop it, and a second
     * tap while one is running does nothing.
     */
    @Synchronized
    fun startInstall(update: AvailableUpdate) {
        if (installJob?.isActive == true) return
        installJob = AppGraph.scope.launch { install(update) }
    }

    /** True when Android lets Meadow install updates; otherwise send people to [installPermissionIntent]. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Downloads, verifies and hands the update to Android's installer, which asks to confirm. */
    private suspend fun install(update: AvailableUpdate) {
        try {
            val apk = withContext(Dispatchers.IO) { download(update) }
            withContext(Dispatchers.IO) { verify(apk, update) }
            installing(update)
            withContext(Dispatchers.IO) { commit(apk) }
        } catch (e: FriendlyException) {
            _state.value = UpdateState.Failed(context.getString(e.messageRes, *e.formatArgs), update)
        } catch (e: CancellationException) {
            _state.value = UpdateState.Available(update)
            throw e
        } catch (e: IOException) {
            _state.value = UpdateState.Failed(context.getString(R.string.error_offline), update)
        } catch (e: Exception) {
            // The installer refused (permission revoked meanwhile, storage full, ...).
            _state.value = UpdateState.Failed(context.getString(R.string.update_failed_install), update)
        }
    }

    @Volatile private var installTimeout: Job? = null

    /**
     * The update is with Android's installer. Its answer may never come (the confirmation was never
     * seen, or the person walked away from it), so after a while the update is offered again rather
     * than waiting for ever, which would also keep [check] from looking for anything newer.
     */
    internal fun installing(update: AvailableUpdate) {
        _state.value = UpdateState.Installing(update)
        installTimeout?.cancel()
        installTimeout = AppGraph.scope.launch {
            delay(installTimeoutMs)
            _state.compareAndSet(UpdateState.Installing(update), UpdateState.Available(update))
        }
    }

    /**
     * Called by [InstallResultReceiver] when Android needs the person to confirm the install. Since
     * Android 10 an app may only open the confirmation itself while it's on screen; otherwise it
     * waits in a notification that opens it when tapped.
     */
    internal fun onConfirmNeeded(confirm: Intent?, inForeground: Boolean = appInForeground()) {
        if (confirm == null) {
            onInstallResult(false, null)
            return
        }
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = inForeground && runCatching { context.startActivity(confirm) }.isSuccess
        if (!opened) notifier().confirmUpdate(confirm)
    }

    private fun appInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** Called by [InstallResultReceiver] when Android finishes (or gives up on) the install. */
    internal fun onInstallResult(success: Boolean, message: String?) {
        val update = (state.value as? UpdateState.Installing)?.update
        installTimeout?.cancel()
        notifier().cancel(Notifier.UPDATE_ID)
        updatesDir().deleteRecursively()
        if (!success) {
            _state.value = UpdateState.Failed(message ?: context.getString(R.string.update_failed_install), update)
        }
    }

    private fun fetchLatest(): AvailableUpdate? {
        val connection = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Meadow/$currentVersion")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            return when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    Releases.updateFrom(Releases.parse(body), currentVersion)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null // No releases yet.
                else -> throw IOException("GitHub answered ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(update: AvailableUpdate): File {
        val dir = updatesDir().apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "meadow-${update.version}.apk")
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Meadow/$currentVersion")
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Download answered ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            _state.value = UpdateState.Downloading(update, 0f)
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) _state.value = UpdateState.Downloading(update, (copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    private fun verify(apk: File, update: AvailableUpdate) {
        update.sha256?.let { expected ->
            if (sha256(apk) != expected) throw FriendlyException(R.string.update_failed_checksum)
        }
        val pm = context.packageManager
        val archive = pm.packageArchiveInfo(apk.path, signatureFlags())
            ?: throw FriendlyException(R.string.update_failed_checksum)
        if (archive.packageName != context.packageName) throw FriendlyException(R.string.update_failed_signature)
        val mine = signatures(pm.packageInfo(context.packageName, signatureFlags()))
        val theirs = signatures(archive)
        // Some Android versions can't read an archive's signatures; the installer still checks them.
        if (theirs.isNotEmpty() && theirs != mine) throw FriendlyException(R.string.update_failed_signature)
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("meadow.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                // Mutable so the installer can attach the result; the intent is explicit.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun updatesDir() = File(context.cacheDir, "updates")

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return raw.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    companion object {
        /** Long enough to read and answer Android's confirmation. */
        const val INSTALL_TIMEOUT_MS = 2 * 60_000L

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
}

/** Receives the installer's progress: asks the person to confirm, or reports the outcome. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                AppGraph.updater.onConfirmNeeded(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> AppGraph.updater.onInstallResult(true, null)
            else -> AppGraph.updater.onInstallResult(
                false,
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) context.getString(R.string.update_cancelled) else null,
            )
        }
    }
}
