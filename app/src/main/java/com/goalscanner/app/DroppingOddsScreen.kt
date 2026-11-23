package com.goalscanner.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val OddsBg = Color(0xFF080D13)
private val OddsPanel = Color(0xFF111A23)
private val OddsAccent = Color(0xFFB7F36B)
private val OddsMuted = Color(0xFF9EADB9)

@Composable
fun DroppingOddsScreen(
    modifier: Modifier,
    movements: List<JSONObject>,
    updatedAt: String,
    onRefresh: () -> Unit
) {
    var sort by rememberSaveable { mutableStateOf("Drop %") }
    val sortedMovements = when(sort) { "Kick-off" -> movements.sortedBy { it.optString("kickoff") }; "Match A-Z" -> movements.sortedBy { it.optString("home") + " " + it.optString("away") }; "League" -> movements.sortedBy { it.optString("competition") }; else -> movements.sortedByDescending { it.optDouble("drop_pct") } }
    Column(
        modifier.fillMaxSize().background(OddsBg).verticalScroll(rememberScrollState())
            .padding(horizontal=17.dp,vertical=18.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("MARKET WATCH",style=MaterialTheme.typography.labelSmall,color=OddsAccent,
                fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
            Text("Dropping odds",style=MaterialTheme.typography.headlineMedium,color=Color(0xFFF2F5F0),
                fontWeight=FontWeight.ExtraBold)
            Text("Track bookmaker prices that have shortened across recorded snapshots.",
                style=MaterialTheme.typography.bodySmall,color=OddsMuted)
        }
        Card(shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFF18252D)),
            border=BorderStroke(1.dp,Color(0xFF30434A))) {
            Column(Modifier.fillMaxWidth().padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("24-HOUR PRICE TRACKER",style=MaterialTheme.typography.labelSmall,color=OddsAccent,
                    fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                Text("3% minimum recorded drop",style=MaterialTheme.typography.titleLarge,color=Color.White,
                    fontWeight=FontWeight.Bold)
                Text(updatedAt,style=MaterialTheme.typography.bodySmall,color=OddsMuted)
                Text("A shorter price shows market movement. It does not prove that a result is more likely.",
                    style=MaterialTheme.typography.bodySmall,color=Color(0xFFBCC7CE),lineHeight=18.sp)
            }
        }
        Button(onClick=onRefresh,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
            colors=ButtonDefaults.buttonColors(containerColor=OddsAccent,contentColor=Color(0xFF17220A))) {
            Text("REFRESH PRICE MOVEMENTS",fontWeight=FontWeight.ExtraBold,letterSpacing=0.4.sp)
        }
        if (movements.isEmpty()) {
            Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=OddsPanel),
                border=BorderStroke(1.dp,Color(0xFF263541))) {
                Column(Modifier.fillMaxWidth().padding(19.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text("No confirmed drops yet",fontWeight=FontWeight.Bold,color=Color(0xFFF2F5F0),
                        style=MaterialTheme.typography.titleMedium)
                    Text("A movement appears only after at least two prices have been recorded for the same selection. New snapshots are collected automatically.",
                        style=MaterialTheme.typography.bodySmall,color=OddsMuted,lineHeight=18.sp)
                }
            }
        } else {
            Text("${movements.size} PRICE MOVEMENTS DETECTED",style=MaterialTheme.typography.labelLarge,
                color=OddsAccent,fontWeight=FontWeight.Bold,letterSpacing=0.7.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Drop %","Kick-off","League","Match A-Z").forEach { value -> FilterChip(selected=sort==value,onClick={sort=value},label={Text(value)}) } }
            sortedMovements.forEach { item ->
                Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=OddsPanel),
                    border=BorderStroke(1.dp,Color(0xFF263541))) {
                    Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("${item.optString("home")}  vs  ${item.optString("away")}",
                            fontWeight=FontWeight.ExtraBold,color=Color(0xFFF2F5F0),
                            style=MaterialTheme.typography.titleMedium)
                        Text("${item.optString("competition")}  ·  ${oddsMovementTitle(item)}",
                            style=MaterialTheme.typography.bodySmall,color=OddsMuted,lineHeight=18.sp)
                        HorizontalDivider(color=Color(0xFF293742))
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            PriceValue("OPENING",String.format("%.2f",item.optDouble("opening_odds")))
                            PriceValue("CURRENT",String.format("%.2f",item.optDouble("current_odds")))
                            PriceValue("PRICE DROP",String.format("%.1f%%",item.optDouble("drop_pct")),OddsAccent)
                        }
                        Text("${item.optInt("samples")} recorded snapshots  ·  last seen ${item.optString("last_seen")}",
                            style=MaterialTheme.typography.labelSmall,color=OddsMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceValue(label:String,value:String,valueColor:Color=Color(0xFFF2F5F0)) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=OddsMuted,letterSpacing=0.5.sp)
        Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=valueColor)
    }
}

private fun oddsMovementTitle(item:JSONObject):String {
    val market=item.optString("market")
    val parts=item.optString("selection").split("/")
    val line=parts.firstOrNull()?.takeIf { it.toDoubleOrNull()!=null }
    val outcome=parts.getOrNull(1)?.lowercase()
    val side=when(market) {
        "home_corners" -> "Home team corners"
        "away_corners" -> "Away team corners"
        "home_cards" -> "Home team bookings (yellow + red)"
        "away_cards" -> "Away team bookings (yellow + red)"
        "home_team_goals" -> "Home team goals"
        "away_team_goals" -> "Away team goals"
        "match_corners" -> "Total match corners"
        "match_cards" -> "Total match bookings (yellow + red)"
        "match_total_goals" -> "Total goals"
        "1X2" -> "1X2"
        "btts" -> "Both teams to score"
        else -> market.replace("_"," ").replaceFirstChar { it.uppercase() }
    }
    val selection=when {
        market=="1X2" -> parts.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""
        market=="btts" -> parts.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: ""
        outcome=="over" -> "Over " + (line ?: "line unavailable")
        outcome=="under" -> "Under " + (line ?: "line unavailable")
        else -> parts.joinToString(" ")
    }
    return listOf(side,selection).filter { it.isNotBlank() }.joinToString(" · ")
}
