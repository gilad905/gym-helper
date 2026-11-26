package gymhelper.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gymhelper.app.AppState
import gymhelper.app.describe
import gymhelper.core.Durations
import gymhelper.core.Plan
import gymhelper.core.RunState
import gymhelper.core.StepKind
import kotlinx.coroutines.delay

@Composable
fun RunScreen(run: RunState, plan: Plan, locked: Boolean, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(200) }
    }
    var confirmEnd by remember { mutableStateOf(false) }

    if (run.done) {
        Finished(run, modifier)
        return
    }
    val step = run.step!!
    val t = describe(run, plan)
    val workout = plan.workout(run.workoutName)
    val wi = AppState.runWorkoutIndex()
    val exCount = workout?.exercises?.size ?: 0
    val remaining = run.remainingMs(now)
    val hasTimer = step.kind != StepKind.SET

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { (run.index.toFloat() / run.steps.size).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Machine ${step.exerciseIndex + 1} of $exCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(t.headline, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            t.exercise?.let {
                Text(it.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            t.detail?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

            if (hasTimer) {
                Text(
                    Durations.format(((remaining + 999) / 1000).toInt()),
                    fontSize = 96.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (run.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (run.paused) Text("Paused", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                val rest = t.exercise?.restSec ?: AppState.settings.value.defaultRestSec
                Text(
                    if (step.set < step.totalSets) "Rest after this set: ${Durations.format(rest)}"
                    else "Last set, then ${Durations.format(AppState.settings.value.transitionSec)} to switch machines",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = AppState::back, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Back")
                }
                FilledTonalButton(
                    onClick = AppState::togglePause, enabled = run.counting || run.paused, modifier = Modifier.weight(1f),
                ) { Icon(if (run.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (run.paused) "Resume" else "Pause") }
                FilledTonalButton(
                    onClick = { AppState.addTime(30) }, enabled = run.counting || run.paused, modifier = Modifier.weight(1f),
                ) { Text("+30s") }
                FilledTonalButton(onClick = AppState::skip, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.SkipNext, "Skip")
                }
            }

            t.exercise?.let { e ->
                ExerciseCard(wi, step.exerciseIndex, e, locked, showName = false)
            }
            TextButton(onClick = { confirmEnd = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("End workout")
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = AppState::primary,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(88.dp),
            ) { Text(t.action, style = MaterialTheme.typography.headlineSmall) }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End workout?") },
            text = { Text("Sets done so far (${run.log.size}) are saved to history.") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; AppState.stop() }) { Text("End") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Finished(run: RunState, modifier: Modifier) {
    val last = run.log.lastOrNull()?.at ?: run.startedAt
    val mins = ((last - run.startedAt) / 60000).coerceAtLeast(0)
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Workout complete", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("${run.workoutName} · ${run.log.size} sets · $mins min", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Saved to history. Unlock 🔒 on the plan to set next session's targets.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = AppState::dismissFinished, modifier = Modifier.height(56.dp)) { Text("Close") }
    }
}
