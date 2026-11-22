package com.goalscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("goal_scanner_settings", MODE_PRIVATE)
        setContent {
            var selectedTab by rememberSaveable { mutableStateOf("Home") }
            var backendUrl by rememberSaveable {
                mutableStateOf(preferences.getString("backend_url", "") ?: "")
            }
            var backendStatus by rememberSaveable { mutableStateOf("Not connected") }
            var isChecking by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.material3.Scaffold(
                        bottomBar = {
                            NavigationBar {
                                listOf("Home", "Scanner", "Intelligence", "Accumulator", "Results", "Settings").forEach { tab ->
                                    NavigationBarItem(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        icon = {},
                                        label = { Text(tab) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(18.dp)
                        ) {
                            Text(
                                "GOAL SCANNER",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "2.0",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(18.dp))

                            when (selectedTab) {
                                "Home" -> HomeScreen(backendStatus)
                                "Scanner" -> InfoScreen(
                                    "Match scanner",
                                    "A match analysis workspace. Fixtures and predictions will appear here only after a verified data feed and model are connected.",
                                    listOf("Fixture and competition details", "Market-by-market probabilities", "Evidence quality and risk checks")
                                )
                                "Intelligence" -> InfoScreen(
                                    "Football intelligence",
                                    "A place for verified team news and match context. Unconfirmed reports will remain clearly marked and will not be treated as facts.",
                                    listOf("Injuries and suspensions", "Expected and confirmed lineups", "Tactics, player roles and schedule context")
                                )
                                "Accumulator" -> InfoScreen(
                                    "Accumulator builder",
                                    "The builder will only use selections that pass the model, evidence and value checks. It will not force a bet.",
                                    listOf("Target combined odds: 2.00–3.00", "Two legs preferred; three maximum", "Correlated selections require joint-probability checks")
                                )
                                "Results" -> InfoScreen(
                                    "Performance",
                                    "This section will track saved prediction snapshots against final results. Historical performance will be reported separately from live selections.",
                                    listOf("Prediction history", "Brier score and log loss", "Profit and loss with transparent assumptions")
                                )
                                "Settings" -> {
                                    SettingsScreen(
                                        backendUrl = backendUrl,
                                        onBackendUrlChange = { backendUrl = it },
                                        backendStatus = backendStatus,
                                        isChecking = isChecking,
                                        onSave = {
                                            preferences.edit().putString("backend_url", backendUrl.trim().trimEnd('/')).apply()
                                            backendStatus = "Backend address saved"
                                        },
                                        onTest = {
                                            val candidate = backendUrl.trim().trimEnd('/')
                                            if (!candidate.startsWith("https://")) {
                                                backendStatus = "Enter a secure HTTPS backend address first"
                                            } else {
                                                scope.launch {
                                                    isChecking = true
                                                    backendStatus = "Checking connection…"
                                                    backendStatus = withContext(Dispatchers.IO) {
                                                        try {
                                                            val connection = (URL("$candidate/health").openConnection() as HttpURLConnection)
                                                            connection.connectTimeout = 7000
                                                            connection.readTimeout = 7000
                                                            connection.requestMethod = "GET"
                                                            val code = connection.responseCode
                                                            connection.disconnect()
                                                            if (code in 200..299) "Connected: backend health check passed"
                                                            else "Server responded with HTTP $code"
                                                        } catch (error: Exception) {
                                                            "Connection failed: check the URL and server"
                                                        }
                                                    }
                                                    isChecking = false
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(backendStatus: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Football analysis, with evidence.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("The Android foundation is ready for the prediction engine and verified data services.")
        StatusCard("Backend", backendStatus)
        StatusCard("Prediction feed", "Not configured")
        StatusCard("Model status", "No live predictions yet")
        Text("What this app is being built to do", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Analyse approved markets, check team news, compare model probabilities with bookmaker prices, and reject selections when evidence is weak.")
        Text("No fixtures or betting selections are fabricated in this build.", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InfoScreen(title: String, description: String, bullets: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description)
        bullets.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("•  $item", modifier = Modifier.fillMaxWidth().padding(14.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("This module is an interface foundation. It will be activated when its data and validation components are ready.", color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SettingsScreen(
    backendUrl: String,
    onBackendUrlChange: (String) -> Unit,
    backendStatus: String,
    isChecking: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connection settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("The app does not contain private provider API keys. Connect it to a secure backend when one is deployed.")
        OutlinedTextField(
            value = backendUrl,
            onValueChange = onBackendUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Backend HTTPS URL") },
            placeholder = { Text("https://your-backend.example") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            Button(onClick = onTest, enabled = !isChecking) { Text(if (isChecking) "Testing…" else "Test connection") }
        }
        Text(backendStatus, color = MaterialTheme.colorScheme.primary)
        Text("The connection test checks the backend /health endpoint. Saving an address does not create or deploy a backend.")
    }
}

@Composable
private fun StatusCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
