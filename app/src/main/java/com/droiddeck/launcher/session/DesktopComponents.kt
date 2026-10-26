package com.droiddeck.launcher.session

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.droiddeck.launcher.runtime.BwrapSpawner
import com.droiddeck.launcher.runtime.LinuxRuntime
import java.io.File

/**
 * Windows components for a program started on the desktop (sdl3deck).
 *
 * The desktop's file manager asks for them (right-click a Windows program -> Windows Components...): its
 * droiddeck-windows-components writes wincomponents-<prefix id>.request into the session's launch folder, and
 * SessionService hands it here. The session screen then shows DroidDeck's own Windows components window
 * (WinComponentsDialog) for that prefix, as the game settings do -- the same catalog, recommendations, downloads
 * and picks, kept under the prefix's id. droiddeck-game-env puts the picked components into the prefix whenever a
 * program starts in it, so the next start of that program has them.
 */
object DesktopComponents {
    private const val TAG = "SessionService"
    private val ID = Regex("[1-9][0-9]{0,9}")

    class Ask(val appKey: String, val name: String, val compat: File?, val gameDir: File?)

    /** The window to show over the session, or null. */
    var asked by mutableStateOf<Ask?>(null)

    /** A request from the desktop ("app=", "name=", "compat=" and "dir=" lines; the paths as the guest sees them). */
    fun request(context: Context, text: String) {
        val fields = text.lineSequence().mapNotNull { line ->
            line.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim() }
        }.toMap()
        val key = fields["app"]?.takeIf { ID.matches(it) && it.toLong() <= 0xFFFFFFFFL } ?: return
        val name = fields["name"].orEmpty().filter { it >= ' ' }.take(120).ifBlank { "Windows program" }
        val binds = runCatching { LinuxRuntime.lastBinds(context).map { BwrapSpawner.Bind.parse(it) } }.getOrDefault(emptyList())
        val rootfs = LinuxRuntime.rootDir(context).path
        fun host(guest: String?): File? = guest?.takeIf { it.startsWith("/") && !it.contains("/../") }?.let {
            runCatching { File(BwrapSpawner.hostPath(binds, rootfs, it)) }.getOrNull()
        }
        val ask = Ask(key, name, host(fields["compat"]), host(fields["dir"])?.takeIf { it.isDirectory })
        Log.i(TAG, "desktop: Windows components for \"$name\" (prefix $key)")
        Handler(Looper.getMainLooper()).post { asked = ask }
    }
}
