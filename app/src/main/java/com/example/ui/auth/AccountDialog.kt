package com.example.ui.auth

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthRepository
import com.example.auth.AuthState
import com.example.sync.SyncState
import com.example.sync.SyncStatus
import com.example.ui.theme.SonaAmber
import com.example.ui.theme.SonaCyan
import com.example.ui.theme.SonaEmerald
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    RESET_PASSWORD
}

@Composable
fun AccountDialog(
    authRepository: AuthRepository,
    authState: AuthState,
    syncState: SyncState,
    onImportLocalData: suspend () -> Int,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = SonaCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Account & Cloud Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (authState) {
                    is AuthState.Authenticated -> {
                        // Profile Banner
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SonaCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = authState.user.displayName?.take(1) ?: authState.user.email?.take(1)?.uppercase() ?: "U",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SonaCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authState.user.displayName ?: "Authenticated User",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = authState.user.email ?: authState.user.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Sync Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SonaEmerald.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (syncState.status == SyncStatus.SYNCING) Icons.Default.Sync else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = SonaEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (syncState.status == SyncStatus.SYNCING) "Synchronizing conversations…" else "Cloud Sync Active (Firestore)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SonaEmerald
                            )
                        }

                        // Import Local Data to Cloud Account Button
                        OutlinedButton(
                            onClick = {
                                isImporting = true
                                scope.launch {
                                    try {
                                        val count = onImportLocalData()
                                        importStatusMessage = "Successfully uploaded $count chats to your cloud account!"
                                        Toast.makeText(context, importStatusMessage, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        errorMessage = e.localizedMessage
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            },
                            enabled = !isImporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_local_chats_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Importing…")
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = SonaCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Local Chats to Cloud")
                            }
                        }

                        if (importStatusMessage != null) {
                            Text(
                                text = importStatusMessage!!,
                                fontSize = 11.sp,
                                color = SonaEmerald
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // Sign Out Button
                        OutlinedButton(
                            onClick = {
                                authRepository.signOut(context, scope) {
                                    onDismissRequest()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Account", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    is AuthState.Guest, is AuthState.Loading, is AuthState.Error -> {
                        // Auth Mode Segmented Selector (Sign In | Sign Up | Reset)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AuthTabButton(
                                text = "Sign In",
                                isSelected = authMode == AuthMode.SIGN_IN,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    authMode = AuthMode.SIGN_IN
                                    errorMessage = null
                                    successMessage = null
                                }
                            )
                            AuthTabButton(
                                text = "Sign Up",
                                isSelected = authMode == AuthMode.SIGN_UP,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    authMode = AuthMode.SIGN_UP
                                    errorMessage = null
                                    successMessage = null
                                }
                            )
                            AuthTabButton(
                                text = "Reset",
                                isSelected = authMode == AuthMode.RESET_PASSWORD,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    authMode = AuthMode.RESET_PASSWORD
                                    errorMessage = null
                                    successMessage = null
                                }
                            )
                        }

                        if (errorMessage != null) {
                            Text(
                                text = "⚠️ $errorMessage",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (successMessage != null) {
                            Text(
                                text = "✓ $successMessage",
                                color = SonaEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        when (authMode) {
                            AuthMode.SIGN_IN -> {
                                OutlinedTextField(
                                    value = emailText,
                                    onValueChange = { emailText = it },
                                    label = { Text("Email address") },
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = SonaCyan) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_email_input")
                                )

                                OutlinedTextField(
                                    value = passwordText,
                                    onValueChange = { passwordText = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = SonaCyan) },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_password_input")
                                )

                                Button(
                                    onClick = {
                                        if (emailText.isBlank() || passwordText.isBlank()) {
                                            errorMessage = "Please enter both email and password."
                                            return@Button
                                        }
                                        isLoading = true
                                        errorMessage = null
                                        authRepository.signInWithEmail(
                                            email = emailText,
                                            password = passwordText,
                                            scope = scope,
                                            onSuccess = {
                                                isLoading = false
                                                Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                errorMessage = err
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = SonaCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("email_sign_in_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003548), strokeWidth = 2.dp)
                                    } else {
                                        Text("Sign In with Email", color = Color(0xFF003548), fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        authMode = AuthMode.RESET_PASSWORD
                                        errorMessage = null
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Forgot password?", fontSize = 12.sp, color = SonaCyan)
                                }
                            }

                            AuthMode.SIGN_UP -> {
                                OutlinedTextField(
                                    value = nameText,
                                    onValueChange = { nameText = it },
                                    label = { Text("Display Name (optional)") },
                                    leadingIcon = { Icon(Icons.Default.Person, null, tint = SonaCyan) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_name_input")
                                )

                                OutlinedTextField(
                                    value = emailText,
                                    onValueChange = { emailText = it },
                                    label = { Text("Email address") },
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = SonaCyan) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_email_input")
                                )

                                OutlinedTextField(
                                    value = passwordText,
                                    onValueChange = { passwordText = it },
                                    label = { Text("Password (6+ chars)") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = SonaCyan) },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_password_input")
                                )

                                Button(
                                    onClick = {
                                        if (emailText.isBlank() || passwordText.isBlank()) {
                                            errorMessage = "Please enter an email and password."
                                            return@Button
                                        }
                                        if (passwordText.length < 6) {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@Button
                                        }
                                        isLoading = true
                                        errorMessage = null
                                        authRepository.signUpWithEmail(
                                            email = emailText,
                                            password = passwordText,
                                            displayName = nameText.ifBlank { null },
                                            scope = scope,
                                            onSuccess = {
                                                isLoading = false
                                                Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                errorMessage = err
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = SonaCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("email_sign_up_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003548), strokeWidth = 2.dp)
                                    } else {
                                        Text("Create Account", color = Color(0xFF003548), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            AuthMode.RESET_PASSWORD -> {
                                OutlinedTextField(
                                    value = emailText,
                                    onValueChange = { emailText = it },
                                    label = { Text("Email address") },
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = SonaCyan) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_email_input")
                                )

                                Button(
                                    onClick = {
                                        if (emailText.isBlank()) {
                                            errorMessage = "Please enter your account email address."
                                            return@Button
                                        }
                                        isLoading = true
                                        errorMessage = null
                                        authRepository.sendPasswordResetEmail(
                                            email = emailText,
                                            scope = scope,
                                            onSuccess = {
                                                isLoading = false
                                                successMessage = "Password reset email sent to $emailText"
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                errorMessage = err
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = SonaCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("send_password_reset_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF003548), strokeWidth = 2.dp)
                                    } else {
                                        Text("Send Password Reset Link", color = Color(0xFF003548), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Text(" OR ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }

                        // Google Sign-In Action Button
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
                                    },
                                    onError = { err ->
                                        isLoading = false
                                        errorMessage = err
                                    },
                                    onCancelled = {
                                        isLoading = false
                                    }
                                )
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("google_sign_in_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = SonaCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close", color = SonaCyan)
            }
        }
    )
}

@Composable
private fun AuthTabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SonaCyan else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF003548) else MaterialTheme.colorScheme.onSurface
        )
    }
}
