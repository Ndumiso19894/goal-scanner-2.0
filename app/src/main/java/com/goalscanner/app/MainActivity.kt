package com.goalscanner.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
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

private val Blue = Color(0xFFB7F36B)
private val Navy = Color(0xFF101A27)
private val AccentTeal = Color(0xFF55D6C2)
private val MutedText = Color(0xFF91A2B3)
private val PanelBorder = Color(0xFF26384A)
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
            val scheme = darkColorScheme(
                primary=Color(0xFFB7F36B), onPrimary=Color(0xFF111A17),
                secondary=Color(0xFF55D6C2), onSecondary=Color(0xFF071B1A),
                background=Color(0xFF070C14), onBackground=Color(0xFFF4F7FA),
                surface=Color(0xFF101A27), onSurface=Color(0xFFF4F7FA),
                surfaceVariant=Color(0xFF1A2938), onSurfaceVariant=Color(0xFF9AAABB),
                secondaryContainer=Color(0xFF183A38), onSecondaryContainer=Color(0xFFDDF8F3),
                outline=Color(0xFF304456)
            )
            MaterialTheme(
                colorScheme=scheme,
                shapes=Shapes(
                    extraLarge=RoundedCornerShape(26.dp),
                    large=RoundedCornerShape(21.dp),
                    medium=RoundedCornerShape(16.dp),
                    small=RoundedCornerShape(12.dp)
                )
            ) { GoalScannerApp(prefs) }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalScannerApp(prefs: android.content.SharedPreferences) {
    var backStack by rememberSaveable { mutableStateOf(listOf("Home")) }
    val tab = backStack.lastOrNull() ?: "Home"
    val saveableStateHolder = rememberSaveableStateHolder()
    fun navigate(route: String) { if (backStack.lastOrNull() != route) backStack = backStack + route }
    fun goBack() { if (backStack.size > 1) backStack = backStack.dropLast(1) else backStack = listOf("Home") }
    var settings by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settings || backStack.size > 1 || tab != "Home") { if (settings) settings = false else goBack() }
    var apiUrl by rememberSaveable { mutableStateOf((prefs.getString("api_url",DEFAULT_DATA_URL) ?: DEFAULT_DATA_URL).replace("feature/android-goal-scanner-pro","main").ifBlank { DEFAULT_DATA_URL }) }
    var apiToken by rememberSaveable { mutableStateOf(prefs.getString("api_token","") ?: "") }
    var season by rememberSaveable { mutableStateOf((prefs.getString("season","2627") ?: "2627").ifBlank { "2627" }) }
    var division by rememberSaveable { mutableStateOf((prefs.getString("division","E0") ?: "E0").ifBlank { "E0" }) }
    var connection by rememberSaveable { mutableStateOf("Connecting to GitHub feed…") }
    var providers by rememberSaveable { mutableStateOf("Waiting for published data") }
    var fixtureSummary by rememberSaveable { mutableStateOf("Loading published fixtures…") }
    var fixtureRows by remember { mutableStateOf(emptyList<JSONObject>()) }
    var leagueRows by remember { mutableStateOf(emptyList<JSONObject>()) }
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
                    .filter { marketSelectionId(it) in MarketIds && it.optString("odds_status") == "available" && it.optDouble("odds",0.0) > 1.0 }
                val fixtureFeed = apiRequest(apiUrl, "docs/data/fixtures.json?ts=$stamp")
                val fixtureArray = fixtureFeed.optJSONArray("fixtures")
                fixtureRows = if (fixtureArray == null) emptyList() else (0 until fixtureArray.length()).mapNotNull { fixtureArray.optJSONObject(it) }
                try {
                    val leagueFeed = apiRequest(apiUrl, "docs/data/league-tables.json?ts=$stamp")
                    val leagueArray = leagueFeed.optJSONArray("leagues")
                    leagueRows = if (leagueArray == null) emptyList() else (0 until leagueArray.length()).mapNotNull { leagueArray.optJSONObject(it) }
                } catch (_: Exception) { leagueRows = emptyList() }
                fixtureSummary = "${fixtureRows.size} fixtures · updated " + fixtureFeed.optString("updated_at", "not run yet")
                val oddsFeed = apiRequest(apiUrl, "docs/data/odds-movements.json?ts=$stamp")
                val movementArray = oddsFeed.optJSONArray("movements")
                oddsMovementRows = if (movementArray == null) emptyList() else (0 until movementArray.length()).mapNotNull { movementArray.optJSONObject(it) }.filter { isApprovedSportyBetMovement(it) }
                oddsMovementUpdated = "Updated " + oddsFeed.optString("updated_at", "not available")
                val resultFeed = apiRequest(apiUrl, "docs/data/results.json?ts=$stamp")
                resultSummary = resultFeed.optJSONObject("summary") ?: JSONObject()
                val recent = resultFeed.optJSONArray("recent")
                resultRows = if (recent == null) emptyList() else (0 until recent.length()).mapNotNull { recent.optJSONObject(it) }.filter { it.optDouble("odds",0.0) > 1.0 }
            } catch (e: Exception) {
                connection = "Feed unavailable"
                message = e.message ?: "Could not load published Goal Scanner data."
            }
        }
    }

    Scaffold(
        containerColor=Color(0xFF070C14),
        topBar = {
            TopAppBar(
                colors=TopAppBarDefaults.topAppBarColors(
                    containerColor=Color(0xFF080D13),
                    titleContentColor=Color(0xFFF2F5F0),
                    actionIconContentColor=Color(0xFFF2F5F0)
                ),
                title = { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(37.dp).background(Brush.linearGradient(listOf(Color(0xFFB7F36B),Color(0xFF55D6C2))),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { Text("GS",color=Color(0xFF0A1720),fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleSmall) }
                    Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text("GOAL SCANNER",fontWeight=FontWeight.ExtraBold,fontSize=17.sp,letterSpacing=1.05.sp)
                        Text("PRO  /  MATCH INTELLIGENCE",style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3),letterSpacing=.85.sp)
                    }
                } },
                actions = {
                    IconButton(onClick={settings=!settings}) {
                        Icon(Icons.Default.Settings,"Settings",tint=Color(0xFFB7F36B))
                    }
                }
            )
        },
        bottomBar = {
            if (!settings && tab!="Match") NavigationBar(
                containerColor=Color(0xFF0D1723),contentColor=Color(0xFFF4F7FA),tonalElevation=0.dp
            ) {
                listOf(NavItem("Home",Icons.Default.Home),NavItem("Fixtures",Icons.Default.CalendarMonth),
                    NavItem("Picks",Icons.Default.QueryStats),NavItem("Scanner",Icons.Default.SportsSoccer),
                    NavItem("More",Icons.Default.Analytics)).forEach { item ->
                    NavigationBarItem(
                        selected=tab==item.label,onClick={backStack=listOf(item.label)},
                        icon={Icon(item.icon,item.label)},
                        label={Text(item.label,fontSize=10.sp,fontWeight=if(tab==item.label) FontWeight.Bold else FontWeight.Normal)},
                        colors=NavigationBarItemDefaults.colors(
                            selectedIconColor=Color(0xFFB7F36B),selectedTextColor=Color(0xFFB7F36B),
                            indicatorColor=Color(0xFF1B3A38),unselectedIconColor=Color(0xFF7F93A5),
                            unselectedTextColor=Color(0xFF8493A0)
                        )
                    )
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
        } else saveableStateHolder.SaveableStateProvider(tab) { when(tab) {
            "Home" -> HomeScreen(Modifier.padding(padding),connection,providers,fixtureSummary,fixtureRows,
                {navigate("Scanner")},{navigate("Fixtures")},{navigate("Picks")},{ fx -> selectedFixture=fx; navigate("Match") })
            "Fixtures" -> FixturesScreen(Modifier.padding(padding),fixtureSummary,
                fixtureRows,busy,message,onSelect={ fixture -> selectedFixture=fixture; navigate("Match") }) {
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
                },message,apiUrl,season,division,recommendations,fixtureRows,scanSummary,busy,
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
                                dateOk && competitionOk && teamOk && oddsOk && odds!=null && odds>1.0 && item.optString("odds_status")=="available" && item.optDouble("estimated_probability")>=minimum/100.0 && marketSelectionId(item) in markets
                            }.let { if(scannerView=="Recommended") it.take(10) else it }
                            scanSummary="${response.optString("status","unknown")} · ${response.optInt("fixture_count")} fixtures · ${allRows.size} published selections · updated ${response.optString("updated_at","unknown")}"
                            message=if(recommendations.isEmpty()) "No published selections match these filters. The next scheduled scan will refresh the feed." else "Latest published selections loaded. Select a match to inspect its summary."
                        } catch(e:Exception) { scanSummary="Scan failed"; message=e.message ?: "Scanner request failed." }
                        finally { busy=false }
                    }
                },
                onSelect={candidate -> selectedRecommendation=candidate; navigate("Analysis")})
            "Match" -> FixtureDetailsScreen(Modifier.padding(padding),selectedFixture,recommendations,
                onBack={goBack()},onSelect={ candidate -> selectedRecommendation=candidate; navigate("Analysis") })
            "Analysis" -> AnalysisScreen(Modifier.padding(padding),selectedRecommendation,
                recommendations.filter { it.optString("fixture_id")==selectedRecommendation?.optString("fixture_id") },
                fixtureRows.firstOrNull { it.optString("id")==selectedRecommendation?.optString("fixture_id") })
            "Picks" -> FinalPicksScreen(Modifier.padding(padding),recommendations,fixtureRows,
                onSelect={ candidate -> selectedRecommendation=candidate; navigate("Analysis") })
            "League Tables" -> LeagueTablesScreen(Modifier.padding(padding),leagueRows)
            "More" -> MoreScreen(Modifier.padding(padding),::navigate)
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
            "Results" -> ResultsScreen(Modifier.padding(padding),resultSummary,resultRows,busy) {
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
        } }
    }
}

