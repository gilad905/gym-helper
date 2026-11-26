package gymhelper.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import gymhelper.app.AppState
import gymhelper.core.Exercise
import gymhelper.core.ExerciseType
import gymhelper.core.Keys
import gymhelper.core.LastNext
import gymhelper.core.Phase
import gymhelper.core.Prop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A text field that keeps its own state while typing and pushes every change out. */
@Composable
fun EditField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    number: Boolean = false,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != text) text = value }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        modifier = modifier,
        label = label?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

/** "12 → 15": last session's value struck lightly, next value bold. */
@Composable
fun LastNextText(last: String?, next: String, big: Boolean = false) {
    val style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    val changed = last != null && last.trim() != next.trim()
    Text(
        buildAnnotatedString {
            if (changed) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.None)) {
                    append(last)
                }
                append("  →  ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append(next) }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(next) }
            }
        },
        style = style,
    )
}

@Composable
fun PlanImage(path: String?, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { AppState.imageFile(path)?.let { decodeSampled(it, 900) } }
    }
    bmp?.let {
        Image(
            it.asImageBitmap(), contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun decodeSampled(f: File, maxSide: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * One exercise with all its properties. Locked: read-only, each value shown as last → next.
 * Unlocked: every field editable in place.
 */
@Composable
fun ExerciseCard(
    workoutIndex: Int,
    exerciseIndex: Int,
    exercise: Exercise,
    locked: Boolean,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
    showImage: Boolean = true,
    canMove: Pair<Boolean, Boolean>? = null,
) {
    val history by AppState.history.collectAsStateWithLifecycle()
    val last = remember(history, exercise.historyKey) { history.lastValues(exercise.historyKey) }
    var showProgress by remember { mutableStateOf(false) }
    fun update(f: (Exercise) -> Exercise) = AppState.updateExercise(workoutIndex, exerciseIndex, f)

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showName) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (locked) {
                        Text(exercise.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (last != null) IconButton(onClick = { showProgress = true }) { Icon(Icons.AutoMirrored.Filled.ShowChart, "Progress") }
                    } else {
                        EditField(exercise.name, { v -> update { it.copy(name = v) } }, Modifier.weight(1f), label = "Name")
                        canMove?.let { (up, down) ->
                            IconButton(onClick = { moveExercise(workoutIndex, exerciseIndex, -1) }, enabled = up) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { moveExercise(workoutIndex, exerciseIndex, 1) }, enabled = down) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { deleteExercise(workoutIndex, exerciseIndex) }) { Icon(Icons.Filled.Delete, "Delete exercise") }
                        }
                    }
                }
            }
            if (showImage && exercise.image != null) PlanImage(exercise.image, Modifier.fillMaxWidth().height(170.dp))

            exercise.props.forEachIndexed { i, p ->
                if (exercise.isTextProp(p.key)) return@forEachIndexed
                PropRow(p, last?.get(p.key), locked,
                    onKey = { k -> update { e -> e.copy(props = e.props.replace(i, p.copy(key = k))) } },
                    onValue = { v -> update { e -> e.copy(props = e.props.replace(i, p.copy(value = v))) } },
                    onDelete = { update { e -> e.copy(props = e.props.filterIndexed { j, _ -> j != i }) } },
                )
            }

            if (exercise.type == ExerciseType.TREADMILL) {
                PhaseBlock(Keys.WARMUP, exercise.effectiveWarmup!!, last, locked) { ph -> update { it.copy(warmup = ph) } }
                PhaseBlock(Keys.COOLDOWN, exercise.effectiveCooldown!!, last, locked) { ph -> update { it.copy(cooldown = ph) } }
            }

            exercise.props.forEachIndexed { i, p ->
                if (!exercise.isTextProp(p.key)) return@forEachIndexed
                if (locked) {
                    HorizontalDivider()
                    Text(p.key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.value, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EditField(p.value, { v -> update { e -> e.copy(props = e.props.replace(i, p.copy(value = v))) } },
                            Modifier.weight(1f), label = p.key, singleLine = false)
                        IconButton(onClick = { update { e -> e.copy(props = e.props.filterIndexed { j, _ -> j != i }) } }) {
                            Icon(Icons.Filled.Close, "Remove")
                        }
                    }
                }
            }

            if (!locked) {
                Row {
                    TextButton(onClick = { update { it.copy(props = it.props + Prop("New", "")) } }) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Property")
                    }
                    if (exercise.prop(Keys.NOTES) == null) {
                        TextButton(onClick = { update { it.copy(props = it.props + Prop(Keys.NOTES, "")) } }) {
                            Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Notes")
                        }
                    }
                }
            }
        }
    }
    if (showProgress) ProgressDialog(exercise) { showProgress = false }
}

