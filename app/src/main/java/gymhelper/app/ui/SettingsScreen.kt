package gymhelper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import gymhelper.app.AppState
import gymhelper.app.Settings

@Composable
fun SettingsScreen(settings: Settings, onImport: () -> Unit, onExport: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Timers", style = MaterialTheme.typography.titleMedium)
        DurationSetting("Machine switch", settings.transitionSec) { s -> AppState.updateSettings { it.copy(transitionSec = s) } }
        DurationSetting("Rest when the plan has none", settings.defaultRestSec) { s -> AppState.updateSettings { it.copy(defaultRestSec = s) } }

        HorizontalDivider()
        Text("Alerts", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Mute beeps", settings.muted) { AppState.toggleMute() }
        SwitchRow("Vibrate", settings.vibrate) { v -> AppState.updateSettings { it.copy(vibrate = v) } }
        Text(
            "Beeps follow your phone's alarm volume and play through the speaker and headphones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Keep screen on during workouts", settings.keepScreenOn) { v -> AppState.updateSettings { it.copy(keepScreenOn = v) } }

        HorizontalDivider()
        Text("Plan files", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImport) { Text("Import .md / .zip") }
            OutlinedButton(onClick = onExport) { Text("Export zip") }
        }
        Text(
            "Export writes plan.md, its photos and history.md into one zip.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        Text("Gym Helper $version", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DurationSetting(label: String, sec: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        EditField(
            sec.toString(),
            { v -> v.trim().toIntOrNull()?.let { if (it in 0..3600) onChange(it) } },
            Modifier.fillMaxWidth(0.4f),
            label = "seconds",
            number = true,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
