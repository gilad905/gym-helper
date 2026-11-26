package gymhelper.app.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import gymhelper.app.AppState
import gymhelper.app.ImportExport
import gymhelper.core.Plan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymAppUi() {
    val plan by AppState.plan.collectAsStateWithLifecycle()
    val run by AppState.run.collectAsStateWithLifecycle()
    val locked by AppState.locked.collectAsStateWithLifecycle()
    val settings by AppState.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Plan?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Keep the screen on while a workout runs.
    val keepOn = run != null && settings.keepScreenOn
    DisposableEffect(keepOn) {
        val window = (ctx as? Activity)?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { ImportExport.import(ctx, uri) }
                if (AppState.plan.value.workouts.isEmpty()) {
                    AppState.setPlan(p)
                    snackbar.showSnackbar("Imported ${p.workouts.size} workouts")
                } else {
                    pendingImport = p
                }
            } catch (e: Exception) {
                snackbar.showSnackbar("Import failed: ${e.message}")
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ImportExport.export(ctx, uri) }
                snackbar.showSnackbar("Exported plan and history")
            } catch (e: Exception) {
                snackbar.showSnackbar("Export failed: ${e.message}")
            }
        }
    }
    val onImport = { importer.launch(arrayOf("*/*")) }
    val onExport = { exporter.launch("gym-plan.zip") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (locked) "Gym Helper" else "Editing") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (locked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.tertiaryContainer,
                ),
                actions = {
                    if (locked) {
                        IconButton(onClick = AppState::unlock) { Icon(Icons.Filled.Lock, "Unlock editing") }
                    } else {
                        TextButton(onClick = {
                            if (AppState.hasUnsavedEdits()) confirmCancel = true else AppState.cancelEdits()
                        }) { Icon(Icons.Filled.Close, null); Text("Cancel") }
                        Button(onClick = AppState::saveEdits) { Icon(Icons.Filled.Check, null); Text("Save") }
                    }
                    IconButton(onClick = AppState::toggleMute) {
                        Icon(
                            if (settings.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (settings.muted) "Unmute" else "Mute",
                        )
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Import plan…") },
                            leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = { menu = false; onImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export plan + history…") },
                            leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = { menu = false; onExport() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.FitnessCenter, null) }, label = { Text("Train") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.History, null) }, label = { Text("History") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            0 -> if (run != null) RunScreen(run!!, plan, locked, m) else PlanScreen(plan, locked, onImport, m)
            1 -> HistoryScreen(locked, m)
            else -> SettingsScreen(settings, onImport, onExport, m)
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Discard your changes?") },
            text = { Text("Everything you changed since unlocking goes back to how it was.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; AppState.cancelEdits() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep editing") } },
        )
    }

    pendingImport?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace your plan?") },
            text = { Text("The imported plan has ${p.workouts.size} workouts. Your current plan will be replaced. Workout history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    AppState.setPlan(p)
                    pendingImport = null
                    scope.launch { snackbar.showSnackbar("Plan imported") }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
}
