package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthRepository
import com.example.auth.AuthState
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaEmerald
import com.example.ui.theme.SonaRose

enum class AuthScreenMode {
    SIGN_IN,
    SIGN_UP,
    RESET_PASSWORD
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    authState: AuthState,
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onContinueAsGuest()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var mode by remember { mutableStateOf(AuthScreenMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // If authenticated, notify success
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onAuthSuccess()
        }
    }

    fun cleanError(raw: String): String {
        return when {
            raw.contains("password", ignoreCase = true) && raw.contains("6", ignoreCase = true) ->
                "Password must be at least 6 characters."
            raw.contains("badly formatted", ignoreCase = true) || raw.contains("invalid email", ignoreCase = true) ->
                "Please enter a valid email address."
            raw.contains("already in use", ignoreCase = true) ->
                "An account with this email already exists. Try signing in."
            raw.contains("no user", ignoreCase = true) || raw.contains("user-not-found", ignoreCase = true) ->
                "No account found with this email. Try signing up."
            raw.contains("wrong-password", ignoreCase = true) || raw.contains("invalid-credential", ignoreCase = true) ->
                "Incorrect email or password. Please try again."
            raw.contains("network", ignoreCase = true) ->
                "Network error. Please check your internet connection."
            else -> raw
        }
    }