@Composable
private fun MoreScreen(modifier: Modifier, navigate: (String) -> Unit) {
    ScreenColumn(modifier) {
        Heading("More tools", "Competition intelligence, performance history and market movement.")
        listOf(Triple("League tables", "Standings with home and away records", "League Tables"), Triple("Results & performance", "Settled picks and historical performance", "Results"), Triple("Odds movement", "Observed SportyBet price changes", "Odds")).forEach { item ->
            Card(Modifier.fillMaxWidth().clickable { navigate(item.third) }, colors=CardDefaults.cardColors(containerColor=Color(0xFF14212B)), border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF30414E))) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(item.first, fontWeight=FontWeight.Bold, color=Color.White, style=MaterialTheme.typography.titleMedium)
                        Text(item.second, color=Color(0xFF91A2B3), style=MaterialTheme.typography.bodySmall)
                    }
                    Text("OPEN  ›", color=Color(0xFFB7F36B), fontWeight=FontWeight.Bold, style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FinalPicksScreen(modifier: Modifier, recommendations: List<JSONObject>, fixtures: List<JSONObject>, onSelect: (JSONObject) -> Unit) {
    var sort by rememberSaveable { mutableStateOf("Kick-off") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val eligible = recommendations.filter { item ->
        marketSelectionId(item) in MarketIds && item.optString("odds_status") == "available" && item.optDouble("odds",0.0) > 1.0
    }
    // One strongest qualifying market per fixture. The backend rank score is preferred;
    // the same transparent ranking formula is used only when older feeds lack that field.
    val onePerMatch = eligible.groupBy {
        it.optString("fixture_id").ifBlank { "${it.optString("home")}| ${it.optString("away")}| ${it.optString("kickoff")}" }
    }.values.mapNotNull { candidates ->
        candidates.maxByOrNull { item ->
            item.optDouble("rank_score",
                .55 * item.optDouble("estimated_probability") +
                .30 * item.optDouble("data_quality") +
                .15 * (((item.optDouble("expected_value", -0.10) + .10) / .30).coerceIn(0.0,1.0)))
        }
    }
    val todays = onePerMatch.filter { runCatching { LocalDate.parse(it.optString("kickoff").take(10)) == today }.getOrDefault(false) }
    val source = if (showAll) onePerMatch else todays
    val sorted = when(sort) {
        "Probability" -> source.sortedByDescending { it.optDouble("estimated_probability") }
        "Odds" -> source.sortedByDescending { it.optDouble("odds") }
        "League" -> source.sortedBy { it.optString("competition") }
        "Match A-Z" -> source.sortedBy { it.optString("home") + " " + it.optString("away") }
        else -> source.sortedBy { it.optString("kickoff") }
    }
    ScreenColumn(modifier) {
        Heading("Final picks of the day", "One match, one strongest qualifying market. Each fixture appears once, with the model estimate and bookmaker price kept separate.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!showAll,onClick={showAll=false},label={Text("Today (${todays.size})")})
            FilterChip(selected=showAll,onClick={showAll=true},label={Text("All upcoming (${onePerMatch.size})")})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Kick-off","Probability","Odds","League","Match A-Z").forEach { value ->
                FilterChip(selected=sort==value,onClick={sort=value},label={Text(value)})
            }
        }
        Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF36516A))) {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF183A43),Color(0xFF17283A))),RoundedCornerShape(24.dp)).padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("ONE PICK PER MATCH",color=Color(0xFF9CB3C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                    Text(sorted.size.toString(),color=Color(0xFFB7F36B),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.ExtraBold)
                    Text(if(showAll) "Upcoming matches" else "Today's shortlist",color=Color(0xFFB8C9D5),style=MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(today.toString(),color=Color.White,fontWeight=FontWeight.Bold)
                    Surface(shape=RoundedCornerShape(50),color=Color(0xFF1B443A)) {
                        Text("PRICE CHECKED",Modifier.padding(horizontal=10.dp,vertical=7.dp),color=Color(0xFF83E0B7),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if(sorted.isEmpty()) Notice("No priced picks for this view", "Only qualifying markets with an actual SportyBet price above 1.00 are shown. Try All upcoming or check after the next data refresh.")
        sorted.forEach { item ->
            val fixture=fixtures.firstOrNull { it.optString("id")==item.optString("fixture_id") }
            Card(Modifier.fillMaxWidth().clickable { onSelect(item) },shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2D4356))) {
                Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text(item.optString("competition").uppercase(),color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.7.sp)
                        Surface(shape=RoundedCornerShape(50),color=Color(0xFF193A37)) {
                            Text(formatKickoff(item.optString("kickoff")),Modifier.padding(horizontal=9.dp,vertical=5.dp),color=Color(0xFF76DCCB),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TeamCrest(item.optString("home"),fixture?.optString("home_crest_url").orEmpty(),38.dp)
                        Text(item.optString("home"),Modifier.weight(1f).padding(horizontal=7.dp),color=Color.White,fontWeight=FontWeight.Bold,maxLines=2)
                        Text("VS",color=Color(0xFF8193A0),style=MaterialTheme.typography.labelSmall)
                        Text(item.optString("away"),Modifier.weight(1f).padding(horizontal=7.dp),color=Color.White,fontWeight=FontWeight.Bold,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                        TeamCrest(item.optString("away"),fixture?.optString("away_crest_url").orEmpty(),38.dp)
                    }
                    Surface(shape=RoundedCornerShape(12.dp),color=Color(0xFF1A2B3B)) {
                        Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text("THE SELECTED MARKET",color=Color(0xFF7F95A7),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                            Text(marketTitle(item),color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleSmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text("MODEL ESTIMATE",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                            Text("${(item.optDouble("estimated_probability")*100).toInt()}%",color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineMedium)
                        }
                        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text("SPORTYBET PRICE",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                            Text(String.format("%.2f",item.optDouble("odds")),color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineMedium)
                        }
                    }
                    LinearProgressIndicator(progress={item.optDouble("estimated_probability").toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(5.dp),color=Color(0xFFB7F36B),trackColor=Color(0xFF344452))
                    Text("OPEN FULL ANALYSIS  ›",color=Color(0xFFB7F36B),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
@Composable
private fun LeagueTablesScreen(modifier: Modifier, leagues: List<JSONObject>) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Position") }
    var selected by rememberSaveable { mutableStateOf("") }
    val filtered = leagues.filter { it.optString("competition").contains(query,true) || query.isBlank() }.sortedBy { it.optString("competition") }
    val active = filtered.firstOrNull { it.optString("competition")==selected } ?: filtered.firstOrNull()
    ScreenColumn(modifier) {
        Heading("League tables", "Current-season standings built from completed league results. Select a competition to inspect its table.")
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search leagues")})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { FilterChip(selected=sort=="Position",onClick={sort="Position"},label={Text("Table order")}); FilterChip(selected=sort=="Team",onClick={sort="Team"},label={Text("Team A-Z")}) }
        if(filtered.isEmpty()) Notice("No league tables available", "The data feed has no current-season table for this competition yet.")
        filtered.forEach { league -> FilterChip(selected=active?.optString("competition")==league.optString("competition"),onClick={selected=league.optString("competition")},label={Text(league.optString("competition"))}) }
        if(active!=null) {
            val table=active.optJSONArray("table")
            Heading(active.optString("competition"), "${table?.length() ?: 0} teams · ${active.optString("source","Historical results")}")
            if(table==null || table.length()==0) Notice("Standings not yet available", "No completed results have been published for this league.")
            else {
                val rows=(0 until table.length()).mapNotNull { table.optJSONObject(it) }.let { list -> if(sort=="Team") list.sortedBy { it.optString("team") } else list.sortedBy { it.optInt("position") } }
                Row(Modifier.fillMaxWidth().background(Color(0xFF172638),RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("POS",Modifier.width(32.dp),color=Color(0xFF8297A9),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                    Text("CLUB",Modifier.weight(1f),color=Color(0xFF8297A9),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                    Text("P",Modifier.width(28.dp),color=Color(0xFF8297A9),style=MaterialTheme.typography.labelSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Text("GD",Modifier.width(35.dp),color=Color(0xFF8297A9),style=MaterialTheme.typography.labelSmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    Text("PTS",Modifier.width(34.dp),color=Color(0xFFB7F36B),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                }
                rows.forEach { team ->
                    var expanded by remember(team.optString("team")) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth().clickable { expanded=!expanded },colors=CardDefaults.cardColors(containerColor=Color(0xFF121F29)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B3D49))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Surface(shape=RoundedCornerShape(8.dp),color=if(team.optInt("position")<=4) Color(0xFF1C443B) else Color(0xFF1B2A39)) { Text(team.optInt("position").toString(),Modifier.width(28.dp).padding(vertical=5.dp),color=if(team.optInt("position")<=4) Color(0xFF8BE0B5) else Color(0xFF9FB0BF),fontWeight=FontWeight.ExtraBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
                                Text(team.optString("team"),Modifier.weight(1f).padding(start=7.dp),color=Color.White,fontWeight=FontWeight.Bold,maxLines=1)
                                Text(team.optInt("played").toString(),Modifier.width(28.dp),color=Color(0xFF91A2B3),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text(team.optInt("gd").toString(),Modifier.width(35.dp),color=Color(0xFF91A2B3),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                Text(team.optInt("points").toString(),Modifier.width(34.dp),color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Text("FORM",color=Color(0xFF71899B),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold); FormStrip(team.optString("form")); Text("HOME / AWAY  ${if(expanded) "−" else "+"}",color=Color(0xFF55D6C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold) }
                            if(expanded) {
                                listOf("Home" to team.optJSONObject("home"),"Away" to team.optJSONObject("away")).forEach { (label,split) -> if(split!=null) Text("$label: ${split.optInt("played")} played · ${split.optInt("won")}W ${split.optInt("drawn")}D ${split.optInt("lost")}L · ${split.optInt("gf")}:${split.optInt("ga")} · ${split.optInt("points")} pts",color=Color(0xFF91A2B3),style=MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
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
private fun TeamCrest(name:String,url:String,size:Dp=54.dp) {
    val initials=name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "FC" }
    Surface(modifier=Modifier.size(size),shape=RoundedCornerShape(18.dp),
        color=Color(0xFF1B2732),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF344452))) {
        Box(contentAlignment=Alignment.Center) {
            Text(initials,color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)
            if(url.isNotBlank()) AsyncImage(model=url,contentDescription="$name crest",
                modifier=Modifier.fillMaxSize().padding(5.dp),contentScale=ContentScale.Fit)
        }
    }
}
@Composable
private fun FormStrip(form:String) {
    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        if(form.isBlank()) Text("No form data",style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3))
        form.takeLast(5).forEach { result ->
            val tint=when(result) { 'W' -> Color(0xFF3D8B5C); 'D' -> Color(0xFF657482); else -> Color(0xFFB65353) }
            Surface(shape=RoundedCornerShape(7.dp),color=tint) {
                Text(result.toString(),modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp),
                    color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
@Composable
private fun RecentGoalsGraph(recent: org.json.JSONArray?) {
    if(recent==null || recent.length()==0) return
    val values=(0 until minOf(5,recent.length())).mapNotNull { i ->
        val score=(recent.optJSONObject(i)?.optString("score") ?: "").split("-")
        val gf=score.getOrNull(0)?.trim()?.toFloatOrNull(); val ga=score.getOrNull(1)?.trim()?.toFloatOrNull()
        if(gf==null||ga==null) null else gf to ga
    }
    if(values.isEmpty()) return
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text("GOALS FOR / AGAINST · LAST ${values.size}",color=Color(0xFF8296A7),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.7.sp)
        Canvas(Modifier.fillMaxWidth().height(76.dp)) {
            val maxGoals=(values.maxOf { maxOf(it.first,it.second) }).coerceAtLeast(1f)
            val group=size.width/values.size
            val base=size.height-5.dp.toPx()
            val available=size.height-14.dp.toPx()
            values.forEachIndexed { index,pair ->
                val bar=group*.22f
                val gap=group*.08f
                val x=group*index+group*.24f
                val homeH=available*(pair.first/maxGoals)
                val awayH=available*(pair.second/maxGoals)
                drawRect(Color(0xFFB7F36B),Offset(x,base-homeH),androidx.compose.ui.geometry.Size(bar,homeH))
                drawRect(Color(0xFF55D6C2),Offset(x+bar+gap,base-awayH),androidx.compose.ui.geometry.Size(bar,awayH))
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) { Box(Modifier.size(7.dp).background(Color(0xFFB7F36B),RoundedCornerShape(2.dp))); Text("Scored",color=Color(0xFF8296A7),style=MaterialTheme.typography.labelSmall) }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) { Box(Modifier.size(7.dp).background(Color(0xFF55D6C2),RoundedCornerShape(2.dp))); Text("Conceded",color=Color(0xFF8296A7),style=MaterialTheme.typography.labelSmall) }
        }
    }
}
@Composable
private fun TeamFormPanel(modifier:Modifier,name:String,stats:JSONObject?,venueLabel:String) {
    Card(modifier=modifier,shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF142436)),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B3A46))) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(name,fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0),maxLines=2,
                style=MaterialTheme.typography.titleSmall)
            Text(venueLabel.uppercase(),style=MaterialTheme.typography.labelSmall,color=Color(0xFF8FA0AD))
            if(stats==null) Text("Historical stats unavailable",style=MaterialTheme.typography.bodySmall,color=Color(0xFF91A2B3))
            else {
                FormStrip(stats.optString("form"))
                val trend=stats.optJSONObject("recent_trends")
                if(trend!=null) Text("Last five: ${trend.optInt("last5_wins")}W ${trend.optInt("last5_draws")}D ${trend.optInt("last5_losses")}L  ·  Goals ${trend.optInt("last5_goals_for")}-${trend.optInt("last5_goals_against")}",style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B))
                Text("${stats.optInt("wins")}W  ${stats.optInt("draws")}D  ${stats.optInt("losses")}L",
                    style=MaterialTheme.typography.labelSmall,color=Color(0xFFCDD6DC))
                Text("${stats.optInt("played")} matches in sample",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8797A4))
                val shots=stats.optDouble("shots_avg",-1.0); val target=stats.optDouble("shots_on_target_avg",-1.0); val xg=stats.optDouble("xg_avg",-1.0)
                if(shots>=0 || target>=0 || xg>=0) Text(listOfNotNull(if(shots>=0) "Shots %.1f".format(shots) else null,if(target>=0) "On target %.1f".format(target) else null,if(xg>=0) "xG %.2f".format(xg) else null).joinToString("  · "),style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B))
                val recent=stats.optJSONArray("recent_matches")
                RecentGoalsGraph(recent)
                if(recent!=null && recent.length()>0) {
                    HorizontalDivider(color=Color(0xFF30414E))
                    Text("RECENT HOME/AWAY MATCHES",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8FA0AD),fontWeight=FontWeight.Bold)
                    (0 until minOf(5,recent.length())).forEach { i ->
                        val match=recent.optJSONObject(i) ?: JSONObject()
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("${match.optString("date")}  ${match.optString("venue")}",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8495A2)); Text(match.optString("opponent"),style=MaterialTheme.typography.bodySmall,color=Color.White,maxLines=1) }
                            Text(match.optString("score"),color=Color.White,fontWeight=FontWeight.Bold)
                            Text(match.optString("result"),modifier=Modifier.padding(start=8.dp),color=when(match.optString("result")){"W"->Color(0xFF67C58B);"D"->Color(0xFF91A2B3);else->Color(0xFFE18A83)},fontWeight=FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun PreMatchStats(stats:JSONObject?) {
    Heading("Pre-match intelligence","Historical results before kick-off · venue-specific form")
    val home=stats?.optJSONObject("home")
    val away=stats?.optJSONObject("away")
    if(home==null && away==null) {
        Notice("Pre-match data unavailable","No matching historical sample is published for this competition. Missing statistics are left blank rather than estimated.")
        return
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        TeamFormPanel(Modifier.weight(1f),"Home team",home,"At home")
        TeamFormPanel(Modifier.weight(1f),"Away team",away,"Away")
    }
    if(home!=null && away!=null) {
        Heading("Team comparison","Historical averages from the available venue-specific sample")
        val comparisons=listOf(
            Triple("Goals scored / match",home.optDouble("goals_for_avg"),away.optDouble("goals_for_avg")),
            Triple("Goals conceded / match",home.optDouble("goals_against_avg"),away.optDouble("goals_against_avg")),
            Triple("BTTS rate",home.optDouble("btts_pct"),away.optDouble("btts_pct")),
            Triple("Over 2.5 rate",home.optDouble("over_2_5_pct"),away.optDouble("over_2_5_pct")),
            Triple("Clean sheets",home.optDouble("clean_sheet_pct"),away.optDouble("clean_sheet_pct"))
        )
        comparisons.forEach { (label,h,a) ->
            val percent=label.contains("rate") || label=="Clean sheets"
            val total=(h+a).takeIf { it>0 } ?: 1.0
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(if(percent) "${h.toInt()}%" else "%.2f".format(h),fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0))
                    Text(label,style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3))
                    Text(if(percent) "${a.toInt()}%" else "%.2f".format(a),fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0))
                }
                LinearProgressIndicator(progress={(h/total).toFloat().coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth().height(5.dp),color=Color(0xFFB7F36B),trackColor=Color(0xFF344452))
            }
        }
        val hc=home.optDouble("corners_avg",-1.0); val ac=away.optDouble("corners_avg",-1.0)
        val hb=home.optDouble("bookings_avg",-1.0); val ab=away.optDouble("bookings_avg",-1.0)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(hc>=0 && ac>=0) Box(Modifier.weight(1f)) { Metric("Corners / match","%.1f / %.1f".format(hc,ac),"Home / away samples") }
            if(hb>=0 && ab>=0) Box(Modifier.weight(1f)) { Metric("Bookings / match","%.1f / %.1f".format(hb,ab),"Home / away samples") }
        }
    }
    val h2h=stats?.optJSONObject("head_to_head")
    if(h2h!=null) {
        Heading("Head-to-head","Previous meetings before this fixture")
        Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF142436)),
            border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B3A46))) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("${h2h.optInt("played")} meetings  ·  ${h2h.optInt("home_wins")} home wins  ·  ${h2h.optInt("draws")} draws  ·  ${h2h.optInt("away_wins")} away wins",
                    color=Color(0xFFF2F5F0),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodySmall)
                val scores=h2h.optJSONArray("recent_scores")
                val meetings=h2h.optJSONArray("recent_meetings")
                if(meetings!=null && meetings.length()>0) {
                    HorizontalDivider(color=Color(0xFF30414E))
                    Text("PREVIOUS MEETINGS",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                    (0 until minOf(10,meetings.length())).forEach { i ->
                        val match=meetings.optJSONObject(i) ?: JSONObject()
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(match.optString("date"),color=Color(0xFF8495A2),style=MaterialTheme.typography.labelSmall); Text(match.optString("score"),color=Color.White,fontWeight=FontWeight.Bold) }
                    }
                } else if(scores!=null && scores.length()>0) Text("Recent scores: "+(0 until scores.length()).joinToString("  ·  ") { scores.optString(it) }, color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
    val context=stats?.optJSONObject("context")
    if(context!=null) {
        Heading("Beyond the scoreline","Schedule and squad context from separate public data sources")
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF121F29)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B3D49))) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                val homeRest=context.optInt("home_rest_days",-1); val awayRest=context.optInt("away_rest_days",-1)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Box(Modifier.weight(1f)) { Metric("Home rest",if(homeRest>=0) "$homeRest days" else "—","Since last recorded match") }; Box(Modifier.weight(1f)) { Metric("Away rest",if(awayRest>=0) "$awayRest days" else "—","Since last recorded match") } }
                listOf("Home squad" to context.optJSONObject("home_squad"),"Away squad" to context.optJSONObject("away_squad")).forEach { (label,squad) -> if(squad!=null) {
                    val value=squad.optDouble("market_value_eur",-1.0)
                    Text("$label · ${squad.optString("name")}",color=Color.White,fontWeight=FontWeight.Bold)
                    Text(listOfNotNull(if(squad.optDouble("average_age",-1.0)>=0) "Average age %.1f".format(squad.optDouble("average_age")) else null,if(squad.optInt("squad_size")>0) "${squad.optInt("squad_size")} players" else null,if(value>=0) "Market value €%.1fm".format(value/1000000.0) else null).joinToString("  · ").ifBlank { "Squad profile unavailable" },color=Color(0xFF91A2B3),style=MaterialTheme.typography.bodySmall)
                } }
                val baseline=context.optJSONObject("league_performance_baseline")
                if(baseline!=null) { Heading("League environment","Historical baseline · ${baseline.optInt("matches_sampled")} matches"); Text(listOfNotNull(if(baseline.optDouble("home_shots_avg",-1.0)>=0) "Home shots %.1f".format(baseline.optDouble("home_shots_avg")) else null,if(baseline.optDouble("away_shots_avg",-1.0)>=0) "Away shots %.1f".format(baseline.optDouble("away_shots_avg")) else null,if(baseline.optDouble("home_xg_avg",-1.0)>=0) "Home xG %.2f".format(baseline.optDouble("home_xg_avg")) else null,if(baseline.optDouble("away_xg_avg",-1.0)>=0) "Away xG %.2f".format(baseline.optDouble("away_xg_avg")) else null).joinToString("  · "),color=Color(0xFF91A2B3),style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Text("Source: Football-Data.co.uk. Sample counts vary by competition; only completed matches before kick-off are included.",
        style=MaterialTheme.typography.labelSmall,color=Color(0xFF7F909D))
}

@Composable
private fun ScreenColumn(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF070C14),Color(0xFF0A1420),Color(0xFF080F19))))
        .verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=20.dp),
        verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable
private fun Heading(title:String,subtitle:String?=null) {
    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top=3.dp).width(3.dp).height(if(subtitle==null) 23.dp else 38.dp).background(Brush.verticalGradient(listOf(Color(0xFFB7F36B),Color(0xFF55D6C2))),RoundedCornerShape(50)))
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Color(0xFFF4F7FA),letterSpacing=(-0.35).sp)
            if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Color(0xFF91A2B3),lineHeight=18.sp)
        }
    }
}
@Composable
private fun Metric(title:String,value:String,detail:String) {
    Card(shape=RoundedCornerShape(19.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111D2B)),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF26384A))) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(),style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3),letterSpacing=1.sp,fontWeight=FontWeight.Bold)
            Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold,color=Color(0xFFB7F36B))
            Text(detail,style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3),maxLines=2,lineHeight=15.sp)
        }
    }
}
@Composable
private fun MatchdayFixtureCard(fx: JSONObject, onClick: () -> Unit) {
    val status=fx.optString("status","NS").uppercase()
    Card(Modifier.fillMaxWidth().clickable { onClick() },shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C)),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B4053))) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(fx.optString("competition","Football").uppercase(),color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=0.8.sp,maxLines=1)
                    Text(fx.optString("country"),color=Color(0xFF64798B),style=MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(formatKickoff(fx.optString("kickoff")).substringAfter("·").trim(),color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleSmall)
                    StatusPill(status)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TeamCrest(fx.optString("home"),fx.optString("home_crest_url"),44.dp)
                Text(fx.optString("home","Home"),Modifier.weight(1f).padding(horizontal=9.dp),color=Color(0xFFF4F7FA),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=2)
                Text(if(!fx.isNull("home_score")&&!fx.isNull("away_score")) "${fx.optInt("home_score")} : ${fx.optInt("away_score")}" else "VS",color=Color(0xFF55D6C2),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleSmall)
                Text(fx.optString("away","Away"),Modifier.weight(1f).padding(horizontal=9.dp),color=Color(0xFFF4F7FA),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.End)
                TeamCrest(fx.optString("away"),fx.optString("away_crest_url"),44.dp)
            }
            HorizontalDivider(color=Color(0xFF26384A))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(formatKickoff(fx.optString("kickoff")),color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall)
                Text("MATCH CENTRE  →",color=Color(0xFF55D6C2),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelSmall,letterSpacing=0.5.sp)
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier:Modifier,connection:String,providers:String,fixtures:String,
    fixtureRows:List<JSONObject>,onScanner:()->Unit,onFixtures:()->Unit,onPicks:()->Unit,onSelect:(JSONObject)->Unit) {
    var sort by rememberSaveable { mutableStateOf("Kick-off") }
    val upcoming=fixtureRows.filter { it.optString("status","NS").uppercase() in listOf("NS","TBD","SCHEDULED","") }
        .let { rows -> if(sort=="Match A-Z") rows.sortedBy { it.optString("home")+it.optString("away") } else rows.sortedBy { it.optString("kickoff") } }.take(6)
    val competitions=fixtureRows.map { it.optString("competition") }.filter { it.isNotBlank() }.distinct().size
    val withHistory=fixtureRows.count { fx -> val stats=fx.optJSONObject("pre_match_stats"); stats?.optJSONObject("home")!=null || stats?.optJSONObject("away")!=null }
    val withoutHistory=(fixtureRows.size-withHistory).coerceAtLeast(0)
    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("GOAL SCANNER PRO",color=Color(0xFF55D6C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=1.5.sp)
                Text("Matchday briefing",color=Color(0xFFF4F7FA),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
            }
            Surface(shape=RoundedCornerShape(50),color=if(connection.startsWith("Connected")) Color(0xFF193A32) else Color(0xFF3B3022)) {
                Text(if(connection.startsWith("Connected")) "● LIVE FEED" else "● OFFLINE",Modifier.padding(horizontal=11.dp,vertical=8.dp),color=if(connection.startsWith("Connected")) Color(0xFF73E1B5) else Color(0xFFE9B86D),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
            }
        }
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF36516A))) {
            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF173A43),Color(0xFF17283A),Color(0xFF172235))),RoundedCornerShape(28.dp)).padding(20.dp)) {
                Canvas(Modifier.size(150.dp).align(Alignment.CenterEnd)) {
                    val c=Color.White.copy(alpha=.08f)
                    drawCircle(c,radius=size.minDimension*.47f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                    drawCircle(c,radius=size.minDimension*.25f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                    drawLine(c,Offset(size.width*.5f,0f),Offset(size.width*.5f,size.height),2.dp.toPx())
                    drawLine(c,Offset(0f,size.height*.5f),Offset(size.width,size.height*.5f),2.dp.toPx())
                }
                Column(Modifier.fillMaxWidth(.88f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("THE GAME, READ PROPERLY",color=Color(0xFFB7F36B),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=1.2.sp)
                    Text("See beyond\nthe scoreline.",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold,lineHeight=35.sp)
                    Text("Form, head-to-head, squad context, league position and verified market prices in one match centre.",color=Color(0xFFC0D0DC),style=MaterialTheme.typography.bodySmall,lineHeight=18.sp)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=onPicks,shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB7F36B),contentColor=Color(0xFF111A17))) { Text("TODAY’S PICKS",fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelMedium) }
                        OutlinedButton(onClick=onScanner,shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Color.White)) { Text("SCAN MATCHES",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            Box(Modifier.weight(1f)) { Metric("Fixtures",fixtures.substringBefore(" ").takeIf { it.toIntOrNull()!=null } ?: fixtureRows.size.toString(),"In the current feed") }
            Box(Modifier.weight(1f)) { Metric("Leagues",competitions.toString(),"Competitions covered") }
            Box(Modifier.weight(1f)) { Metric("Feed",if(connection.startsWith("Connected")) "Ready" else "Wait","${providers.take(22)}") }
        }
        Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111D2B)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF26384A))) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("FIXTURE DATA COVERAGE",color=Color(0xFF55D6C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=.8.sp)
                    Text("${fixtureRows.size} discovered",color=Color(0xFFB7F36B),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { Metric("History available",withHistory.toString(),"Has team history") }
                    Box(Modifier.weight(1f)) { Metric("Needs more data",withoutHistory.toString(),"Still visible in fixtures") }
                }
                Text("Fixture discovery is separate from prediction eligibility. Matches with limited history remain visible; unsupported markets are not guessed.",color=Color(0xFF91A2B3),style=MaterialTheme.typography.bodySmall,lineHeight=17.sp)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Heading("Next on the pitch","Select a fixture to open its match centre")
            TextButton(onClick=onFixtures) { Text("ALL MATCHES  →",color=Color(0xFF55D6C2),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { listOf("Kick-off","Match A-Z").forEach { value -> FilterChip(selected=sort==value,onClick={sort=value},label={Text(value)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF1C3B3B),selectedLabelColor=Color(0xFF73E1D1))) } }
        if(upcoming.isEmpty()) Notice("No upcoming fixtures published","The next scheduled data refresh will update this list.")
        upcoming.forEach { fx -> MatchdayFixtureCard(fx) { onSelect(fx) } }
        Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111D2B)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF26384A))) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                Text("INTELLIGENCE LAYERS",color=Color(0xFF55D6C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp)
                Text("A fuller picture of every match",color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) { listOf("HOME / AWAY FORM","H2H HISTORY","SQUAD CONTEXT").forEach { label -> Surface(shape=RoundedCornerShape(8.dp),color=Color(0xFF1B2C3B)) { Text(label,Modifier.padding(horizontal=9.dp,vertical=7.dp),color=Color(0xFFB6C6D2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold) } } }
                Text("Every recommendation remains separate from the bookmaker price. Only the approved markets with an actual SportyBet quote are eligible.",color=Color(0xFF91A2B3),style=MaterialTheme.typography.bodySmall,lineHeight=18.sp)
            }
        }
        Text("DATA STATUS  ·  $providers",color=Color(0xFF657B8D),style=MaterialTheme.typography.labelSmall)
    }
}
@Composable
private fun FixturesScreen(
    modifier:Modifier,summary:String,fixtures:List<JSONObject>,busy:Boolean,message:String,
    onSelect:(JSONObject)->Unit,onCollect:()->Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Upcoming") }
    var sort by rememberSaveable { mutableStateOf("Time") }
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
    }.let { rows -> when(sort) { "Match A-Z" -> rows.sortedBy { it.optString("home") + " " + it.optString("away") }; "League A-Z" -> rows.sortedBy { it.optString("competition") }; else -> rows.sortedBy { it.optString("kickoff") } } }
    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fixtures",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
                Text("Browse matches and open each one for its details.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick=onCollect,enabled=!busy) { Text(if(busy) "Loading…" else "Refresh") }
        }
        Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF24313D))) {
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${fixtures.size} matches available",fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0))
                Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(query,{query=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("Search team or competition")})
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("All","Upcoming","Live","Finished").forEach { item ->
                FilterChip(selected=filter==item,onClick={filter=item},label={Text(item)})
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Time","Match A-Z","League A-Z").forEach { item -> FilterChip(selected=sort==item,onClick={sort=item},label={Text(item)}) } }
        if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        if(visible.isEmpty()) Notice("No matching fixtures","Try another search or status filter, or refresh the fixture feed.")
        var currentDay = ""
        visible.forEach { fx ->
            val day=fx.optString("kickoff").take(10)
            if(day!=currentDay) {
                currentDay=day
                Text(day,modifier=Modifier.padding(top=5.dp),color=Color(0xFF55D6C2),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelLarge,letterSpacing=0.8.sp)
            }
            MatchdayFixtureCard(fx) { onSelect(fx) }
        }
    }
}
@Composable
private fun StatusPill(status:String) {
    val live=status in listOf("1H","2H","HT","LIVE","ET","P")
    val finished=status in listOf("FT","AET","PEN","FINISHED")
    val tint=when { live -> Color(0xFFB7F36B); finished -> Color(0xFF91A2B3); else -> Color(0xFF9AB7D3) }
    Surface(color=when { live -> Color(0xFF263B20); finished -> Color(0xFF26313A); else -> Color(0xFF1B2B37) },
        shape=RoundedCornerShape(50)) {
        Text(status.ifBlank { "NS" },modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp),
            style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,color=tint)
    }
}
private fun formatKickoff(value:String):String = runCatching {
    java.time.OffsetDateTime.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))
}.getOrElse { value.replace("T"," ").take(16) }
private fun isApprovedSportyBetMovement(item:JSONObject):Boolean {
    val market=item.optString("market")
    val selection=item.optString("selection").lowercase()
    return when(market) {
        "1X2" -> selection in setOf("home","draw","away")
        "btts" -> selection=="yes"
        "match_total_goals" -> selection=="2.5/over"
        "match_corners","home_corners","away_corners","match_cards","home_cards","away_cards" -> selection.endsWith("/over")
        else -> false
    }
}

