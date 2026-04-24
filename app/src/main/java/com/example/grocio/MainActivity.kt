package com.example.grocio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grocio.ui.theme.GrocioTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrocioTheme {
                SplashScreen {
                    checkUserStatus()
                }
            }
        }
    }

    private fun checkUserStatus() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            // Using setClassName to bypass a Kotlin 2.0 compiler crash with class literals
            val dashboardIntent = Intent().setClassName(this, "com.example.grocio.DashboardActivity")
            startActivity(dashboardIntent)
        } else {
            val loginIntent = Intent().setClassName(this, "com.example.grocio.LoginActivity")
            startActivity(loginIntent)
        }
        finish()
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onTimeout()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Grocio",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = colorResource(id = R.color.neo_mint_accent),
            fontFamily = FontFamily.SansSerif
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "\"Would you like your receipts?\"\nSAY: \"Yes please!\"",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colorResource(id = R.color.neo_mint_accent),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}
