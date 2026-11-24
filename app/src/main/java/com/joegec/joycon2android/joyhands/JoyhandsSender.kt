package com.joegec.joycon2android.joyhands

import android.content.Context
import android.util.Log
import com.joegec.joycon2android.gamepad.privileged.PrivilegedShell
import com.joegec.joycon2android.gamepad.privileged.ShellProcess
import java.io.File

/** Runs joyhands' sender, as the shell user when Shizuku allows it: docs/joyhands.md#the-sender */
class JoyhandsSender(private val context: Context, private val readyShell: () -> PrivilegedShell?) {

    private var shellProcess: ShellProcess? = null
    private var ownProcess: Process? = null

    @Synchronized
    fun setRunning(running: Boolean) {
        stop()
        if (running) start()
    }

    private fun start() {
        val binary = context.applicationInfo.nativeLibraryDir + "/$LIBRARY"
        try {
            val shell = readyShell()
            if (shell != null) {
                shellProcess = shell.shell("$STOP_OTHERS; sleep 0.3; cp $binary $REMOTE && chmod 755 $REMOTE && exec $REMOTE >/dev/null 2>&1")
                Log.i(TAG, "Sender started as the shell user")
            } else {
                ownProcess = ProcessBuilder(binary).redirectErrorStream(true).redirectOutput(File("/dev/null")).start()
                Log.i(TAG, "Sender started as the app, for Joy-Con 2 only")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start the sender", e)
        }
    }

    private fun stop() {
        shellProcess?.destroy()
        shellProcess = null
        ownProcess?.destroy()
        ownProcess = null
    }

    private companion object {
        const val TAG = "JoyhandsSender"
        const val LIBRARY = "libjcstream.so"
        const val REMOTE = "/data/local/tmp/jcstream"

        // By exact name: this app's copy, and the one joyhands' own app starts.
        const val STOP_OTHERS = "for p in \$(pidof jcstream $LIBRARY); do kill \$p; done"
    }
}