private fun marketSelectionId(item:JSONObject):String = if(item.optString("market")=="match_winner") "match_winner_${item.optString("selection")}" else item.optString("market")
private fun marketTitle(item:JSONObject):String {
    val market=item.optString("market"); val selection=item.optString("selection")
    val line=if(item.isNull("line")) "" else item.optString("line")
    return when(market) {
        "team_corners_over" -> "${selection.replaceFirstChar { it.uppercase() }} team corners · Over ${line.ifBlank { "line unavailable" }}"
        "match_corners_over" -> "Total match corners · Over ${line.ifBlank { "line unavailable" }}"
        "match_bookings_over" -> "Total match bookings (yellow + red) · Over ${line.ifBlank { "line unavailable" }}"
        "team_bookings_over" -> "${selection.replaceFirstChar { it.uppercase() }} team bookings (yellow + red) · Over ${line.ifBlank { "line unavailable" }}"
        "match_over_2_5" -> "Total goals · Over 2.5"
        "btts_yes" -> "Both teams to score · Yes"
        "match_winner" -> "1X2 · ${selection.replaceFirstChar { it.uppercase() }}"
        else -> market.replace("_"," ").replaceFirstChar { it.uppercase() }
    }
}
@Composable
private fun PremiumMatchHeader(fixture: JSONObject) {
    val home=fixture.optString("home"); val away=fixture.optString("away")
    Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=Color.Transparent),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF36516A))) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF193D48),Color(0xFF172A3D),Color(0xFF142033))),RoundedCornerShape(28.dp)).padding(18.dp)) {
            Canvas(Modifier.size(175.dp).align(Alignment.Center)) {
                val c=Color.White.copy(alpha=.055f)
                drawCircle(c,radius=size.minDimension*.46f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                drawCircle(c,radius=size.minDimension*.24f,style=androidx.compose.ui.graphics.drawscope.Stroke(width=2.dp.toPx()))
                drawLine(c,Offset(size.width*.5f,0f),Offset(size.width*.5f,size.height),2.dp.toPx())
            }
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(17.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(fixture.optString("competition","Football").uppercase(),color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp)
                        Text(fixture.optString("country"),color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall)
                    }
                    StatusPill(fixture.optString("status","NS").uppercase())
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        TeamCrest(home,fixture.optString("home_crest_url"),76.dp)
                        Text(home,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
                        Text("HOME",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(if(!fixture.isNull("home_score")&&!fixture.isNull("away_score")) "${fixture.optInt("home_score")} : ${fixture.optInt("away_score")}" else "VS",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineMedium)
                        Text(if(fixture.optString("status").uppercase() in listOf("FT","AET","PEN","FINISHED")) "FULL TIME" else "KICK-OFF",color=Color(0xFF55D6C2),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                    }
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        TeamCrest(away,fixture.optString("away_crest_url"),76.dp)
                        Text(away,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
                        Text("AWAY",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    }
                }
                HorizontalDivider(color=Color.White.copy(alpha=.12f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text(formatKickoff(fixture.optString("kickoff")),color=Color(0xFFD1DEE9),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold)
                    Text("MATCH CENTRE",color=Color(0xFFB7F36B),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp)
                }
            }
        }
    }
}
@Composable
private fun FixtureDetailsScreen(
    modifier:Modifier,fixture:JSONObject?,recommendations:List<JSONObject>,onBack:()->Unit,onSelect:(JSONObject)->Unit
) {
    ScreenColumn(modifier) {
        TextButton(onClick=onBack) { Text("‹  ALL FIXTURES",fontWeight=FontWeight.Bold) }
        if(fixture==null) Notice("No fixture selected","Return to the fixtures list and select a match.")
        else {
            val picks=recommendations.filter { it.optString("fixture_id")==fixture.optString("id") }
            val bestPick=picks.maxByOrNull { item ->
                item.optDouble("rank_score",
                    .55 * item.optDouble("estimated_probability") +
                    .30 * item.optDouble("data_quality") +
                    .15 * (((item.optDouble("expected_value",-0.10)+.10)/.30).coerceIn(0.0,1.0)))
            }
            PremiumMatchHeader(fixture)
            val kickoff=fixture.optString("kickoff")
            val venue=fixture.optString("venue")
            val referee=fixture.optString("referee")
            val details=listOfNotNull(
                venue.takeIf { it.isNotBlank() && it!="null" }?.let { "VENUE  ·  $it" },
                referee.takeIf { it.isNotBlank() && it!="null" }?.let { "REFEREE  ·  $it" },
                fixture.optString("round").takeIf { it.isNotBlank() && it!="null" }?.let { "ROUND  ·  $it" }
            )
            if(details.isNotEmpty()) {
                Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111D2B)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF26384A))) {
                    Column(Modifier.fillMaxWidth().padding(13.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        details.forEach { Text(it,color=Color(0xFFB9C8D3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold) }
                    }
                }
            }
            if(bestPick!=null) {
                Heading("Top market for this match","One strongest qualifying selection · ranked using probability, data quality and available price value")
                Card(Modifier.fillMaxWidth().clickable { onSelect(bestPick) },shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF142B35)),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF47746D))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("SINGLE MATCH RECOMMENDATION",color=Color(0xFF73D9C7),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.ExtraBold,letterSpacing=.8.sp)
                        Text(marketTitle(bestPick),color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column { Text("MODEL PROBABILITY",color=Color(0xFF9BB0BD),style=MaterialTheme.typography.labelSmall); Text("${(bestPick.optDouble("estimated_probability")*100).toInt()}%",color=Color(0xFFB7F36B),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold) }
                            Column(horizontalAlignment=Alignment.End) { Text("SPORTYBET ODDS",color=Color(0xFF9BB0BD),style=MaterialTheme.typography.labelSmall); Text(String.format("%.2f",bestPick.optDouble("odds")),color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold) }
                        }
                        Text("Expected value: ${if(bestPick.isNull("expected_value")) "Unavailable" else String.format("%+.1f%%",bestPick.optDouble("expected_value")*100)}  ·  Data quality: ${(bestPick.optDouble("data_quality")*100).toInt()}%",color=Color(0xFFB9C8D3),style=MaterialTheme.typography.bodySmall)
                        Text("VIEW MARKET DETAILS  ›",color=Color(0xFFB7F36B),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall)
                    }
                }
                val sample=bestPick.optJSONObject("sample")
                if(sample!=null) {
                    Heading("Model evidence","Historical sample used before kick-off")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { Metric("Home at home",sample.optInt("home_home_matches").toString(),"Prior matches") }
                        Box(Modifier.weight(1f)) { Metric("Away away",sample.optInt("away_away_matches").toString(),"Prior matches") }
                    }
                    Text("Expected goals: home ${String.format("%.2f",bestPick.optDouble("home_lambda"))}  ·  away ${String.format("%.2f",bestPick.optDouble("away_lambda"))}",color=Color(0xFFB7C6D0),style=MaterialTheme.typography.bodySmall)
                    Text("History cutoff: ${bestPick.optString("history_cutoff","Not supplied")}  ·  Model: ${bestPick.optString("model_version","Not supplied")}",color=Color(0xFF8296A5),style=MaterialTheme.typography.labelSmall)
                }
            } else {
                Heading("Prediction status","No single market passed the current evidence and price requirements")
                Notice("No qualifying selection published","This fixture remains available for analysis. The scanner will not invent a probability or force a market when the historical sample, model threshold or exact SportyBet price is missing.")
            }
            PreMatchStats(fixture.optJSONObject("pre_match_stats"))
            Notice("How to read this match","Historical form, head-to-head and team statistics describe previous matches only. The model estimate is an uncalibrated baseline, not a guarantee. Missing fields are shown as unavailable rather than guessed.")
        }
    }
}
@Composable
private fun ScannerScreen(
    modifier:Modifier,view:String,onView:(String)->Unit,minimum:Float,onMinimum:(Float)->Unit,
    selected:Set<String>,onSelected:(Set<String>)->Unit,onSave:()->Unit,message:String,
    apiUrl:String,season:String,division:String,recommendations:List<JSONObject>,fixtures:List<JSONObject>,scanSummary:String,
    busy:Boolean,onRun:(String,String,String,Double?,Double?)->Unit,onSelect:(JSONObject)->Unit
) {
    var date by rememberSaveable { mutableStateOf("Today") }
    var competition by rememberSaveable { mutableStateOf("") }
    var team by rememberSaveable { mutableStateOf("") }
    var minOdds by rememberSaveable { mutableStateOf("") }
    var maxOdds by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Kick-off") }
    val sortedRecommendations = when(sort) { "Probability" -> recommendations.sortedByDescending { it.optDouble("estimated_probability") }; "Odds" -> recommendations.sortedByDescending { it.optDouble("odds") }; "Match A-Z" -> recommendations.sortedBy { it.optString("home") + " " + it.optString("away") }; "League" -> recommendations.sortedBy { it.optString("competition") }; else -> recommendations.sortedBy { it.optString("kickoff") } }
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
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Kick-off","Probability","Odds","League","Match A-Z").forEach { value -> FilterChip(selected=sort==value,onClick={sort=value},label={Text(value)}) } }
            sortedRecommendations.forEach { item ->
                val estimate=(item.optDouble("estimated_probability")*100).toInt()
                Card(Modifier.fillMaxWidth().clickable { onSelect(item) },shape=RoundedCornerShape(20.dp),
                    colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C)),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2D4356))) {
                    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Text(item.optString("competition","FOOTBALL").uppercase(),Modifier.weight(1f),
                                style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3),maxLines=1)
                            Surface(color=Color(0xFF263B20),shape=RoundedCornerShape(50)) {
                                Text("MODEL PICK",Modifier.padding(horizontal=9.dp,vertical=5.dp),
                                    style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B),fontWeight=FontWeight.Bold)
                            }
                        }
                        val fixture=fixtures.firstOrNull { it.optString("id")==item.optString("fixture_id") }
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            TeamCrest(item.optString("home"),fixture?.optString("home_crest_url").orEmpty(),42.dp)
                            Text(item.optString("home"),Modifier.weight(1f).padding(horizontal=8.dp),
                                style=MaterialTheme.typography.titleSmall,color=Color(0xFFF2F5F0),fontWeight=FontWeight.Bold,maxLines=2)
                            Text("VS",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8294A2))
                            Text(item.optString("away"),Modifier.weight(1f).padding(horizontal=8.dp),
                                style=MaterialTheme.typography.titleSmall,color=Color(0xFFF2F5F0),fontWeight=FontWeight.Bold,
                                textAlign=androidx.compose.ui.text.style.TextAlign.End,maxLines=2)
                            TeamCrest(item.optString("away"),fixture?.optString("away_crest_url").orEmpty(),42.dp)
                        }
                        Text(formatKickoff(item.optString("kickoff")),style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3))
                        Surface(color=Color(0xFF1B2931),shape=RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("SELECTED MARKET",style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A0AC),letterSpacing=0.8.sp)
                                Text(marketTitle(item),style=MaterialTheme.typography.bodyMedium,color=Color(0xFFF2F5F0),fontWeight=FontWeight.Bold)
                            }
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text("MODEL PROBABILITY",style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A0AC))
                                Text("$estimate%",style=MaterialTheme.typography.headlineSmall,color=Color(0xFFB7F36B),fontWeight=FontWeight.ExtraBold)
                            }
                            Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text("BOOKMAKER ODDS",style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A0AC))
                                Text(if(item.isNull("odds")) "Unavailable" else String.format("%.2f",item.optDouble("odds")),
                                    style=MaterialTheme.typography.titleLarge,color=Color(0xFFF2F5F0),fontWeight=FontWeight.Bold)
                            }
                        }
                        LinearProgressIndicator(progress={estimate/100f},modifier=Modifier.fillMaxWidth().height(5.dp),
                            color=Color(0xFFB7F36B),trackColor=Color(0xFF34434B))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Data quality ${(item.optDouble("data_quality")*100).toInt()}%",
                                style=MaterialTheme.typography.labelSmall,color=Color(0xFF91A2B3))
                            Text("VIEW ANALYSIS  ›",style=MaterialTheme.typography.labelSmall,
                                color=Color(0xFFB7F36B),fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
        } else Notice("No recommendations displayed","No published selections match this view. The scheduled GitHub scan will refresh the feed automatically.")
    }
}
@Composable
private fun ProbabilityGauge(value: Int) {
    Box(Modifier.size(112.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke=11.dp.toPx()
            drawArc(Color(0xFF2A3D50),startAngle=135f,sweepAngle=270f,useCenter=false,style=androidx.compose.ui.graphics.drawscope.Stroke(width=stroke,cap=StrokeCap.Round))
            drawArc(Color(0xFFB7F36B),startAngle=135f,sweepAngle=270f*(value/100f).coerceIn(0f,1f),useCenter=false,style=androidx.compose.ui.graphics.drawscope.Stroke(width=stroke,cap=StrokeCap.Round))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(0.dp)) {
            Text("$value%",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.headlineMedium)
            Text("MODEL",color=Color(0xFF91A2B3),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelSmall,letterSpacing=.8.sp)
        }
    }
}
@Composable
private fun AnalysisScreen(modifier:Modifier,candidate:JSONObject?,related:List<JSONObject>,fixture:JSONObject?) {
    ScreenColumn(modifier) {
        Heading("Match analysis","The exact market threshold, model estimate and supporting historical data.")
        if(candidate==null) Notice("No match selected","Open a match from Fixtures or Scanner to see its analysis.")
        else {
            val home=candidate.optString("home"); val away=candidate.optString("away")
            val probability=(candidate.optDouble("estimated_probability")*100).toInt()
            Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF152B3A)),shape=RoundedCornerShape(25.dp),
                border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF36516A))) {
                Column(Modifier.fillMaxWidth().padding(19.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
                    Text(candidate.optString("competition","Competition unavailable").uppercase(),
                        color=Color(0xFFB7F36B),style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            TeamCrest(home,fixture?.optString("home_crest_url").orEmpty(),62.dp)
                            Text(home,style=MaterialTheme.typography.titleSmall,color=Color.White,fontWeight=FontWeight.Bold,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                        Text("VS",style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B))
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            TeamCrest(away,fixture?.optString("away_crest_url").orEmpty(),62.dp)
                            Text(away,style=MaterialTheme.typography.titleSmall,color=Color.White,fontWeight=FontWeight.Bold,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Text(formatKickoff(candidate.optString("kickoff")),color=Color(0xFFB9C5CC),style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color=Color(0xFF40515C))
                    Text("SELECTED MARKET",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp)
                    Text(marketTitle(candidate),color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(17.dp)) {
                        ProbabilityGauge(probability)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text("MODEL PROBABILITY",color=Color(0xFF91A2B3),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold,letterSpacing=.8.sp)
                            Text(if(candidate.isNull("odds")) "Price unavailable" else "SportyBet  ${String.format("%.2f",candidate.optDouble("odds"))}",color=Color.White,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleLarge)
                            Text("The model estimate and bookmaker quote are separate measures.",color=Color(0xFF9FB0BE),style=MaterialTheme.typography.bodySmall,lineHeight=17.sp)
                        }
                    }
                }
            }
            if(fixture!=null) PreMatchStats(fixture.optJSONObject("pre_match_stats"))
            val sample=candidate.optJSONObject("sample") ?: JSONObject()
            Heading("Model sample","Previous matches available to the probability model")
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) { Metric("Home at home",sample.optInt("home_home_matches").toString(),"Previous matches") }
                Box(Modifier.weight(1f)) { Metric("Away away",sample.optInt("away_away_matches").toString(),"Previous matches") }
            }
            val counts=candidate.optJSONObject("count_expectations") ?: JSONObject()
            val corners=counts.optJSONObject("corners")
            val cards=counts.optJSONObject("bookings")
            if(corners!=null || cards!=null) {
                Heading("Expected match counts","Historical averages adjusted for venue and league")
                if(corners!=null) Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) { Metric("Home corners","%.1f".format(corners.optDouble("home_lambda")),"Expected count") }
                    Box(Modifier.weight(1f)) { Metric("Away corners","%.1f".format(corners.optDouble("away_lambda")),"Expected count") }
                }
                if(cards!=null) Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.weight(1f)) { Metric("Home bookings","%.1f".format(cards.optDouble("home_lambda")),"Expected count") }
                    Box(Modifier.weight(1f)) { Metric("Away bookings","%.1f".format(cards.optDouble("away_lambda")),"Expected count") }
                }
            }
            val marketRows=related.distinctBy { it.optString("market")+"|"+it.optString("selection")+"|"+it.optString("line") }
            Heading("Other model markets","Each selection displays its real line")
            if(marketRows.isEmpty()) Notice("No additional market estimates","Only the selected recommendation is available in this published snapshot.")
            marketRows.forEach { item ->
                val isSelected=item.optString("prediction_id")==candidate.optString("prediction_id")
                Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(
                    containerColor=if(isSelected) Color(0xFF20331E) else Color(0xFF111E2C)),
                    border=androidx.compose.foundation.BorderStroke(1.dp,if(isSelected) Color(0xFF668A42) else Color(0xFF2D4356))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(marketTitle(item),fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0))
                            Text("Probability ${(item.optDouble("estimated_probability")*100).toInt()}%  ·  ${if(item.isNull("odds")) "Odds unavailable" else "Odds "+String.format("%.2f",item.optDouble("odds"))}",
                                style=MaterialTheme.typography.bodySmall,color=Color(0xFF91A2B3))
                            if(!item.isNull("expected_value")) Text("Model price value: ${String.format("%+.1f%%",item.optDouble("expected_value")*100)}",
                                style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B))
                        }
                        if(isSelected) Text("SELECTED",style=MaterialTheme.typography.labelSmall,color=Color(0xFFB7F36B),fontWeight=FontWeight.Bold)
                    }
                }
            }
            Heading("Model notes")
            Text("Data quality ${(candidate.optDouble("data_quality")*100).toInt()}% · ${candidate.optString("model_version")}",
                style=MaterialTheme.typography.bodySmall,color=Color(0xFF91A2B3))
            Notice("Important limitation","These are uncalibrated historical-rate estimates, not proven betting edges. Corner and booking markets appear only when sufficient historical statistics exist. Bookmaker prices are displayed separately and do not determine model probabilities.")
        }
    }
}
@Composable
private fun ResultsScreen(
    modifier:Modifier,summary:JSONObject,rows:List<JSONObject>,busy:Boolean,onRefresh:()->Unit
) {
    val settled=summary.optInt("settled",0)
    val accuracy=if(summary.isNull("accuracy")) "—" else "${(summary.optDouble("accuracy")*100).toInt()}%"
    var sort by rememberSaveable { mutableStateOf("Newest") }
    val sortedRows=when(sort) { "Match A-Z" -> rows.sortedBy { it.optString("home") + " " + it.optString("away") }; "League" -> rows.sortedBy { it.optString("competition") }; "Odds" -> rows.sortedByDescending { it.optDouble("odds") }; else -> rows.sortedByDescending { it.optString("kickoff") } }
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
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Newest","Match A-Z","League","Odds").forEach { value -> FilterChip(selected=sort==value,onClick={sort=value},label={Text(value)}) } }
            sortedRows.forEach { item ->
                Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C)),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2D4356))) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${item.optString("home")}  vs  ${item.optString("away")}",fontWeight=FontWeight.ExtraBold,color=Color(0xFFF2F5F0))
                        val detail=item.optJSONObject("payload") ?: JSONObject().apply {
                            put("market",item.optString("market"))
                            put("selection",item.optString("selection"))
                        }
                        Text(marketTitle(detail),fontWeight=FontWeight.SemiBold)
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
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF111E2C))) {
            Text("Premium dark interface · electric green accent",Modifier.padding(14.dp))
        }
        Heading("Scanner defaults")
        Card { Text("Balanced shortlist · 60% initial probability threshold · Exclude insufficient data",Modifier.padding(14.dp)) }
        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("Goal Scanner Pro · Version 1.4.0",fontWeight=FontWeight.Bold)
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
