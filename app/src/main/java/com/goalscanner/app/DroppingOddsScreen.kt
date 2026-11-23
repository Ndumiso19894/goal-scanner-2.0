package com.goalscanner.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun DroppingOddsScreen(
    modifier: Modifier,
    movements: List<JSONObject>,
    updatedAt: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dropping Odds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Bookmaker prices that have shortened across recorded snapshots.", style = MaterialTheme.typography.bodyMedium)
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("24-hour market watch · 3% minimum drop", fontWeight = FontWeight.SemiBold)
                Text(updatedAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("A shorter price is market movement, not proof that a result is more likely.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh odds movements") }
        if (movements.isEmpty()) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No confirmed drops yet", fontWeight = FontWeight.Bold)
                    Text("The system needs at least two recorded prices for the same selection. New snapshots are collected on the scheduled refresh.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("${movements.size} price movements detected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            movements.forEach { item ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${item.optString("home")} vs ${item.optString("away")}", fontWeight = FontWeight.Bold)
                        Text("${item.optString("competition")} · ${oddsMovementTitle(item)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Was", style = MaterialTheme.typography.labelSmall)
                                Text(String.format("%.2f", item.optDouble("opening_odds")), fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Now", style = MaterialTheme.typography.labelSmall)
                                Text(String.format("%.2f", item.optDouble("current_odds")), fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Drop", style = MaterialTheme.typography.labelSmall)
                                Text(String.format("%.1f%%", item.optDouble("drop_pct")), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("${item.optInt("samples")} snapshots · last seen ${item.optString("last_seen")}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
