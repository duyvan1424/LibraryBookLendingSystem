package com.example.librarybooklendingsystem.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.librarybooklendingsystem.R
import com.example.librarybooklendingsystem.data.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val isAdmin by AuthState.isAdmin.collectAsStateWithLifecycle(initialValue = false)
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AuthState.init(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Logo
        Image(
            painter = painterResource(id = R.drawable.logouth),
            contentDescription = "Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(top = 32.dp, bottom = 16.dp)
        )

        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 8.dp),
            fontFamily = FontFamily(Font(R.font.nunito_bold, FontWeight.Bold))
        )

        Text(
            text = "Welcome back!",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp),
            fontFamily = FontFamily(Font(R.font.nunito_bold, FontWeight.Bold))
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Person else Icons.Filled.Lock,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Color(0xFF0093AB)
                    )
                }
            }
        )

        // Display error message if any
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        TextButton(
            onClick = { /* TODO: Handle forgot password */ },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                "Forgot Password?",
                color = Color(0xFF0093AB)
            )
        }

        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    errorMessage = "Vui lòng nhập đầy đủ thông tin đăng nhập"
                    return@Button
                }
                if (!isValidPassword(password)) {
                    errorMessage = "Mật khẩu phải có ít nhất 8 ký tự, bao gồm 1 chữ hoa, 1 số và 1 ký tự đặc biệt"
                    return@Button
                }

                isLoading = true
                AuthState.signIn(
                    email = email,
                    password = password,
                    context = context,
                    onSuccess = {
                        // Bỏ kiểm tra xác minh email
                        
                        isLoading = false
                        if (isAdmin) {
                            navController.navigate("admin_dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        } else {
                            navController.navigate("home") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    },
                    onError = { error ->
                        isLoading = false
                        errorMessage = error
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0093AB)
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily(Font(R.font.nunito_bold, FontWeight.Bold)),
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account? ")
            TextButton(onClick = { navController.navigate("signup") }) {
                Text(
                    "Signup",
                    color = Color(0xFF0093AB)
                )
            }
        }
    }
}
fun isValidPassword(password: String): Boolean {
    val passwordRegex = Regex("^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#%^&*(),.?\":{}|<>]).{8,}$")
    return passwordRegex.matches(password)
}