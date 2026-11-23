package com.goalscanner.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private val Blue = Color(0xFF1976D2)
private val Navy = Color(0xFF101C32)
private const val DEFAULT_DATA_URL = "https://raw.githubusercontent.com/Ndumiso19894/goal-scanner-2.0/main"
private val MarketIds = listOf("team_corners_over","match_corners_over","match_bookings_over","team_bookings_over","match_over_2_5","btts_yes","match_winner_home","match_winner_draw","match_winner_away")
private val MarketNames = listOf("Team corners · Over 4.5+","Total corners · Over 8.5+","Match bookings · Over 3.5+","Team bookings · Over 1.5+","Total goals · Over 2.5","Both teams to score · Yes","1X2 · Home","1X2 · Draw","1X2 · Away")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("goal_scanner_pro", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7101)
        }
        val work = WorkManager.getInstance(this)
        work.enqueueUniquePeriodicWork("goal-scanner-release-check", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReleaseUpdateWorker>(12, TimeUnit.HOURS).build())
        work.enqueue(OneTimeWorkRequestBuilder<ReleaseUpdateWorker>().build())
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
    var apiUrl by rememberSaveable { mutableStateOf((prefs.getString("api_url",DEFAULT_DATA_URL) ?: DEFAULT_DATA_URL).replace("feature/android-goal-scanner-pro","main").ifBlank { DEFAULT_DATA_URL }) }
    var apiToken by rememberSaveable { mutableStateOf(prefs.getString("api_token","") ?: "") }
    var season by rememberSaveable { mutableStateOf((prefs.getString("season","2627") ?: "2627").ifBlank { "2627" }) }
    var division by rememberSaveable { mutableStateOf((prefs.getString("division","E0") ?: "E0").ifBlank { "E0" }) }
    var connection by rememberSaveable { mutableStateOf("Connecting to GitHub feed…") }
    var providers by rememberSaveable { mutableStateOf("Waiting for published data") }
    var fixtureSummary by rememberSaveable { mutableStateOf("Loading published fixtures…") }
    var fixtureRows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var selectedFixture by remember { mutableStateOf<JSONObject?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var scannerView by rememberSaveable { mutableStateOf("Recommended") }
    var minimum by rememberSaveable { mutableFloatStateOf(prefs.getFloat("minimum",60f)) }
    var markets by remember { mutableStateOf((prefs.getStringSet("markets",null)?.intersect(MarketIds.toSet())?.takeIf { it.isNotEmpty() } ?: MarketIds.toSet()).toSet()) }
    var recommendations by remember { mutableStateOf(emptyList<JSONObject>()) }
    var scanSummary by rememberSaveable { mutableStateOf("No scan has been run yet.") }
    var selectedRecommendation by remember { mutableStateOf<JSONObject?>(null) }
    var resultSummary by remember { mutableStateOf(JSONObject()) }
    var resultRows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var oddsMovementRows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var oddsMovementUpdated by rememberSaveable { mutableStateOf("Waiting for first odds snapshots") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(apiUrl) {
        if (apiUrl.isNotBlank()) {
            try {
                val stamp = System.currentTimeMillis()
                val feed = apiRequest(apiUrl, "docs/data/latest.json?ts=$stamp")
                connection = "Connected · data updated " + feed.optString("updated_at", "not run yet")
                providers = "GitHub feed · " + feed.optInt("recommendation_count") + " selections"
                scanSummary = "${feed.optString("status", "unknown")} · ${feed.optInt("fixture_count")} fixtures · ${feed.optInt("recommendation_count")} selections"
                val picks = feed.optJSONArray("recommendations")
                recommendations = if (picks == null) emptyList() else (0 until picks.length()).mapNotNull { picks.optJSONObject(it) }
                val fixtureFeed = apiRequest(apiUrl, "docs/data/fixtures.json?ts=$stamp")
                val fixtureArray = fixtureFeed.optJSONArray("fixtures")
                fixtureRows = if (fixtureArray == null) emptyList() else (0 until fixtureArray.length()).mapNotNull { fixtureArray.optJSONObject(it) }
                fixtureSummary = "${fixtureRows.size} fixtures · updated " + fixtureFeed.optString("updated_at", "not run yet")
                val oddsFeed = apiRequest(apiUrl, "docs/data/odds-movements.json?ts=$stamp")
                val movementArray = oddsFeed.optJSONArray("movements")
                oddsMovementRows = if (movementArray == null) emptyList() else (0 until movementArray.length()).mapNotNull { movementArray.optJSONObject(it) }
                oddsMovementUpdated = "Updated " + oddsFeed.optString("updated_at", "not available")
                val resultFeed = apiRequest(apiUrl, "docs/data/results.json?ts=$stamp")
                resultSummary = resultFeed.optJSONObject("summary") ?: JSONObject()
                val recent = resultFeed.optJSONArray("recent")
                resultRows = if (recent == null) emptyList() else (0 until recent.length()).mapNotNull { recent.optJSONObject(it) }
            } catch (e: Exception) {
                connection = "Feed unavailable"
                message = e.message ?: "Could not load published Goal Scanner data."
            }
        }
    }

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
                    NavItem("Results",Icons.Default.QueryStats),NavItem("Odds",Icons.Default.TrendingDown)).forEach { item ->
                    NavigationBarItem(selected=tab==item.label,onClick={tab=item.label},
                        icon={Icon(item.icon,item.label)},label={Text(item.label,fontSize=10.sp)})
                }
            }
        }
    ) { padding ->
        if (settings) {
            SettingsScreen(Modifier.padding(padding),apiUrl,{apiUrl=it},apiToken,{apiToken=it},season,{season=it},division,{division=it},
                connection,providers,busy,message,
                onSave={
                    prefs.edit().putString("api_url",apiUrl.trim().trimEnd('/'))
                        .putString("api_token",apiToken.trim()).putString("season",season.trim()).putString("division",division.trim())
                        .putFloat("minimum",minimum).putStringSet("markets",markets).apply()
                    message="Settings saved on this device."
                },
                onTest={
                    scope.launch {
                        busy=true; connection="Checking published data…"
                        try {
                            val feed=apiRequest(apiUrl,"docs/data/latest.json?ts=" + System.currentTimeMillis())
                            connection="Connected · data updated "+feed.optString("updated_at","unknown")
                            providers="Published feed · "+feed.optInt("recommendation_count")+" selections"
                            message="GitHub data feed is reachable."
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
            "Fixtures" -> FixturesScreen(Modifier.padding(padding),fixtureSummary,
                fixtureRows,busy,message,onSelect={ fixture -> selectedFixture=fixture; tab="Match" }) {
                scope.launch {
                    busy=true; message=""
                    try {
                        val result=apiRequest(apiUrl,"docs/data/fixtures.json?ts=" + System.currentTimeMillis())
                        val array=result.optJSONArray("fixtures")
                        fixtureRows=if(array==null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                        fixtureSummary="${fixtureRows.size} fixtures · updated "+result.optString("updated_at","unknown")
                        providers="Published fixtures feed"
                        message="Latest scheduled fixture data loaded."
                    } catch(e:Exception) { message=e.message ?: "Fixture collection failed." }
                    finally { busy=false }
                }
            }
            "Scanner" -> ScannerScreen(Modifier.padding(padding),scannerView,{scannerView=it},
                minimum,{minimum=it},markets,{markets=it},{
                    prefs.edit().putFloat("minimum",minimum).putStringSet("markets",markets).apply()
                    message="Scanner preferences saved."
                },message,apiUrl,season,division,recommendations,scanSummary,busy,
                onRun={dateChoice,competitionQuery,teamQuery,minOdds,maxOdds ->
                    scope.launch {
                        busy=true; message=""
                        try {
                            val response=apiRequest(apiUrl,"docs/data/latest.json?ts=" + System.currentTimeMillis())
                            val array=response.optJSONArray("recommendations")
                            val allRows=if(array==null) emptyList() else (0 until array.length()).mapNotNull { i -> array.optJSONObject(i) }
                            val today=LocalDate.now()
                            recommendations=allRows.filter { item ->
                                val kickoffDay=runCatching { LocalDate.parse(item.optString("kickoff").take(10)) }.getOrNull()
                                val dateOk=when(dateChoice) {
                                    "Tomorrow" -> kickoffDay==today.plusDays(1)
                                    "7 days" -> kickoffDay!=null && !kickoffDay.isBefore(today) && !kickoffDay.isAfter(today.plusDays(7))
                                    else -> kickoffDay==today
                                }
                                val competitionOk=competitionQuery.isBlank() || item.optString("competition").contains(competitionQuery,true)
                                val teamOk=teamQuery.isBlank() || (item.optString("home")+" "+item.optString("away")).contains(teamQuery,true)
                                val odds=if(item.isNull("odds")) null else item.optDouble("odds")
                                val oddsOk=(minOdds==null || (odds!=null && odds>=minOdds)) && (maxOdds==null || (odds!=null && odds<=maxOdds))
                                dateOk && competitionOk && teamOk && oddsOk && item.optDouble("estimated_probability")>=minimum/100.0 && marketSelectionId(item) in markets
                            }.let { if(scannerView=="Recommended") it.take(10) else it }
                            scanSummary="${response.optString("status","unknown")} · ${response.optInt("fixture_count")} fixtures · ${allRows.size} published selections · updated ${response.optString("updated_at","unknown")}"
                            message=if(recommendations.isEmpty()) "No published selections match these filters. The next scheduled scan will refresh the feed." else "Latest published selections loaded. Select a match to inspect its summary."
                        } catch(e:Exception) { scanSummary="Scan failed"; message=e.message ?: "Scanner request failed." }
                        finally { busy=false }
                    }
                },
                onSelect={candidate -> selectedRecommendation=candidate; tab="Analysis"})
            "Match" -> FixtureDetailsScreen(Modifier.padding(padding),selectedFixture,recommendations,
                onBack={tab="Fixtures"},onSelect={ candidate -> selectedRecommendation=candidate; tab="Analysis" })
            "Analysis" -> AnalysisScreen(Modifier.padding(padding),selectedRecommendation,
                recommendations.filter { it.optString("fixture_id")==selectedRecommendation?.optString("fixture_id") })
            "Odds" -> DroppingOddsScreen(Modifier.padding(padding),oddsMovementRows,oddsMovementUpdated) {
                scope.launch {
                    busy=true
                    try {
                        val feed=apiRequest(apiUrl,"docs/data/odds-movements.json?ts=" + System.currentTimeMillis())
                        val array=feed.optJSONArray("movements")
                        oddsMovementRows=if(array==null) emptyList() else (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                        oddsMovementUpdated="Updated "+feed.optString("updated_at","not available")
                    } catch(e:Exception) { message=e.message ?: "Could not refresh odds movement data." }
                    finally { busy=false }
                }
            }
            else -> ResultsScreen(Modifier.padding(padding),resultSummary,resultRows,busy) {
                scope.launch {
                    busy=true
                    try {
                        val response=apiRequest(apiUrl,"docs/data/results.json?ts=" + System.currentTimeMillis())
                        resultSummary=response.optJSONObject("summary") ?: JSONObject()
                        val array=response.optJSONArray("recent")
                        resultRows=if(array==null) emptyList() else (0 until array.length()).mapNotNull { i -> array.optJSONObject(i) }
                        message="Scores and prediction history refreshed."
                    } catch(e:Exception) { message=e.message ?: "Could not refresh results." }
                    finally { busy=false }
                }
            }
        }
    }
}

private suspend fun apiRequest(baseUrl:String,path:String,method:String="GET",body:JSONObject?=null,token:String?=null):JSONObject =
    withContext(Dispatchers.IO) {
        val base=baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")||base.startsWith("http://")) {
            "Enter the published Goal Scanner data URL in Settings."
        }
        val conn=(URL("$base/$path").openConnection() as HttpURLConnection).apply {
            requestMethod=method; connectTimeout=12000; readTimeout=30000
            setRequestProperty("Accept","application/json")
            if(!token.isNullOrBlank()) setRequestProperty("Authorization","Bearer ${token.trim()}")
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
        Heading("System overview","Latest scheduled football data published through GitHub")
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
private fun FixturesScreen(
    modifier:Modifier,summary:String,fixtures:List<JSONObject>,busy:Boolean,message:String,
    onSelect:(JSONObject)->Unit,onCollect:()->Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Upcoming") }
    val visible=fixtures.filter { fx ->
        val text=(fx.optString("home")+" "+fx.optString("away")+" "+fx.optString("competition")).lowercase()
        val status=fx.optString("status","NS").uppercase()
        val queryOk=query.isBlank() || text.contains(query.lowercase())
        val statusOk=when(filter) {
            "Upcoming" -> status in listOf("NS","TBD","SCHEDULED","")
            "Live" -> status in listOf("1H","2H","HT","LIVE","ET","P")
            "Finished" -> status in listOf("FT","AET","PEN","FINISHED")
            else -> true
        }
        queryOk && statusOk
    }.sortedBy { it.optString("kickoff") }
    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fixtures",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
                Text("Browse matches and open each one for its details.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick=onCollect,enabled=!busy) { Text(if(busy) "Loading…" else "Refresh") }
        }
        Card(shape=RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${fixtures.size} matches available",fontWeight=FontWeight.Bold)
                Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search team or competition")})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("All","Upcoming","Live","Finished").forEach { item ->
                FilterChip(selected=filter==item,onClick={filter=item},label={Text(item)})
            }
        }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        if(visible.isEmpty()) Notice("No matching fixtures","Try another search or status filter, or refresh the fixture feed.")
        visible.forEach { fx ->
            val status=fx.optString("status","NS").uppercase()
            Card(Modifier.fillMaxWidth().clickable { onSelect(fx) },shape=RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(fx.optString("competition",fx.optString("country","Football")),modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                        StatusPill(status)
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(fx.optString("home","Home"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                            Text(fx.optString("away","Away"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            if(!fx.isNull("home_score")&&!fx.isNull("away_score"))
                                Text("${fx.optInt("home_score")} - ${fx.optInt("away_score")}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                            else Text(formatKickoff(fx.optString("kickoff")),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                            Text("View match  ›",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun StatusPill(status:String) {
    val live=status in listOf("1H","2H","HT","LIVE","ET","P")
    Surface(color=if(live) Color(0xFFE3F7EB) else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(50)) {
        Text(status.ifBlank { "NS" },modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp),
            style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,
            color=if(live) Color(0xFF167A45) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun formatKickoff(value:String):String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))
}.getOrElse { value.replace("T"," ").take(16) }
private fun marketSelectionId(item:JSONObject):String = if(item.optString("market")=="match_winner") "match_winner_${item.optString("selection")}" else item.optString("market")
private fun marketTitle(item:JSONObject):String {
    val market=item.optString("market"); val selection=item.optString("selection")
    val line=if(item.isNull("line")) "" else item.optString("line")
    return when(market) {
        "team_corners_over" -> "${selection.replaceFirstChar { it.uppercase() }} team corners · Over ${line.ifBlank { "line unavailable" }}"
        "match_corners_over" -> "Total match corners · Over ${line.ifBlank { "line unavailable" }}"
        "match_bookings_over" -> "Total match bookings · Over ${line.ifBlank { "line unavailable" }}"
        "team_bookings_over" -> "${selection.replaceFirstChar { it.uppercase() }} team bookings · Over ${line.ifBlank { "line unavailable" }}"
        "match_over_2_5" -> "Total goals · Over 2.5"
        "btts_yes" -> "Both teams to score · Yes"
        "match_winner" -> "1X2 · ${selection.replaceFirstChar { it.uppercase() }}"
        else -> market.replace("_"," ").replaceFirstChar { it.uppercase() }
    }
}
@Composable
private fun FixtureDetailsScreen(
    modifier:Modifier,fixture:JSONObject?,recommendations:List<JSONObject>,onBack:()->Unit,onSelect:(JSONObject)->Unit
) {
    ScreenColumn(modifier) {
        TextButton(onClick=onBack) { Text("‹  All fixtures") }
        if(fixture==null) Notice("No fixture selected","Return to the fixtures list and select a match.")
        else {
            val picks=recommendations.filter { it.optString("fixture_id")==fixture.optString("id") }
            Card(colors=CardDefaults.cardColors(containerColor=Navy),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(fixture.optString("competition","Football").uppercase(),color=Color(0xFFB8D2F2),style=MaterialTheme.typography.labelSmall)
                    Text(fixture.optString("home"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text("VS",color=Color(0xFF9CB7D9),style=MaterialTheme.typography.labelSmall)
                    Text(fixture.optString("away"),color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    if(!fixture.isNull("home_score")&&!fixture.isNull("away_score"))
                        Text("${fixture.optInt("home_score")} - ${fixture.optInt("away_score")}",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
                    else Text(formatKickoff(fixture.optString("kickoff")),color=Color(0xFFD1DEEF))
                    Text("${fixture.optString("country")} · ${fixture.optString("status","NS")}",color=Color(0xFFD1DEEF),style=MaterialTheme.typography.bodySmall)
                }
            }
            Heading("Available model selections","${picks.size} published selections for this match")
            if(picks.isEmpty()) Notice("No qualifying selections published","This fixture did not meet the scanner's data and probability filters. That does not mean the match has no betting markets.")
            picks.forEach { pick ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(pick) },shape=RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(marketTitle(pick),fontWeight=FontWeight.Bold)
                            Text("Model estimate ${(pick.optDouble("estimated_probability")*100).toInt()}%  ·  ${if(pick.isNull("odds")) "Odds unavailable" else "Odds "+String.format("%.2f",pick.optDouble("odds"))}",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Notice("About the data","Only markets with sufficient historical information appear here. A missing estimate is not treated as zero, and bookmaker odds do not determine the model probability.")
        }
    }
}
@Composable
private fun ScannerScreen(
    modifier:Modifier,view:String,onView:(String)->Unit,minimum:Float,onMinimum:(Float)->Unit,
    selected:Set<String>,onSelected:(Set<String>)->Unit,onSave:()->Unit,message:String,
    apiUrl:String,season:String,division:String,recommendations:List<JSONObject>,scanSummary:String,
    busy:Boolean,onRun:(String,String,String,Double?,Double?)->Unit,onSelect:(JSONObject)->Unit
) {
    var date by rememberSaveable { mutableStateOf("Today") }
    var competition by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var minOdds by rememberSaveable { mutableStateOf("") }
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
                Text("Rank by estimated probability, historical sample quality and available bookmaker value.")
                Text("Initial minimum estimated probability: ${minimum.toInt()}%",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Text("Only fixtures with sufficient prior home/away history qualify. Estimates are not calibrated guarantees.",style=MaterialTheme.typography.bodySmall)
            }
        } else {
            Heading("Full scanner filters")
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf("Today","Tomorrow","7 days").forEach { item ->
                    FilterChip(selected=date==item,onClick={date=item},label={Text(item)})
                }
            }
            OutlinedTextField(competition,{competition=it},label={Text("Competition contains · display filter")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(team,{team=it},label={Text("Search team · display filter")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minOdds,{minOdds=it},label={Text("Minimum odds (optional)")},modifier=Modifier.weight(1f),singleLine=true)
                OutlinedTextField(maxOdds,{maxOdds=it},label={Text("Maximum odds (optional)")},modifier=Modifier.weight(1f),singleLine=true)
            }
            Text("Filters are applied to the latest published scan on this device.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Minimum estimated probability · ${minimum.toInt()}%",fontWeight=FontWeight.Bold)
            Slider(value=minimum,onValueChange=onMinimum,valueRange=50f..95f)
            Text("Your selected initial threshold is 60%.",style=MaterialTheme.typography.bodySmall)
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
        Button(onClick={onRun(date,competition,team,minOdds.toDoubleOrNull(),maxOdds.toDoubleOrNull())},
            enabled=!busy&&apiUrl.isNotBlank()&&season.isNotBlank()&&division.isNotBlank()&&selected.isNotEmpty(),
            modifier=Modifier.fillMaxWidth()) {
            Text(if(busy) "Loading latest scan…" else "Load latest published scan")
        }
        OutlinedButton(onClick=onSave,modifier=Modifier.fillMaxWidth()) { Text("Save scanner preferences") }
        Text(scanSummary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        if(apiUrl.isBlank()) Notice("Backend not connected","Enter the deployed API address in Settings before running the scanner.")
        if(recommendations.isNotEmpty()) {
            Heading(if(view=="Recommended") "Balanced shortlist" else "All qualifying selections",
                "${recommendations.size} recorded candidates · tap one for match analysis")
            recommendations.forEach { item ->
                Card(Modifier.fillMaxWidth().clickable { onSelect(item) },shape=RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("${item.optString("home")} vs ${item.optString("away")}",fontWeight=FontWeight.Bold)
                        Text(marketTitle(item),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("Estimate ${(item.optDouble("estimated_probability")*100).toInt()}%",fontWeight=FontWeight.Bold)
                            Text(if(item.isNull("odds")) "Odds unavailable" else "Odds ${item.optDouble("odds")}",
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Data quality ${(item.optDouble("data_quality")*100).toInt()}% · rank ${(item.optDouble("rank_score")*100).toInt()}",
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else Notice("No recommendations displayed","No published selections match this view. The scheduled GitHub scan will refresh the feed automatically.")
    }
}
@Composable
private fun AnalysisScreen(modifier:Modifier,candidate:JSONObject?,related:List<JSONObject>) {
    ScreenColumn(modifier) {
        Heading("Match analysis","A clear match summary with the exact market line behind every estimate.")
        if(candidate==null) Notice("No match selected","Open a match from Fixtures or Scanner to see its analysis.")
        else {
            val home=candidate.optString("home"); val away=candidate.optString("away")
            val probability=(candidate.optDouble("estimated_probability")*100).toInt()
            Card(colors=CardDefaults.cardColors(containerColor=Navy),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Text(candidate.optString("competition","Competition unavailable").uppercase(),color=Color(0xFFB8D2F2),style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp)
                    Text(home,style=MaterialTheme.typography.headlineSmall,color=Color.White,fontWeight=FontWeight.Bold)
                    Text("VS",style=MaterialTheme.typography.labelSmall,color=Color(0xFF9CB7D9))
                    Text(away,style=MaterialTheme.typography.headlineSmall,color=Color.White,fontWeight=FontWeight.Bold)
                    Text(formatKickoff(candidate.optString("kickoff")),color=Color(0xFFD1DEEF),style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color=Color(0xFF405573))
                    Text("SELECTED MARKET",color=Color(0xFFB8D2F2),style=MaterialTheme.typography.labelSmall)
                    Text(marketTitle(candidate),color=Color.White,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("$probability%",style=MaterialTheme.typography.displaySmall,color=Color.White,fontWeight=FontWeight.ExtraBold)
                        Text("model estimate",color=Color(0xFFD1DEEF),modifier=Modifier.padding(bottom=7.dp))
                    }
                    Text(if(candidate.isNull("odds")) "Bookmaker price unavailable" else "Bookmaker odds: "+String.format("%.2f",candidate.optDouble("odds")),
                        color=Color(0xFFD1DEEF))
                }
            }
            val sample=candidate.optJSONObject("sample") ?: JSONObject()
            Heading("Historical sample","Matches used before this fixture's kickoff")
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { Metric("Home at home",sample.optInt("home_home_matches").toString(),"Previous matches") }
                Box(Modifier.weight(1f)) { Metric("Away away",sample.optInt("away_away_matches").toString(),"Previous matches") }
            }
            val counts=candidate.optJSONObject("count_expectations") ?: JSONObject()
            val corners=counts.optJSONObject("corners")
            val cards=counts.optJSONObject("bookings")
            if(corners!=null || cards!=null) {
                Heading("Expected match counts","Historical averages after venue and league adjustment")
                if(corners!=null) {
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f)) { Metric("Home corners","%.1f".format(corners.optDouble("home_lambda")),"Expected count") }
                        Box(Modifier.weight(1f)) { Metric("Away corners","%.1f".format(corners.optDouble("away_lambda")),"Expected count") }
                    }
                }
                if(cards!=null) {
                    Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f)) { Metric("Home bookings","%.1f".format(cards.optDouble("home_lambda")),"Expected count") }
                        Box(Modifier.weight(1f)) { Metric("Away bookings","%.1f".format(cards.optDouble("away_lambda")),"Expected count") }
                    }
                }
            }
            val marketRows=related.distinctBy { it.optString("market")+"|"+it.optString("selection")+"|"+it.optString("line") }
            Heading("Markets for this match","Each Over selection shows its actual threshold")
            if(marketRows.isEmpty()) Notice("No additional market estimates","Only the selected recommendation is available in this published snapshot.")
            marketRows.forEach { item ->
                val isSelected=item.optString("prediction_id")==candidate.optString("prediction_id")
                Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(
                    containerColor=if(isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                )) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(marketTitle(item),fontWeight=FontWeight.Bold)
                            Text("Probability ${(item.optDouble("estimated_probability")*100).toInt()}%  ·  ${if(item.isNull("odds")) "Odds unavailable" else "Odds "+String.format("%.2f",item.optDouble("odds"))}",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(!item.isNull("expected_value")) Text("Model price value: ${String.format("%+.1f%%",item.optDouble("expected_value")*100)}",
                                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                        }
                        if(isSelected) Text("SELECTED",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    }
                }
            }
            Heading("Model notes")
            Text("Data quality ${(candidate.optDouble("data_quality")*100).toInt()}% · ${candidate.optString("model_version")}",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Notice("Important limitation","These are uncalibrated historical-rate estimates, not proven betting edges. Corner and booking markets are shown only when sufficient historical statistics exist. Bookmaker prices are displayed separately and do not determine model probabilities.")
        }
    }
}
@Composable
private fun ResultsScreen(
    modifier:Modifier,summary:JSONObject,rows:List<JSONObject>,busy:Boolean,onRefresh:()->Unit
) {
    val settled=summary.optInt("settled",0)
    val accuracy=if(summary.isNull("accuracy")) "—" else "${(summary.optDouble("accuracy")*100).toInt()}%"
    ScreenColumn(modifier) {
        Heading("Results & performance","Recorded scanner predictions, settled only against verified full-time scores.")
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Predictions",summary.optInt("predictions").toString(),"${summary.optInt("pending")} pending") }
            Box(Modifier.weight(1f)) { Metric("Accuracy",accuracy,"${summary.optInt("wins")} wins · ${summary.optInt("losses")} losses") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Brier score",if(summary.isNull("brier_score")) "—" else String.format("%.3f",summary.optDouble("brier_score")),"Lower is better") }
            Box(Modifier.weight(1f)) { Metric("Profit / loss",if(summary.isNull("profit_units")) "—" else String.format("%.2f u",summary.optDouble("profit_units")),"${summary.optInt("priced_settled")} priced results") }
        }
        Button(onClick=onRefresh,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text(if(busy) "Refreshing…" else "Refresh verified scores")
        }
        if(rows.isEmpty()) Notice("No prediction history yet","Run the scanner to save prediction snapshots. Final scores are matched to the original fixture IDs.")
        else {
            Heading("Prediction history","${rows.size} recent records")
            rows.forEach { item ->
                Card {
                    Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("${item.optString("home")} vs ${item.optString("away")}",fontWeight=FontWeight.Bold)
                        Text("${item.optString("market").replace("_"," ").uppercase()} · ${item.optString("selection").uppercase()}")
                        Text("${(item.optDouble("probability")*100).toInt()}% estimate · ${if(item.isNull("odds")) "odds unavailable" else "odds "+item.optDouble("odds")}")
                        Text("${item.optString("status")} ${if(item.isNull("final_home_goals")) "" else "· score ${item.optInt("final_home_goals")}-${item.optInt("final_away_goals")}"}",
                            color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
        Text("ROI is calculated only for settled records with captured odds. Each selection is counted as one flat unit; overlapping markets may be correlated.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
private fun SettingsScreen(modifier:Modifier,apiUrl:String,onApiUrl:(String)->Unit,apiToken:String,onApiToken:(String)->Unit,season:String,onSeason:(String)->Unit,
    division:String,onDivision:(String)->Unit,connection:String,providers:String,busy:Boolean,message:String,
    onSave:()->Unit,onTest:()->Unit,onBack:()->Unit) {
    ScreenColumn(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("Back") }
            Text("Settings",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
        Heading("Live data connection","Goal Scanner Pro reads the public data feed from its GitHub repository. No API key, paid server or manual endpoint is needed.")
        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("Goal Scanner Pro · Main data feed",fontWeight=FontWeight.Bold)
            Text("Fixtures, predictions, results and observed odds movements are published automatically by GitHub Actions.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Repository: Ndumiso19894/goal-scanner-2.0",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        } }
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
        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Goal Scanner Pro · Version 1.1.0",fontWeight=FontWeight.Bold)
            Text("Automatic release checks run when the app opens and every 12 hours. If a new signed version is published, you will receive a notification. Android will ask you to confirm installation.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
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
