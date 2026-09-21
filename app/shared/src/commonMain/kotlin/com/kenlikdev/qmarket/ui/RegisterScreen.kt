package com.kenlikdev.qmarket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kenlikdev.qmarket.validation.ClientInputValidation

@Composable
fun RegisterScreen(
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    error: String?,
    loading: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onRegister: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Register", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = { Text("First name") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = { Text("Last name") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        )
        ErrorText(error)
        Button(
            onClick = onRegister,
            enabled =
                !loading &&
                    ClientInputValidation.isValidEmail(email) &&
                    ClientInputValidation.isValidPassword(password) &&
                    ClientInputValidation.isValidPersonName(firstName) &&
                    ClientInputValidation.isValidPersonName(lastName),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .testTag("registerSubmit"),
        ) {
            Text(if (loading) "…" else "Register")
        }
        TextButton(onClick = onBackToLogin, enabled = !loading) {
            Text("Back to login")
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
    }
}
