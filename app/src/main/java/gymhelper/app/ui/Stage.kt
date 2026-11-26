@file:Suppress("DEPRECATION")

package gymhelper.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TransferWithinAStation
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import gymhelper.core.Exercise
import gymhelper.core.ExerciseType
import gymhelper.core.RunState
import gymhelper.core.Step
import gymhelper.core.StepKind

/** What part of the workout you're in; each gets its own color and icon. */
enum class Stage(val label: String) { SET("Set"), REST("Rest"), MOVING("Moving"), WARMUP("Warmup"), WORK("Cardio"), COOLDOWN("Cooldown") }

fun stageOf(step: Step): Stage = when (step.kind) {
    StepKind.SET -> Stage.SET
    StepKind.REST -> Stage.REST
    StepKind.TRANSITION -> Stage.MOVING
    StepKind.TIMED -> when (step.phase) {
        "WARMUP" -> Stage.WARMUP
        "COOLDOWN" -> Stage.COOLDOWN
        else -> Stage.WORK
    }
}

data class StageColors(val container: Color, val content: Color)

@Composable
fun stageColors(stage: Stage): StageColors {
    val dark = isSystemInDarkTheme()
    fun c(light: Long, darkBg: Long) =
        if (dark) StageColors(Color(darkBg), Color.White) else StageColors(Color(light), Color(0xFF1A1A1A))
    return when (stage) {
        Stage.SET -> StageColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        Stage.REST -> c(0xFFD3E3FD, 0xFF1C3A5E)        // blue
        Stage.MOVING -> c(0xFFE8DDFF, 0xFF3D2C66)      // purple
        Stage.WARMUP -> c(0xFFFFDDB8, 0xFF603A00)      // orange
        Stage.WORK -> c(0xFFD2F4D9, 0xFF1D4D2C)        // green
        Stage.COOLDOWN -> c(0xFFC8F0F4, 0xFF004E57)    // teal
    }
}

fun stageIcon(stage: Stage): ImageVector = when (stage) {
    Stage.SET -> Icons.Filled.FitnessCenter
    Stage.REST -> Icons.Filled.Timer
    Stage.MOVING -> Icons.Filled.TransferWithinAStation
    Stage.WARMUP -> Icons.Filled.LocalFireDepartment
    Stage.WORK -> Icons.Filled.DirectionsRun
    Stage.COOLDOWN -> Icons.Filled.AcUnit
}

fun exerciseIcon(e: Exercise): ImageVector {
    val n = e.name.lowercase()
    return when (e.type) {
        ExerciseType.TREADMILL -> Icons.Filled.DirectionsRun
        ExerciseType.CARDIO -> if ("bike" in n || "fahrrad" in n) Icons.Filled.PedalBike else Icons.Filled.MonitorHeart
        ExerciseType.STRENGTH -> Icons.Filled.FitnessCenter
    }
}

/** Icons for the usual workout kinds; custom workouts get none. */
fun workoutIcon(name: String): ImageVector? {
    val n = name.lowercase()
    return when {
        listOf("cardio", "run", "treadmill", "laufen").any { it in n } -> Icons.Filled.DirectionsRun
        listOf("full body", "fullbody", "strength", "upper", "lower", "leg", "arm", "push", "pull", "core").any { it in n } ->
            Icons.Filled.FitnessCenter
        else -> null
    }
}

val GoGreen = Color(0xFF2E7D32)

/** Green for "go" actions: Start, I'm ready, Set done. */
@Composable
fun goButtonColors(): ButtonColors = ButtonDefaults.buttonColors(containerColor = GoGreen, contentColor = Color.White)

/** A short summary for a collapsed card, e.g. "3 × 15 · 15 kg" or "3 + 25 + 3 min". */
fun summary(e: Exercise): String = when (e.type) {
    ExerciseType.STRENGTH -> listOfNotNull(
        "${e.sets} × ${e.prop("Reps") ?: "?"}",
        e.prop("Weight"),
    ).joinToString(" · ")
    ExerciseType.CARDIO -> listOfNotNull(e.prop("Duration"), if (e.sets > 1) "${e.sets} sets" else null).joinToString(" · ")
    ExerciseType.TREADMILL -> {
        val w = e.effectiveWarmup?.duration
        val c = e.effectiveCooldown?.duration
        listOfNotNull(w?.let { "warmup $it" }, e.prop("Duration"), c?.let { "cooldown $it" }).joinToString(" · ")
    }
}