    fun submit() {
        focusManager.clearFocus()
        errorMessage = null
        successMessage = null

        when (mode) {
            AuthScreenMode.SIGN_IN -> {
                if (email.isBlank()) {
                    errorMessage = "Please enter your email address."
                    return
                }
                if (password.isBlank()) {
                    errorMessage = "Please enter your password."
                    return
                }
                isLoading = true
                authRepository.signInWithEmail(
                    email = email.trim(),
                    password = password,
                    scope = scope,
                    onSuccess = {
                        isLoading = false
                        Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    },
                    onError = { err ->
                        isLoading = false
                        errorMessage = cleanError(err)
                    }
                )
            }

            AuthScreenMode.SIGN_UP -> {
                if (email.isBlank()) {
                    errorMessage = "Please enter an email address."
                    return
                }
                if (!email.contains("@") || !email.contains(".")) {
                    errorMessage = "Please enter a valid email address."
                    return
                }
                if (password.isBlank()) {
                    errorMessage = "Please choose a password."
                    return
                }
                if (password.length < 6) {
                    errorMessage = "Password must be at least 6 characters long."
                    return
                }
                isLoading = true
                authRepository.signUpWithEmail(
                    email = email.trim(),
                    password = password,
                    displayName = displayName.trim().ifBlank { null },
                    scope = scope,
                    onSuccess = {
                        isLoading = false
                        Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        onAuthSuccess()
                    },
                    onError = { err ->
                        isLoading = false
                        errorMessage = cleanError(err)
                    }
                )
            }

            AuthScreenMode.RESET_PASSWORD -> {
                if (email.isBlank() || !email.contains("@")) {
                    errorMessage = "Please enter your registered email address."
                    return
                }
                isLoading = true
                authRepository.sendPasswordResetEmail(
                    email = email.trim(),
                    scope = scope,
                    onSuccess = {
                        isLoading = false
                        successMessage = "Password reset email sent to ${email.trim()}. Check your inbox!"
                    },
                    onError = { err ->
                        isLoading = false
                        errorMessage = cleanError(err)
                    }
                )
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(max = 440.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Top bar with Back button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = onContinueAsGuest,
                        modifier = Modifier.testTag("auth_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Brand Emblem (ChatGPT-style minimalist icon badge)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.5.dp, SonaCyan.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Sona AI Logo",
                        tint = SonaCyan,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Title & Subtitle
                Text(
                    text = when (mode) {
                        AuthScreenMode.SIGN_IN -> "Welcome back"
                        AuthScreenMode.SIGN_UP -> "Create your account"
                        AuthScreenMode.RESET_PASSWORD -> "Reset your password"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (mode) {
                        AuthScreenMode.SIGN_IN -> "Log in with your email to sync conversations across devices"
                        AuthScreenMode.SIGN_UP -> "Sign up to start saving and syncing your conversations to cloud"
                        AuthScreenMode.RESET_PASSWORD -> "Enter your email address and we'll send you a link to reset your password"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mode Tabs (Sign In | Sign Up)
                if (mode != AuthScreenMode.RESET_PASSWORD) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        TabPill(
                            title = "Log in",
                            isSelected = mode == AuthScreenMode.SIGN_IN,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                mode = AuthScreenMode.SIGN_IN
                                errorMessage = null
                                successMessage = null
                            }
                        )
                        TabPill(
                            title = "Sign up",
                            isSelected = mode == AuthScreenMode.SIGN_UP,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                mode = AuthScreenMode.SIGN_UP
                                errorMessage = null
                                successMessage = null
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Error Banner
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    errorMessage?.let { err ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SonaRose.copy(alpha = 0.12f))
                                .border(1.dp, SonaRose.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = SonaRose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = err,
                                color = SonaRose,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Success Banner
                AnimatedVisibility(
                    visible = successMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    successMessage?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SonaEmerald.copy(alpha = 0.12f))
                                .border(1.dp, SonaEmerald.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = SonaEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                color = SonaEmerald,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Form Inputs
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Display name input (Sign Up mode only)
                    if (mode == AuthScreenMode.SIGN_UP) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display name (optional)") },
                            placeholder = { Text("What should Sona call you?") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = SonaCyan)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SonaCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_name_input")
                        )
                    }

                    // Email input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email address") },
                        placeholder = { Text("name@example.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = SonaCyan)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = if (mode == AuthScreenMode.RESET_PASSWORD) ImeAction.Done else ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { submit() }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SonaCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    // Password input (Sign in and Sign up modes)
                    if (mode != AuthScreenMode.RESET_PASSWORD) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = {
                                password = it
                                errorMessage = null
                            },
                            label = { Text(if (mode == AuthScreenMode.SIGN_UP) "Password (6+ characters)" else "Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = SonaCyan)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.testTag("auth_toggle_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submit() }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SonaCyan,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )
                    }

                    // Forgot password link (Sign in mode)
                    if (mode == AuthScreenMode.SIGN_IN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    mode = AuthScreenMode.RESET_PASSWORD
                                    errorMessage = null
                                    successMessage = null
                                },
                                modifier = Modifier.testTag("auth_forgot_password_button")
                            ) {
                                Text(
                                    text = "Forgot password?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SonaCyan,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary "Continue" Button (ChatGPT-style prominent action)
                Button(
                    onClick = { submit() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonaCyan,
                        contentColor = Color(0xFF003548)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("auth_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF003548),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = when (mode) {
                                AuthScreenMode.SIGN_IN -> "Log In"
                                AuthScreenMode.SIGN_UP -> "Create Account"
                                AuthScreenMode.RESET_PASSWORD -> "Send Reset Link"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // If in Reset Password mode, provide "Back to Login" button
                if (mode == AuthScreenMode.RESET_PASSWORD) {
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            mode = AuthScreenMode.SIGN_IN
                            errorMessage = null
                            successMessage = null
                        },
                        modifier = Modifier.testTag("auth_back_to_login_button")
                    ) {
                        Text("← Return to Log in", color = SonaCyan, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // "OR" Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                    Text(
                        text = " OR ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Continue with Google Button
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        authRepository.signInWithGoogle(
                            context = context,
                            scope = scope,
                            onSuccess = {
                                isLoading = false
                                Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = cleanError(err)
                            },
                            onCancelled = {
                                isLoading = false
                            }
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_google_button")
                ) {
                    // Google "G" Emblem
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SonaCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Continue as Guest Option (ChatGPT allows casual use with local storage)
                OutlinedButton(
                    onClick = onContinueAsGuest,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auth_guest_button")
                ) {
                    Text(
                        text = "Continue as Guest (Local Phone Storage)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Footer Mode Switcher
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (mode == AuthScreenMode.SIGN_IN) "Don't have an account? " else "Already have an account? ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (mode == AuthScreenMode.SIGN_IN) "Sign up" else "Log in",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = SonaCyan,
                        modifier = Modifier
                            .clickable {
                                mode = if (mode == AuthScreenMode.SIGN_IN) AuthScreenMode.SIGN_UP else AuthScreenMode.SIGN_IN
                                errorMessage = null
                                successMessage = null
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("auth_toggle_mode_footer")
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SonaCyan else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF003548) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
