package com.goalscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF07111F)
private val Panel = Color(0xFF101E30)
private val Panel2 = Color(0xFF17283B)
private val Lime = Color(0xFFB8F34A)
private val Muted = Color(0xFF91A2B7)
private val Line = Color(0xFF26384C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("goal_scanner_settings", MODE_PRIVATE)
        setContent {
            var selectedTab by rememberSaveable { mutableStateOf("Home") }
            var backendUrl by rememberSaveable { mutableStateOf(preferences.getString("backend_url", "") ?: "") }
            var backendStatus by rememberSaveable { mutableStateOf("Not connected") }
            var isChecking by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Lime, onPrimary = Ink, background = Ink, onBackground = Color.White,
                    surface = Panel, onSurface = Color.White, surfaceVariant = Panel2,
                    onSurfaceVariant = Muted, secondary = Muted, outline = Line
                )
            ) {
                Scaffold(
                    containerColor = Ink,
                    topBar = {
                        TopBar(
                            onSettings = { selectedTab = "Settings" },
                            onHome = { selectedTab = "Home" }
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Color(0xFF0B1726), tonalElevation = 0.dp) {
                            val items = listOf(
                                NavItem("Home", Icons.Filled.Home),
                                NavItem("Scanner", Icons.Filled.SportsSoccer),
                                NavItem("Intel", Icons.Filled.Insights),
                                NavItem("Slip", Icons.Filled.ListAlt),
                                NavItem("Results", Icons.Filled.Analytics)
                            )
                            items.forEach { item ->
                                NavigationBarItem(
                                    selected = selectedTab == item.label,
                                    onClick = { selectedTab = item.label },
                                    icon = { Icon(item.icon, null, modifier = Modifier.size(21.dp)) },
                                    label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Lime,
                                        selectedTextColor = Lime,
                                        indicatorColor = Color(0xFF24351E),
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp)
                    ) {
                        Spacer(Modifier.height(10.dp))
                        when (selectedTab) {
                            "Home" -> HomeScreen(
                                backendStatus,
                                onScan = { selectedTab = "Scanner" },
                                onIntel = { selectedTab = "Intel" }
                            )
                            "Scanner" -> ScannerScreen()
                            "Intel" -> IntelligenceScreen()
                            "Slip" -> AccumulatorScreen()
                            "Results" -> ResultsScreen()
                            "Settings" -> SettingsScreen(
                                backendUrl, { backendUrl = it }, backendStatus, isChecking,
                                onSave = {
                                    preferences.edit().putString("backend_url", backendUrl.trim().trimEnd('/')).apply()
                                    backendStatus = "Backend address saved"
                                },
                                onTest = {
                                    val candidate = backendUrl.trim().trimEnd('/')
                                    if (!candidate.startsWith("https://")) {
                                        backendStatus = "Enter a secure HTTPS address first"
                                    } else {
                                        scope.launch {
                                            isChecking = true
                                            backendStatus = "Checking connection…"
                                            backendStatus = withContext(Dispatchers.IO) {
                                                try {
                                                    val connection = URL("$candidate/health").openConnection() as HttpURLConnection
                                                    connection.connectTimeout = 7000
                                                    connection.readTimeout = 7000
                                                    val code = connection.responseCode
                                                    connection.disconnect()
                                                    if (code in 200..299) "Connected · health check passed" else "Server responded with HTTP $code"
                                                } catch (_: Exception) { "Connection failed · check the URL and server" }
                                            }
                                            isChecking = false
                                        }
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                }
            }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun TopBar(onSettings: () -> Unit, onHome: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Lime, Color(0xFF6DCC45)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SportsSoccer, null, tint = Ink, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("GOAL SCANNER", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text("FOOTBALL INTELLIGENCE  ·  2.0", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
        }
        IconButton(onClick = onHome) { Icon(Icons.Filled.Refresh, "Home", tint = Muted) }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = Muted) }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeScreen(backendStatus: String, onScan: () -> Unit, onIntel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Eyebrow("Your matchday workspace")
                Text("See the game
beyond the odds.", fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF172B20)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Bolt, null, tint = Lime, modifier = Modifier.size(28.dp))
            }
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFF203B2B), Color(0xFF14283A), Panel))).padding(19.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, null, tint = Lime, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Eyebrow("Evidence before predictions")
                    }
                    Text("Smarter analysis.
No forced selections.", fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Every market must pass data-quality, probability and value checks. No invented fixtures or guaranteed wins.", color = Color(0xFFC1D0DF), fontSize = 12.sp, lineHeight = 18.sp)
                    Button(
                        onClick = onScan,
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 11.dp)
                    ) {
                        Text("Open match scanner", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        SectionTitle("System overview", "Live services will be connected in a later phase")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("FIXTURES", "—", "Awaiting data", Modifier.weight(1f))
            MetricCard("APPROVED", "—", "No selections", Modifier.weight(1f))
            MetricCard("MODEL", "READY*", "Engine offline", Modifier.weight(1f))
        }
        SectionTitle("Your workspace")
        FeatureRow(Icons.Filled.SportsSoccer, "Match scanner", "Fixture analysis and approved markets", onScan)
        FeatureRow(Icons.Filled.Insights, "Team intelligence", "Lineups, injuries and match context", onIntel)
        FeatureRow(Icons.Filled.EmojiEvents, "Accumulator builder", "Build disciplined 2–3 leg tickets") {}
        SectionTitle("Connection status")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFFB74D)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Backend", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(backendStatus, color = Muted, fontSize = 11.sp)
            }
            Text("OFFLINE", color = Color(0xFFFFB74D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Text("*The app interface is ready; live model services are not connected yet.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(9.dp))
        Text(value, color = Lime, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(3.dp))
        Text(detail, color = Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF24351E)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Lime, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(detail, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Filled.ArrowForward, null, tint = Muted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String, tag: String = "COMING IN A LATER BUILD") {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF24351E)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Lime, modifier = Modifier.size(28.dp))
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Text(tag, color = Lime, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
            modifier = Modifier.border(1.dp, Color(0xFF3A542C), RoundedCornerShape(30.dp)).padding(horizontal = 12.dp, vertical = 7.dp))
    }
}

@Composable
private fun ScannerScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Eyebrow("Analysis centre")
        SectionTitle("Match scanner", "A structured view of fixtures and market opportunities")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("ALL FIXTURES", true)
            FilterPill("GOALS", false)
            FilterPill("1X2", false)
        }
        EmptyState(Icons.Filled.SportsSoccer, "No fixtures loaded", "The scanner is waiting for a verified fixture feed. Once connected, each match will show model probabilities, bookmaker comparison and evidence quality.")
        Text("MARKETS SUPPORTED BY THE ENGINE", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        listOf("Match Over 2.5 goals", "Both teams to score · Yes", "Match result · 1X2", "Team and match corners · Over", "Team and match bookings · Over").forEach {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Lime, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(it, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean) {
    Text(text, color = if (selected) Ink else Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(30.dp)).background(if (selected) Lime else Panel2).padding(horizontal = 12.dp, vertical = 9.dp))
}

@Composable
private fun IntelligenceScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Eyebrow("Research desk")
        SectionTitle("Team intelligence", "Context that helps explain what may happen on the pitch")
        EmptyState(Icons.Filled.Insights, "No team reports yet", "Verified team news will appear here after data sources are connected. Rumours and unconfirmed reports will never be presented as facts.")
        listOf(
            "Player availability" to "Injuries, suspensions and return dates",
            "Lineup intelligence" to "Expected and official starting elevens",
            "Tactical context" to "Formations, roles and likely matchups",
            "Match conditions" to "Rest days, travel, weather and referee"
        ).forEach { (title, detail) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(15.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AccumulatorScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Eyebrow("Selection workspace")
        SectionTitle("Accumulator builder", "Quality first. A bet is never compulsory.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("TARGET ODDS", "2.00–3.00", "Combined price", Modifier.weight(1f))
            MetricCard("MAX LEGS", "3", "Two preferred", Modifier.weight(1f))
        }
        EmptyState(Icons.Filled.ListAlt, "Your ticket is empty", "Approved selections will be available here when the scanner is connected. Correlated markets will be checked jointly instead of multiplying probabilities as if they were independent.")
        Text("TICKET RULES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        listOf("Positive conservative expected value", "Evidence and freshness checks passed", "Different fixtures preferred", "No forced picks when data is insufficient").forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Lime, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Text(it, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ResultsScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Eyebrow("Track record")
        SectionTitle("Results & performance", "Transparent records, not screenshots of winning bets")
        EmptyState(Icons.Filled.Analytics, "No settled predictions", "Prediction snapshots and final scores will be stored here. Performance metrics will only be calculated from recorded selections and verified outcomes.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Brier score", "—", "Not calculated", Modifier.weight(1f))
            MetricCard("Log loss", "—", "Not calculated", Modifier.weight(1f))
        }
        MetricCard("Profit / loss", "—", "No historical record loaded", Modifier.fillMaxWidth())
    }
}

@Composable
private fun SettingsScreen(
    backendUrl: String, onBackendUrlChange: (String) -> Unit, backendStatus: String,
    isChecking: Boolean, onSave: () -> Unit, onTest: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Eyebrow("App configuration")
        SectionTitle("Settings", "Connections can be added when the backend is ready")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BACKEND CONNECTION", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("The app contains no private provider API keys. It will communicate with a secure backend when deployed.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
            OutlinedTextField(
                value = backendUrl, onValueChange = onBackendUrlChange, modifier = Modifier.fillMaxWidth(),
                label = { Text("Backend HTTPS URL") }, placeholder = { Text("https://your-backend.example") },
                singleLine = true, shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Save address") }
                OutlinedButton(onClick = onTest, enabled = !isChecking) { Text(if (isChecking) "Testing…" else "Test") }
            }
            Text(backendStatus, color = Muted, fontSize = 11.sp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ABOUT GOAL SCANNER", color = Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Version 0.2.0 · Interface preview", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Built around evidence-led football analysis, market-specific models and honest performance tracking. Live fixtures, predictions and results are not active in this build.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        }
    }
}
