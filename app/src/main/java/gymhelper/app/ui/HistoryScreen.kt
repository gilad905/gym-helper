package gymhelper.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gymhelper.app.AppState
import gymhelper.core.Keys
import gymhelper.core.LastNext
import gymhelper.core.Session
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm")

@Composable
fun HistoryScreen(locked: Boolean, modifier: Modifier = Modifier) {
    val history by AppState.history.collectAsStateWithLifecycle()
    val sessions = remember(history) { history.sessions.sortedByDescending { it.startedAt } }
    var toDelete by remember { mutableStateOf<Session?>(null) }

    if (sessions.isEmpty()) {
        Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.History, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No workouts yet", style = MaterialTheme.typography.headlineSmall)
            Text("Finished workouts show up here.")
        }
        return
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sessions, key = { it.id }) { s ->
            var open by rememberSaveable(s.id) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable { open = !open }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(workoutIcon(s.workoutName) ?: Icons.Filled.EventAvailable, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.workoutName, style = MaterialTheme.typography.titleMedium)
                            val mins = ((s.endedAt - s.startedAt) / 60000).coerceAtLeast(0)
                            Text(
                                "${fmt.format(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()))} · $mins min · ${s.sets.size} sets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!locked) IconButton(onClick = { toDelete = s }) { Icon(Icons.Filled.Delete, "Delete session") }
                    }
                    if (open) {
                        for ((_, sets) in s.byExercise()) {
                            HorizontalDivider()
                            Text(sets.first().values[LastNext.NAME_KEY] ?: sets.first().exerciseKey, style = MaterialTheme.typography.titleSmall)
                            for (l in sets) {
                                val label = l.phase?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Set ${l.set}"
                                val vals = l.values.filterKeys { it != LastNext.NAME_KEY && it != Keys.TYPE && it != Keys.REST && it != Keys.SETS }
                                    .entries.joinToString(" · ") { "${it.key} ${it.value}" }
                                Text("$label: $vals", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("${s.workoutName}, ${fmt.format(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()))}") },
            confirmButton = { TextButton(onClick = { AppState.deleteSession(s.id); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
