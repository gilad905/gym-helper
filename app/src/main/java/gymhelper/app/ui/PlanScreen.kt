package gymhelper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import gymhelper.app.AppState
import gymhelper.core.Days
import gymhelper.core.Exercise
import gymhelper.core.Plan
import gymhelper.core.ScheduleEntry
import gymhelper.core.Workout
import java.time.LocalDate

@Composable
fun PlanScreen(plan: Plan, locked: Boolean, onImport: () -> Unit, modifier: Modifier = Modifier) {
    if (plan.workouts.isEmpty() && locked) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.FitnessCenter, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("No plan yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Import your plan as a markdown file, or the zip you export from Notion (photos included).",
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onImport) { Text("Import plan") }
            Spacer(Modifier.height(8.dp))
            Text("Or unlock 🔒 to build one by hand.", style = MaterialTheme.typography.bodySmall)
        }
        return
    }

    val today = LocalDate.now().dayOfWeek.value
    val todayIndex = plan.workoutForDay(today)?.let { t -> plan.workouts.indexOfFirst { it.name == t.name } } ?: 0
    var selected by rememberSaveable(plan.workouts.size) { mutableIntStateOf(todayIndex.coerceAtLeast(0)) }
    val wi = selected.coerceIn(0, (plan.workouts.size - 1).coerceAtLeast(0))
    val workout = plan.workouts.getOrNull(wi)
    var confirmDelete by remember { mutableStateOf(false) }
    // Exercise cards start collapsed; tap one to open it.
    var expanded by remember(wi) { mutableStateOf(setOf<Int>()) }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(plan.workouts) { i, w ->
                        val isToday = plan.workoutForDay(today)?.name == w.name
                        FilterChip(
                            selected = i == wi,
                            onClick = { selected = i },
                            label = { Text(if (isToday) "${w.name} · today" else w.name) },
                            leadingIcon = workoutIcon(w.name)?.let { icon -> { Icon(icon, null, Modifier.size(18.dp)) } },
                        )
                    }
                    if (!locked) {
                        item {
                            TextButton(onClick = {
                                AppState.updatePlan { it.copy(workouts = it.workouts + Workout("New workout")) }
                                selected = plan.workouts.size
                            }) { Icon(Icons.Filled.Add, null); Text("Workout") }
                        }
                    }
                }
            }
            item { ScheduleCard(plan, locked, today) }
            if (workout != null && !locked) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EditField(workout.name, { v -> renameWorkout(wi, workout.name, v) }, Modifier.weight(1f), label = "Workout name")
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete workout") }
                    }
                }
            }
            if (workout != null) {
                itemsIndexed(workout.exercises) { i, e ->
                    ExerciseCard(
                        wi, i, e, locked,
                        expanded = i in expanded,
                        onToggle = { expanded = if (i in expanded) expanded - i else expanded + i },
                        canMove = (i > 0) to (i < workout.exercises.lastIndex),
                    )
                }
                if (!locked) {
                    item {
                        OutlinedButton(
                            onClick = { AppState.updateWorkout(wi) { it.copy(exercises = it.exercises + Exercise("New exercise")) } },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add exercise") }
                    }
                }
            }
        }
        if (workout != null && workout.exercises.isNotEmpty()) {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { AppState.start(workout) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
                    colors = goButtonColors(),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start ${workout.name}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    if (confirmDelete && workout != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${workout.name}?") },
            text = { Text("Its ${workout.exercises.size} exercises will be removed from the plan.") },
            confirmButton = {
                TextButton(onClick = {
                    AppState.updatePlan { p -> p.copy(workouts = p.workouts.filterIndexed { i, _ -> i != wi }) }
                    selected = 0
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private fun renameWorkout(wi: Int, old: String, new: String) = AppState.updatePlan { p ->
    p.copy(
        workouts = p.workouts.mapIndexed { i, w -> if (i == wi) w.copy(name = new) else w },
        schedule = p.schedule.map { if (it.workout == old) it.copy(workout = new) else it },
    )
}

@Composable
private fun ScheduleCard(plan: Plan, locked: Boolean, today: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Schedule", style = MaterialTheme.typography.titleSmall)
            if (locked) {
                if (plan.schedule.isEmpty()) Text("No schedule", style = MaterialTheme.typography.bodyMedium)
                for (s in plan.schedule.sortedBy { it.day }) {
                    Row {
                        Text(Days.short[s.day - 1], Modifier.width(56.dp),
                            color = if (s.day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.workout)
                    }
                }
            } else {
                Text("Type a workout name per day; leave empty for rest days.", style = MaterialTheme.typography.bodySmall)
                for (d in 1..7) {
                    val current = plan.schedule.firstOrNull { it.day == d }?.workout ?: ""
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(56.dp)) { Text(Days.short[d - 1]) }
                        EditField(current, { v ->
                            AppState.updatePlan { p ->
                                val rest = p.schedule.filter { it.day != d }
                                p.copy(schedule = if (v.isBlank()) rest else (rest + ScheduleEntry(d, v)).sortedBy { it.day })
                            }
                        }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
