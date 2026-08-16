package com.kypeli.flightsoverhead.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.viewmodel.AuthUiState
import com.kypeli.flightsoverhead.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        onSignIn = { email, password -> viewModel.signIn(email, password) },
        onSignUp = { email, password -> viewModel.signUp(email, password) },
        onClearError = { viewModel.clearError() },
        modifier = modifier,
    )
}

@Composable
fun LoginScreenContent(
    uiState: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    initialEmail: String = "",
    initialPassword: String = "",
    initialIsSignUp: Boolean = false,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf(initialPassword) }
    var isSignUp by remember { mutableStateOf(initialIsSignUp) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Validation
    val isEmailValid = remember(email) { email.contains("@") && email.contains(".") }
    val isPasswordValid = remember(password) { password.length >= 6 }
    val canSubmit = isEmailValid && isPasswordValid && !uiState.isLoading

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.background,
                                ),
                        ),
                ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App Logo
            Icon(
                imageVector = Icons.Outlined.AirplanemodeActive,
                contentDescription = "SkyTrack Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Name / Title
            Text(
                text = "SkyTrack Pro",
                style =
                    MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "Observe flights directly overhead",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login/Signup Card
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            onClearError()
                        },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Email, contentDescription = "Email")
                        },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                        isError = email.isNotEmpty() && !isEmailValid,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onClearError()
                        },
                        label = { Text("Password (min 6 chars)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Password")
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (canSubmit) {
                                        if (isSignUp) {
                                            onSignUp(email, password)
                                        } else {
                                            onSignIn(email, password)
                                        }
                                    }
                                },
                            ),
                        isError = password.isNotEmpty() && !isPasswordValid,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Button or Loader
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Button(
                            onClick = {
                                if (isSignUp) {
                                    onSignUp(email, password)
                                } else {
                                    onSignIn(email, password)
                                }
                            },
                            enabled = canSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = if (isSignUp) "Register" else "Sign In",
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle Auth Mode
                    TextButton(
                        onClick = {
                            isSignUp = !isSignUp
                            onClearError()
                        },
                        enabled = !uiState.isLoading,
                    ) {
                        Text(
                            text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error Message Banner
            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                uiState.error?.let { errMsg ->
                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = errMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Phone light",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
)
@Composable
fun LoginScreenPreview() {
    FlightsOverheadTheme {
        LoginScreenContent(
            uiState = AuthUiState(),
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onClearError = {},
        )
    }
}

@Preview(
    name = "Phone dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun LoginScreenDarkPreview() {
    FlightsOverheadTheme {
        LoginScreenContent(
            uiState = AuthUiState(),
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onClearError = {},
        )
    }
}

@Preview(
    name = "Sign Up mode",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
)
@Composable
fun LoginScreenSignUpPreview() {
    FlightsOverheadTheme {
        LoginScreenContent(
            uiState = AuthUiState(),
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onClearError = {},
            initialIsSignUp = true,
        )
    }
}

@Preview(
    name = "Loading",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
)
@Composable
fun LoginScreenLoadingPreview() {
    FlightsOverheadTheme {
        LoginScreenContent(
            uiState = AuthUiState(isLoading = true),
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onClearError = {},
            initialEmail = "user@example.com",
            initialPassword = "password123",
        )
    }
}

@Preview(
    name = "Error",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
)
@Composable
fun LoginScreenErrorPreview() {
    FlightsOverheadTheme {
        LoginScreenContent(
            uiState = AuthUiState(error = "Invalid email address or password."),
            onSignIn = { _, _ -> },
            onSignUp = { _, _ -> },
            onClearError = {},
            initialEmail = "user@example.com",
            initialPassword = "wrongpassword",
        )
    }
}
