package com.example.auth

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthScreenTest {

    private lateinit var fakeAuthRepo: FakeAuthRepository

    @Before
    fun setUp() {
        fakeAuthRepo = FakeAuthRepository()
    }

    @Test
    fun `signUpWithEmail succeeds and transitions to Authenticated without email verification`() = runTest {
        var successCalled = false
        var errorCalled: String? = null

        fakeAuthRepo.signUpWithEmail(
            email = "user@example.com",
            password = "password123",
            displayName = "Alex",
            scope = this,
            onSuccess = { successCalled = true },
            onError = { errorCalled = it }
        )

        assertTrue("onSuccess must be called on successful sign up", successCalled)
        assertNull("onError must not be called", errorCalled)

        val authState = fakeAuthRepo.authState.value
        assertTrue("User must be Authenticated immediately without email verification", authState is AuthState.Authenticated)
        val user = (authState as AuthState.Authenticated).user
        assertEquals("user@example.com", user.email)
        assertEquals("Alex", user.displayName)
    }

    @Test
    fun `signInWithEmail succeeds with registered credentials`() = runTest {
        // Register user first
        fakeAuthRepo.signUpWithEmail(
            email = "login@example.com",
            password = "securePassword",
            displayName = "Tester",
            scope = this,
            onSuccess = {},
            onError = {}
        )

        // Sign out
        fakeAuthRepo.signOutInternal()
        assertTrue("User must be Guest after sign out", fakeAuthRepo.authState.value is AuthState.Guest)

        var signInSuccess = false
        var signInError: String? = null

        fakeAuthRepo.signInWithEmail(
            email = "login@example.com",
            password = "securePassword",
            scope = this,
            onSuccess = { signInSuccess = true },
            onError = { signInError = it }
        )

        assertTrue("signInWithEmail onSuccess must be called", signInSuccess)
        assertNull(signInError)
        assertTrue("State must be Authenticated", fakeAuthRepo.authState.value is AuthState.Authenticated)
    }

    @Test
    fun `signInWithEmail reports error on wrong password`() = runTest {
        fakeAuthRepo.signUpWithEmail(
            email = "test@example.com",
            password = "correctPassword",
            displayName = null,
            scope = this,
            onSuccess = {},
            onError = {}
        )

        fakeAuthRepo.signOutInternal()

        var signInSuccess = false
        var signInError: String? = null

        fakeAuthRepo.signInWithEmail(
            email = "test@example.com",
            password = "wrongPassword",
            scope = this,
            onSuccess = { signInSuccess = true },
            onError = { signInError = it }
        )

        assertFalse("onSuccess must not be called on wrong password", signInSuccess)
        assertNotNull("onError must be called", signInError)
        assertEquals("Incorrect email or password. Please try again.", signInError)
    }

    @Test
    fun `sendPasswordResetEmail succeeds for valid email`() = runTest {
        var resetSuccess = false
        var resetError: String? = null

        fakeAuthRepo.sendPasswordResetEmail(
            email = "forgot@example.com",
            scope = this,
            onSuccess = { resetSuccess = true },
            onError = { resetError = it }
        )

        assertTrue(resetSuccess)
        assertNull(resetError)
    }
}

// In-memory fake implementation of AuthRepository for deterministic JVM testing
class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Guest)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val registeredAccounts = mutableMapOf<String, Pair<String, String?>>() // email -> (password, displayName)

    override val currentUserId: String?
        get() = (_authState.value as? AuthState.Authenticated)?.user?.uid

    override val currentUser: FirebaseUser?
        get() = null

    override fun attemptAutoSignIn(context: Context, scope: CoroutineScope) {}

    override fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val uid = "google_user_123"
        _authState.value = AuthState.Authenticated(
            AuthUser(uid = uid, email = "google@example.com", displayName = "Google User", photoUrl = null)
        )
        onSuccess()
    }

    override fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (password.length < 6) {
            onError("Password must be at least 6 characters.")
            return
        }
        if (registeredAccounts.containsKey(email)) {
            onError("An account with this email already exists. Try signing in.")
            return
        }
        registeredAccounts[email] = Pair(password, displayName)
        val uid = "email_user_${email.hashCode()}"
        _authState.value = AuthState.Authenticated(
            AuthUser(uid = uid, email = email, displayName = displayName, photoUrl = null)
        )
        onSuccess()
    }

    override fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val account = registeredAccounts[email]
        if (account == null || account.first != password) {
            onError("Incorrect email or password. Please try again.")
            return
        }
        val uid = "email_user_${email.hashCode()}"
        _authState.value = AuthState.Authenticated(
            AuthUser(uid = uid, email = email, displayName = account.second, photoUrl = null)
        )
        onSuccess()
    }

    override fun sendPasswordResetEmail(
        email: String,
        scope: CoroutineScope,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank()) {
            onError("Please enter your email address.")
            return
        }
        onSuccess()
    }

    override fun signOut(context: Context, scope: CoroutineScope, onComplete: () -> Unit) {
        signOutInternal()
        onComplete()
    }

    override fun continueAsGuest() {
        signOutInternal()
    }

    fun signOutInternal() {
        _authState.value = AuthState.Guest
    }
}
