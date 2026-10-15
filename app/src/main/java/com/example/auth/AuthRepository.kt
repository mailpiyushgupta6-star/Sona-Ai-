package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUserId: String?
    val currentUser: FirebaseUser?
    fun attemptAutoSignIn(context: Context, scope: CoroutineScope)
    fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    )
    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    fun sendPasswordResetEmail(
        email: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    )
    fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit)
    fun continueAsGuest()
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = Firebase.auth
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override val currentUser: FirebaseUser?
        get() = auth.currentUser

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            _authState.value = AuthState.Authenticated(
                AuthUser(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString()
                )
            )
        } else {
            _authState.value = AuthState.Guest
        }
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    override fun attemptAutoSignIn(context: Context, scope: CoroutineScope) {
        if (auth.currentUser != null) {
            val user = auth.currentUser!!
            _authState.value = AuthState.Authenticated(
                AuthUser(
                    uid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                    photoUrl = user.photoUrl?.toString()
                )
            )
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            _authState.value = AuthState.Guest
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(context)

        scope.launch(Dispatchers.IO) {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                } else {
                    _authState.value = AuthState.Guest
                }
            } catch (_: Exception) {
                _authState.value = AuthState.Guest
            }
        }
    }

    override fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val credentialManager = CredentialManager.create(context)

        scope.launch(Dispatchers.Main) {
            try {
                val activity = context as? Activity ?: run {
                    onError("Context must be an Activity for Google Sign-In")
                    return@launch
                }
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    auth.signInWithCredential(authCredential).await()
                    onSuccess()
                } else {
                    onError("Unexpected credential type returned from Google Sign-In")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    override fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = result.user
                if (user != null && !displayName.isNullOrBlank()) {
                    val profileUpdates = userProfileChangeRequest {
                        this.displayName = displayName.trim()
                    }
                    user.updateProfile(profileUpdates).await()
                }
                launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("Auth", "Sign Up failed", e)
                launch(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Sign Up failed")
                }
            }
        }
    }

    override fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("Auth", "Sign In failed", e)
                launch(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Sign In failed")
                }
            }
        }
    }

    override fun sendPasswordResetEmail(
        email: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                auth.sendPasswordResetEmail(email.trim()).await()
                launch(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("Auth", "Password reset failed", e)
                launch(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to send password reset email")
                }
            }
        }
    }

    override fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit) {
        auth.signOut()
        val credentialManager = CredentialManager.create(context)
        scope.launch(Dispatchers.IO) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("Auth", "Failed to clear credential state", e)
            } finally {
                _authState.value = AuthState.Guest
                launch(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    override fun continueAsGuest() {
        _authState.value = AuthState.Guest
    }
}