@Composable
private fun PropRow(
    p: Prop,
    last: String?,
    locked: Boolean,
    onKey: (String) -> Unit,
    onValue: (String) -> Unit,
    onDelete: () -> Unit,
) {
    if (locked) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.key, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.45f))
            Column(Modifier.weight(0.55f)) { LastNextText(last, p.value) }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditField(p.key, onKey, Modifier.weight(0.4f))
            EditField(p.value, onValue, Modifier.weight(0.6f))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}

@Composable
private fun PhaseBlock(name: String, phase: Phase, last: Map<String, String>?, locked: Boolean, onChange: (Phase) -> Unit) {
    HorizontalDivider()
    Text(name, style = MaterialTheme.typography.titleSmall)
    PropRow(Prop("Duration", phase.duration), last?.get(name), locked, onKey = {}, onValue = { onChange(phase.copy(duration = it)) }, onDelete = {})
    phase.props.forEachIndexed { i, p ->
        PropRow(p, last?.get("$name ${p.key}"), locked,
            onKey = { k -> onChange(phase.copy(props = phase.props.replace(i, p.copy(key = k)))) },
            onValue = { v -> onChange(phase.copy(props = phase.props.replace(i, p.copy(value = v)))) },
            onDelete = { onChange(phase.copy(props = phase.props.filterIndexed { j, _ -> j != i })) },
        )
    }
    if (!locked) {
        Row {
            if (phase.props.none { it.key.equals("Slope", true) }) {
                TextButton(onClick = { onChange(phase.copy(props = phase.props + Prop("Slope", ""))) }) { Text("+ Slope") }
            }
            if (phase.props.none { it.key.equals("Pace", true) }) {
                TextButton(onClick = { onChange(phase.copy(props = phase.props + Prop("Pace", ""))) }) { Text("+ Pace") }
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM")

/** Values of one exercise across past sessions, newest first. */
@Composable
fun ProgressDialog(exercise: Exercise, onDismiss: () -> Unit) {
    val history by AppState.history.collectAsStateWithLifecycle()
    val rows = remember(history, exercise.historyKey) {
        history.sessions.sortedByDescending { it.startedAt }.mapNotNull { s ->
            s.sets.lastOrNull { it.exerciseKey == exercise.historyKey }?.let { s.startedAt to it.values }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rows.isEmpty()) Text("No sessions yet.")
                for ((at, values) in rows) {
                    Text(dateFmt.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelLarge)
                    Text(
                        values.filterKeys { it != LastNext.NAME_KEY && it != Keys.TYPE && it != Keys.REST && it != Keys.SETS }
                            .entries.joinToString(" · ") { "${it.key} ${it.value}" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

fun <T> List<T>.replace(i: Int, v: T): List<T> = mapIndexed { j, x -> if (j == i) v else x }

private fun moveExercise(w: Int, i: Int, d: Int) = AppState.updateWorkout(w) { wk ->
    val l = wk.exercises.toMutableList()
    val j = i + d
    if (j in l.indices) { val t = l[i]; l[i] = l[j]; l[j] = t }
    wk.copy(exercises = l)
}

private fun deleteExercise(w: Int, i: Int) = AppState.updateWorkout(w) { wk ->
    wk.copy(exercises = wk.exercises.filterIndexed { j, _ -> j != i })
}
