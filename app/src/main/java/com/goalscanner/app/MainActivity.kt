package com.goalscanner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private val Blue = Color(0xFF1976D2)
private val Navy = Color(0xFF101C32)
private val MarketIds = listOf("home_over_0_5","away_over_0_5","home_over_1_5","away_over_1_5","match_over_1_5","match_over_2_5","match_winner","double_chance","btts_yes")
private val MarketNames = listOf("Home team over 0.5 goals","Away team over 0.5 goals","Home team over 1.5 goals","Away team over 1.5 goals","Total over 1.5 goals","Total over 2.5 goals","Match winner (1X2)","Double chance","Both teams to score")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("goal_scanner_pro", MODE_PRIVATE)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(
                primary=Color(0xFF69AFFF), secondary=Color(0xFF9CC7FF),
                background=Color(0xFF0B1422), surface=Color(0xFF142238),
                surfaceVariant=Color(0xFF1C2D45), onSurface=Color(0xFFF1F5FB),
                onBackground=Color(0xFFF1F5FB)
            ) else lightColorScheme(
                primary=Blue, secondary=Color(0xFF315F9B),
                background=Color(0xFFF4F7FC), surface=Color.White,
                surfaceVariant=Color(0xFFEAF0F8), onSurface=Color(0xFF17243A),
                onBackground=Color(0xFF17243A)
            )
            MaterialTheme(colorScheme=scheme) { GoalScannerApp(prefs) }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalScannerApp(prefs: android.content.SharedPreferences) {
    var tab by rememberSaveable { mutableStateOf("Home") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var apiUrl by rememberSaveable { mutableStateOf(prefs.getString("api_url","") ?: "") }
    var season by rememberSaveable { mutableStateOf(prefs.getString("season","") ?: "") }
    var division by rememberSaveable { mutableStateOf(prefs.getString("division","") ?: "") }
    var connection by rememberSaveable { mutableStateOf("Not configured") }
    var providers by rememberSaveable { mutableStateOf("Not checked") }
    var fixtureSummary by rememberSaveable { mutableStateOf("No fixtures collected yet") }
    var fixtureRows by remember { mutableStateOf(emptyList<String>()) }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var scannerView by rememberSaveable { mutableStateOf("Recommended") }
    var minimum by rememberSaveable { mutableFloatStateOf(prefs.getFloat("minimum",60f)) }
    var markets by remember { mutableStateOf(prefs.getStringSet("markets",MarketIds.toSet())?.toSet() ?: MarketIds.toSet()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Goal Scanner Pro",fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text("FOOTBALL INTELLIGENCE",style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.sp)
                } },
                actions = { IconButton(onClick={settings=!settings}) { Icon(Icons.Default.Settings,"Settings") } }
            )
        },
        bottomBar = {
            if (!settings) NavigationBar {
                listOf(NavItem("Home",Icons.Default.Home),NavItem("Fixtures",Icons.Default.CalendarMonth),
                    NavItem("Scanner",Icons.Default.SportsSoccer),NavItem("Analysis",Icons.Default.Analytics),
                    NavItem("Results",Icons.Default.QueryStats)).forEach { item ->
                    NavigationBarItem(selected=tab==item.label,onClick={tab=item.label},
                        icon={Icon(item.icon,item.label)},label={Text(item.label,fontSize=10.sp)})
                }
            }
        }
    ) { padding ->
        if (settings) {
            SettingsScreen(Modifier.padding(padding),apiUrl,{apiUrl=it},season,{season=it},division,{division=it},
                connection,providers,busy,message,
                onSave={
                    prefs.edit().putString("api_url",apiUrl.trim().trimEnd('/'))
                        .putString("season",season.trim()).putString("division",division.trim())
                        .putFloat("minimum",minimum).putStringSet("markets",markets).apply()
                    message="Settings saved on this device."
                },
                onTest={
                    scope.launch {
                        busy=true; connection="Checking backend…"
                        try {
                            val health=apiRequest(apiUrl,"health")
                            connection="Connected · version "+health.optString("version","available")
                            val manifest=apiRequest(apiUrl,"sources/manifest")
                            providers=if(manifest.length()>0) "Provider manifest received" else "Manifest unavailable"
                            message="Backend health check passed."
                        } catch(e:Exception) {
                            connection="Connection failed"; message=e.message ?: "Could not reach the backend."
                        } finally { busy=false }
                    }
                },
                onBack={settings=false}
            )
        } else when(tab) {
            "Home" -> HomeScreen(Modifier.padding(padding),connection,providers,fixtureSummary,
                {tab="Scanner"},{tab="Fixtures"})
            "Fixtures" -> FixturesScreen(Modifier.padding(padding),apiUrl,season,division,fixtureSummary,
                fixtureRows,busy,message) {
                scope.launch {
                    busy=true; message=""
                    try {
                        val body=JSONObject().put("match_date",LocalDate.now().toString())
                            .put("season",season.trim()).put("division",division.trim())
                            .put("hours_ahead",48).put("include_squads",true)
                        val result=apiRequest(apiUrl,"sources/collect","POST",body)
                        val array=result.optJSONArray("fixtures")
                        fixtureRows=if(array==null) emptyList() else (0 until array.length()).mapNotNull { i ->
                            array.optJSONObject(i)?.let { fx ->
                                val home=fx.optString("home"); val away=fx.optString("away")
                                if(home.isBlank()||away.isBlank()) null else "$home vs $away · "+fx.optString("kickoff","Kickoff unavailable")
                            }
                        }
                        fixtureSummary="${fixtureRows.size} fixtures · "+result.optString("overall_status","unknown")
                        providers="Collection status: "+result.optString("overall_status","unknown")
                        message="Collection completed. Check source coverage before relying on match data."
                    } catch(e:Exception) { message=e.message ?: "Fixture collection failed." }
                    finally { busy=false }
                }
            }
            "Scanner" -> ScannerScreen(Modifier.padding(padding),scannerView,{scannerView=it},
                minimum,{minimum=it},markets,{markets=it},{
                    prefs.edit().putFloat("minimum",minimum).putStringSet("markets",markets).apply()
                    message="Scanner preferences saved."
                },message)
            "Analysis" -> AnalysisScreen(Modifier.padding(padding))
            else -> ResultsScreen(Modifier.padding(padding))
        }
    }
}

