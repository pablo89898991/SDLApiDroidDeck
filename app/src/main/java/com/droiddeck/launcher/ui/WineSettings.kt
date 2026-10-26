package com.droiddeck.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.session.ProtonExtras
import com.droiddeck.launcher.session.SessionState
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

// sdl3deck: Settings -> Wine (Proton) updates
/**
 * Keeping Wine (Proton) up to date from DroidDeck's Settings. Windows programs run with an ARM64 Proton: the one the
 * Steam client downloads (Steam updates it), or DroidDeck's own builds from GitHub (ProtonExtras: DroidDeck-Proton,
 * and GE-Proton / Proton-CachyOS when installed), which DroidDeck installs and updates itself. This tab shows what is
 * installed and the newest release of each, checks now instead of hourly, updates on request, and turns the automatic
 * updates (ProtonMenu.ensureAutoUpdates) on or off. Installing is ProtonExtras' own (download, checksum, the old
 * version's components, settings and per-game picks carried over); like DroidDeck, it waits until no session runs --
 * on the desktop, Wine -> Update Wine updates DroidDeck-Proton while it runs (droiddeck-wine).
 */
internal object WineUpdates {
    private const val TAG = "WineUpdates"
    private const val PREFS = "sdl3deck-wine"
    private val main = Handler(Looper.getMainLooper())

    var busyId by mutableStateOf<String?>(null)
        private set
    var stage by mutableStateOf<String?>(null)
        private set
    var percent by mutableIntStateOf(-1)
        private set
    var checking by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    /** Bumped when what is installed or known changed, so the tab reads it again. */
    var revision by mutableIntStateOf(0)
        private set

    fun automatic(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto", true)

    fun setAutomatic(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply()
        revision++
    }

    /** When the newest releases were last looked up (ProtonExtras keeps it next to the tags), or null. */
    fun checkedAt(context: Context): Long? = runCatching {
        val all = JSONObject(File(context.filesDir, "proton-latest.json").readText())
        all.keys().asSequence().mapNotNull { all.optJSONObject(it)?.optLong("at", 0L) }.filter { it > 0 }.maxOrNull()
    }.getOrNull()

    fun check(context: Context, force: Boolean) {
        if (checking) return
        val app = context.applicationContext
        checking = true
        if (force) message = null
        Thread({
            val before = checkedAt(app)
            runCatching { ProtonExtras.checkLatest(app, force) }.onFailure { Log.w(TAG, "check", it) }
            val after = checkedAt(app)
            main.post {
                checking = false
                revision++
                if (force) message = when {
                    after == null || after == before -> "Could not reach GitHub; try again later."
                    ProtonExtras.tools.none { ProtonExtras.update(app, it) != null || ProtonExtras.missing(app, it) != null } ->
                        "Wine is up to date."
                    else -> null
                }
            }
        }, "wine-check").start()
    }

    fun install(context: Context, tool: ProtonExtras.Tool) {
        if (busyId != null || ProtonExtras.installInProgress) {
            message = "Another Wine (Proton) install is running."
            return
        }
        if (SessionState.running) {
            message = "Close the session first (on the desktop: Wine -> Update Wine)."
            return
        }
        val app = context.applicationContext
        busyId = tool.id
        stage = "Starting"
        percent = -1
        message = null
        Thread({
            val problem = runCatching {
                ProtonExtras.install(app, tool) { label, value -> main.post { stage = label; percent = value } }
            }.getOrElse { it.message ?: it.javaClass.simpleName }
            if (problem == null && tool.id == ProtonExtras.OWN) ProtonExtras.setDeclined(app, false)
            val now = ProtonExtras.installedTags(app, tool).lastOrNull()
            main.post {
                busyId = null
                stage = null
                percent = -1
                message = problem ?: "${tool.name} ${now ?: ""} is installed.".replace("  ", " ")
                revision++
            }
        }, "wine-install-${tool.id}").start()
    }
}

@Composable
internal fun WineSettingsTab(host: MenuHost) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val seen = WineUpdates.revision  // read again after a check or an install
    var session by remember { mutableStateOf(SessionState.running) }
    var installing by remember { mutableStateOf(ProtonExtras.installInProgress) }
    LaunchedEffect(Unit) {
        WineUpdates.check(ctx, force = false)  // ProtonExtras' own hourly check, if it is due
        while (true) {
            session = SessionState.running
            installing = ProtonExtras.installInProgress
            delay(1000)
        }
    }
    val own = ProtonExtras.tools.filter { it.id == ProtonExtras.OWN }
    val shown = own + ProtonExtras.tools.filter { it.id != ProtonExtras.OWN && ProtonExtras.installedAll(ctx, it).isNotEmpty() }
    Column {
        SettingsGroup("Wine (Proton)") {
            for (tool in shown) {
                val installed = ProtonExtras.installedTags(ctx, tool).lastOrNull()
                val latest = ProtonExtras.cachedLatest(ctx, tool)
                val update = ProtonExtras.update(ctx, tool)
                val busy = WineUpdates.busyId == tool.id
                val hint = when {
                    busy -> (WineUpdates.stage ?: "Working") + (if (WineUpdates.percent >= 0) " · ${WineUpdates.percent}%" else "")
                    installed == null -> "Not installed" + (latest?.let { " · newest $it" } ?: "") +
                        (if (tool.id == ProtonExtras.OWN) ". Runs Windows programs when Steam's Proton isn't there." else "")
                    update != null -> "Installed $installed · update available: $update"
                    else -> "Installed $installed · up to date"
                }
                val canAct = !busy && WineUpdates.busyId == null && !installing && !session && (installed == null || update != null)
                SettingsRow(tool.name, hint) {
                    SecondaryButton(
                        when {
                            busy -> "Updating"
                            installed == null -> "Install"
                            update != null -> "Update"
                            else -> "Up to date"
                        },
                        enabled = canAct,
                        compact = true,
                        progress = if (busy && WineUpdates.percent >= 0) WineUpdates.percent / 100f else null,
                    ) { WineUpdates.install(ctx, tool) }
                }
            }
            val at = WineUpdates.checkedAt(ctx)
            SettingsRow(
                "Check for updates",
                WineUpdates.message ?: when {
                    WineUpdates.checking -> "Checking GitHub for the newest releases"
                    at != null -> "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
                    else -> "Not checked yet"
                },
            ) {
                SecondaryButton(if (WineUpdates.checking) "Checking" else "Check now", enabled = !WineUpdates.checking, compact = true) {
                    WineUpdates.check(ctx, force = true)
                }
            }
            ToggleRow(
                host, "wine-auto-update", "Update automatically",
                "Installs new versions by itself while no session runs and the connection allows it (as DroidDeck always did). Off: only when you tap Update.",
                WineUpdates.automatic(ctx),
            ) { WineUpdates.setAutomatic(ctx, it) }
            if (session) SettingsRow(
                "A session is running",
                "Wine is updated when no session runs. On the desktop, Wine -> Update Wine updates DroidDeck-Proton meanwhile.",
            ) {}
        }
        SettingsGroup("Steam's Proton") {
            SettingsRow(
                "Proton Experimental / Proton 11.0 (ARM64)",
                "Steam downloads and updates it by itself. Without Steam, DroidDeck-Proton runs Windows programs instead.",
            ) {}
        }
    }
}
