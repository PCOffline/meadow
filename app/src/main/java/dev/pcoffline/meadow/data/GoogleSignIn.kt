package dev.pcoffline.meadow.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.pcoffline.meadow.R

/** "Continue with Google", through Android's Credential Manager. */
object GoogleSignIn {
    /**
     * Shows Google's account picker and returns an ID token for Firebase. [context] must be an
     * Activity. Throws [androidx.credentials.exceptions.GetCredentialCancellationException] if
     * the person closes the picker.
     */
    suspend fun idToken(context: Context): String {
        val clientId = webClientId(context) ?: throw FriendlyException(R.string.error_google_unavailable)
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (_: NoCredentialException) {
            throw FriendlyException(R.string.error_google_no_account)
        }
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw FriendlyException(R.string.error_generic)
    }

    suspend fun forget(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    /**
     * The OAuth client the Google services plugin generates from google-services.json. Looked up
     * by name because builds without Firebase config don't have it.
     */
    @SuppressLint("DiscouragedApi")
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }
}