private suspend fun apiRequest(baseUrl:String,path:String,method:String="GET",body:JSONObject?=null):JSONObject =
    withContext(Dispatchers.IO) {
        val base=baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")||base.startsWith("http://")) {
            "Enter your backend URL in Settings. No public backend has been deployed yet."
        }
        val conn=(URL("$base/$path").openConnection() as HttpURLConnection).apply {
            requestMethod=method; connectTimeout=12000; readTimeout=30000
            setRequestProperty("Accept","application/json")
            if(body!=null) { doOutput=true; setRequestProperty("Content-Type","application/json") }
        }
        try {
            if(body!=null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code=conn.responseCode
            val stream=if(code in 200..299) conn.inputStream else conn.errorStream
            val response=stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if(code !in 200..299) throw IllegalStateException("Backend returned HTTP $code: $response")
            if(response.isBlank()) JSONObject() else JSONObject(response)
        } finally { conn.disconnect() }
    }

@Composable
private fun ScreenColumn(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable
private fun Heading(title:String,subtitle:String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun Metric(title:String,value:String,detail:String) {
    Card(shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(title,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
            Text(detail,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
private fun HomeScreen(modifier:Modifier,connection:String,providers:String,fixtures:String,onScanner:()->Unit,onFixtures:()->Unit) {
    ScreenColumn(modifier) {
        Card(colors=CardDefaults.cardColors(containerColor=Navy),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("YOUR FOOTBALL WORKSPACE",color=Color(0xFFB8D2F2),style=MaterialTheme.typography.labelMedium,letterSpacing=1.2.sp)
                Text("See the game\nbeyond the odds.",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
                Text("Evidence-led match analysis, goal markets and performance tracking.",color=Color(0xFFD1DEEF))
                Button(onClick=onScanner,colors=ButtonDefaults.buttonColors(containerColor=Blue)) { Text("Open Goal Scanner") }
            }
        }
        Heading("System overview","Live information appears after the backend is connected")
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Fixtures","—",fixtures) }
            Box(Modifier.weight(1f)) { Metric("API",if(connection.startsWith("Connected")) "Online" else "Offline",connection) }
        }
        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Data sources",fontWeight=FontWeight.Bold)
            Text(providers,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Odds remain separate from model probabilities. Missing information is never replaced with invented values.",
                style=MaterialTheme.typography.bodySmall)
        } }
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Button(onClick=onFixtures,modifier=Modifier.weight(1f)) { Text("Fixtures") }
            OutlinedButton(onClick=onScanner,modifier=Modifier.weight(1f)) { Text("Scanner") }
        }
        Heading("Two scanner modes")
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Recommended","Balanced","Quality-led shortlist") }
            Box(Modifier.weight(1f)) { Metric("Full scanner","Advanced","Detailed filters") }
        }
    }
}
@Composable
private fun FixturesScreen(modifier:Modifier,apiUrl:String,season:String,division:String,summary:String,
    fixtures:List<String>,busy:Boolean,message:String,onCollect:()->Unit) {
    ScreenColumn(modifier) {
        Heading("Fixtures","Retrieve today's fixtures and provider data.")
        Card { Column(Modifier.padding(15.dp)) { Text("Latest collection",fontWeight=FontWeight.Bold); Text(summary,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        Button(onClick=onCollect,enabled=!busy&&apiUrl.isNotBlank()&&season.isNotBlank()&&division.isNotBlank(),modifier=Modifier.fillMaxWidth()) {
            Text(if(busy) "Collecting…" else "Collect today's fixtures")
        }
        if(apiUrl.isBlank()) Notice("Backend not configured","Enter the address of your running Goal Scanner API in Settings.")
        if(season.isBlank()||division.isBlank()) Notice("Competition settings required","Set the Football-Data season and division codes in Settings.")
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        if(fixtures.isNotEmpty()) {
            Heading("Retrieved fixtures","${fixtures.size} records")
            fixtures.forEach { Card { Text(it,Modifier.fillMaxWidth().padding(14.dp)) } }
        } else Notice("No fixture records","No fixtures have been retrieved. Provider failures and missing coverage will not be treated as empty match statistics.")
    }
}
@Composable
private fun ScannerScreen(modifier:Modifier,view:String,onView:(String)->Unit,minimum:Float,onMinimum:(Float)->Unit,
    selected:Set<String>,onSelected:(Set<String>)->Unit,onSave:()->Unit,message:String) {
    var date by rememberSaveable { mutableStateOf("Today") }
    var competition by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var minOdds by rememberSaveable { mutableStateOf("1.10") }
    var maxOdds by rememberSaveable { mutableStateOf("") }
    ScreenColumn(modifier) {
        Heading("Goal Scanner","Balanced recommendations and detailed statistical search.")
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)).padding(4.dp)) {
            listOf("Recommended","Full scanner").forEach { label ->
                Box(Modifier.weight(1f).background(if(view==label) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(10.dp)).clickable { onView(label) }.padding(11.dp),contentAlignment=Alignment.Center) {
                    Text(label,color=if(view==label) Color.White else MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold)
                }
            }
        }
        if(view=="Recommended") Card {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Balanced quality",fontWeight=FontWeight.Bold)
                Text("Rank using probability, statistical consistency, data completeness and available bookmaker value.")
                Text("Initial minimum estimated probability: ${minimum.toInt()}%",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Text("Insufficient-data matches are excluded. No threshold is a guarantee.",style=MaterialTheme.typography.bodySmall)
            }
        } else {
            Heading("Quick filters")
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf("Today","Tomorrow","7 days").forEach { item ->
                    FilterChip(selected=date==item,onClick={date=item},label={Text(item)})
                }
            }
            OutlinedTextField(competition,{competition=it},label={Text("Competition contains")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(team,{team=it},label={Text("Search team")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minOdds,{minOdds=it},label={Text("Minimum odds")},modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(maxOdds,{maxOdds=it},label={Text("Maximum odds")},modifier=Modifier.weight(1f),singleLine=true)
            }
        }
        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Minimum estimated probability · ${minimum.toInt()}%",fontWeight=FontWeight.Bold)
            Slider(value=minimum,onValueChange=onMinimum,valueRange=50f..95f)
            Text("Adjustable filter. Calibration must be measured against recorded outcomes.",style=MaterialTheme.typography.bodySmall)
        } }
        Heading("Selected markets","${selected.size} of 9 enabled")
        MarketIds.indices.forEach { index ->
            Card {
                Row(Modifier.fillMaxWidth().clickable {
                    onSelected(if(MarketIds[index] in selected) selected-MarketIds[index] else selected+MarketIds[index])
                }.padding(horizontal=13.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(MarketNames[index],Modifier.weight(1f),fontWeight=FontWeight.Medium)
                    Checkbox(checked=MarketIds[index] in selected,onCheckedChange={checked ->
                        onSelected(if(checked) selected+MarketIds[index] else selected-MarketIds[index])
                    })
                }
            }
        }
        Button(onClick=onSave,modifier=Modifier.fillMaxWidth()) { Text("Save scanner preferences") }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        Notice("Fixture-level recommendations are not active yet","The backend calculates probabilities when valid home and away goal expectations are supplied. It does not yet generate a trained fixture-level shortlist. No matches or estimates will be invented.")
    }
}
@Composable
private fun AnalysisScreen(modifier:Modifier) {
    ScreenColumn(modifier) {
        Heading("Match analysis","Summary first, supporting evidence below.")
        Card(shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("HOME TEAM     VS     AWAY TEAM",fontWeight=FontWeight.Bold)
                Text("Select a fixture to begin.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Text("Main prediction",fontWeight=FontWeight.Bold)
                Text("Not available",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                Text("No fixture-level prediction has been generated. No probability is displayed without supported match data.")
            }
        }
        Heading("Detailed analysis")
        listOf("Team form and home/away performance","Goals scored and conceded","Expected goals, where available",
            "Head-to-head with sample-size warnings","Predicted lineups only when player evidence is sufficient",
            "Model probabilities and bookmaker odds shown separately").forEach { Card { Text(it,Modifier.fillMaxWidth().padding(14.dp)) } }
    }
}
@Composable
private fun ResultsScreen(modifier:Modifier) {
    ScreenColumn(modifier) {
        Heading("Results & performance","Scanner predictions and manually recorded bets remain separate.")
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Predictions","0","No recorded recommendations") }
            Box(Modifier.weight(1f)) { Metric("Accuracy","—","Needs settled outcomes") }
        }
        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Automatic prediction history",fontWeight=FontWeight.Bold)
            Text("Each qualifying recommendation is intended to retain its match, market, probability, odds snapshot, timestamp and model version.")
            Text("Verified final scores will settle predictions automatically, with a manual correction path. The current backend does not yet emit fixture-level recommendations or a settlement feed.")
        } }
        Notice("No settled predictions yet","Accuracy, calibration, Brier score and profit/loss remain blank until genuine predictions and verified outcomes are recorded.")
    }
}
@Composable
private fun SettingsScreen(modifier:Modifier,apiUrl:String,onApiUrl:(String)->Unit,season:String,onSeason:(String)->Unit,
    division:String,onDivision:(String)->Unit,connection:String,providers:String,busy:Boolean,message:String,
    onSave:()->Unit,onTest:()->Unit,onBack:()->Unit) {
    ScreenColumn(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("Back") }
            Text("Settings",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
        Heading("Backend connection","Connect to a deployed Goal Scanner FastAPI service.")
        OutlinedTextField(apiUrl,onApiUrl,modifier=Modifier.fillMaxWidth(),label={Text("API base URL")},
            placeholder={Text("https://your-server.example")},singleLine=true)
        Text("A physical phone needs a server address reachable over the internet or local network. Use HTTPS outside a trusted local development network.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(season,onSeason,modifier=Modifier.fillMaxWidth(),label={Text("Football-Data season code")},placeholder={Text("2627")},singleLine=true)
        OutlinedTextField(division,onDivision,modifier=Modifier.fillMaxWidth(),label={Text("Competition division code")},placeholder={Text("E0")},singleLine=true)
        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Connection",fontWeight=FontWeight.Bold)
            Text(connection,color=MaterialTheme.colorScheme.primary)
            Text(providers,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onTest,enabled=!busy,modifier=Modifier.weight(1f)) { Text(if(busy) "Checking…" else "Test connection") }
            Button(onClick=onSave,modifier=Modifier.weight(1f)) { Text("Save settings") }
        }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        Heading("App appearance")
        Card { Text("Automatic light/dark appearance · Blue accent",Modifier.padding(14.dp)) }
        Heading("Scanner defaults")
        Card { Text("Balanced shortlist · 60% initial probability threshold · Exclude insufficient data",Modifier.padding(14.dp)) }
        Card { Text("Goal Scanner Pro · Version 1.0.0",Modifier.padding(14.dp)) }
    }
}
@Composable
private fun Notice(title:String,body:String) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer),shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(title,fontWeight=FontWeight.Bold)
            Text(body,style=MaterialTheme.typography.bodySmall)
        }
    }
}
