package com.burixer85.cs2skinflip.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.burixer85.cs2skinflip.core.auth.AuthViewModel
import com.burixer85.cs2skinflip.core.ui.theme.AccentGreen
import com.burixer85.cs2skinflip.core.ui.theme.AccentOrange
import com.burixer85.cs2skinflip.core.ui.theme.AccentRed
import com.burixer85.cs2skinflip.core.ui.theme.DividerColor
import com.burixer85.cs2skinflip.core.ui.theme.Surface
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceElevated
import com.burixer85.cs2skinflip.core.ui.theme.SurfaceVariant
import com.burixer85.cs2skinflip.core.ui.theme.TextPrimary
import com.burixer85.cs2skinflip.core.ui.theme.TextSecondary

/** Requests a password reset email. Shared between the Alerts and Settings sign-in flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordSheet(
    onDismiss: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by authViewModel.forgotPasswordState.collectAsState()
    var email by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { authViewModel.resetForgotPasswordState() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Reset your password", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            if (state.sent) {
                Text(
                    "If that email is registered, we've sent a link to reset your password. Check your inbox.",
                    color = AccentGreen, fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    "Enter your account email and we'll send you a link to reset your password.",
                    color = TextSecondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    placeholder = { Text("Email", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(), colors = forgotPasswordTextFieldColors(),
                )

                state.errorMessage?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = AccentRed, fontSize = 13.sp)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { authViewModel.forgotPassword(email.trim()) },
                    enabled = !state.submitting && email.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        disabledContainerColor = SurfaceElevated,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Send reset link", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun forgotPasswordTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = SurfaceVariant,
    unfocusedContainerColor = SurfaceVariant,
    focusedIndicatorColor = AccentOrange,
    unfocusedIndicatorColor = DividerColor,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentOrange,
)
