package com.github.nrfr.manager

import android.app.ActivityManager
import android.app.IActivityManager
import android.app.Instrumentation
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.os.ServiceManager
import android.system.Os
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.InputStream

private val LOGICAL_SIM_SLOT_PATTERN = Regex("""^\s*Logical SIM slot (\d+): subId=(-?\d+)""")

private const val ARG_CALLER_PID = "caller_pid"
private const val ARG_SUB_ID = "sub_id"
private const val ARG_CONFIG = "config"
private const val ARG_RESET = "reset"

object PrivilegedCarrierConfigRunner {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?) {
        val args = Bundle().apply {
            putInt(ARG_CALLER_PID, Process.myPid())
            putInt(ARG_SUB_ID, subId)
            putBoolean(ARG_RESET, bundle == null)
            if (bundle != null) {
                putParcelable(ARG_CONFIG, bundle)
            }
        }

        val activity = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val activityManager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(activity))
        val component = ComponentName(context, PrivilegedCarrierConfigInstrumentation::class.java)
        val flags = ActivityManager.INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS or
            ActivityManager.INSTR_FLAG_NO_RESTART

        activityManager.startInstrumentation(
            component,
            null,
            flags,
            args,
            null,
            UiAutomationConnection(),
            0,
            null
        )
    }

    /** Resolve the active subscription ID when Android returns INVALID_SUBSCRIPTION_ID. */
    fun getSubIdForSlot(slotIndex: Int): Int? {
        val output = runCatching {
            runShellCommand(arrayOf("dumpsys", "isub"))
        }.getOrNull() ?: return null

        return output.lineSequence().mapNotNull { line ->
            val match = LOGICAL_SIM_SLOT_PATTERN.find(line) ?: return@mapNotNull null
            val slot = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val subId = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            if (slot == slotIndex && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                subId
            } else {
                null
            }
        }.firstOrNull()
    }

    private fun runShellCommand(command: Array<String>): String {
        val process = newShizukuProcess(command)
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutThread = readAsync(process.inputStream, stdout)
        val stderrThread = readAsync(process.errorStream, stderr)
        val exitCode = process.waitFor()
        stdoutThread.join()
        stderrThread.join()

        if (exitCode != 0) {
            throw IllegalStateException(stderr.toString().ifBlank { "dumpsys isub failed: $exitCode" })
        }
        return stdout.toString()
    }

    private fun newShizukuProcess(command: Array<String>): Process {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        newProcess.isAccessible = true
        return newProcess.invoke(null, command, null, null) as Process
    }

    private fun readAsync(inputStream: InputStream, output: StringBuilder): Thread {
        return Thread {
            inputStream.bufferedReader().use { reader ->
                output.append(reader.readText())
            }
        }.apply { start() }
    }
}

class PrivilegedCarrierConfigInstrumentation : Instrumentation() {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)

        if (arguments.getInt(ARG_CALLER_PID, 0) != Process.myPid()) {
            finish(0, Bundle())
            return
        }

        val activity = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val activityManager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(activity))

        val subId = arguments.getInt(ARG_SUB_ID)
        val bundle = getPersistableBundle(arguments)

        try {
            activityManager.startDelegateShellPermissionIdentity(Os.getuid(), null)
            // On Android 16 the temporary override is the effective path on
            // vendor ROMs that accept the write but do not apply persistence.
            val persistent = Build.VERSION.SDK_INT < 36
            overrideCarrierConfig(subId, bundle, persistent)
        } catch (e: SecurityException) {
            overrideCarrierConfig(subId, bundle, persistent = false)
        } finally {
            activityManager.stopDelegateShellPermissionIdentity()
            finish(0, Bundle())
        }
    }

    private fun overrideCarrierConfig(
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        val manager = targetContext.getSystemService(CarrierConfigManager::class.java)
        manager.overrideConfig(subId, bundle, persistent)
    }

    private fun getPersistableBundle(arguments: Bundle): PersistableBundle? {
        if (arguments.getBoolean(ARG_RESET)) {
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments.getParcelable(ARG_CONFIG, PersistableBundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments.getParcelable<PersistableBundle>(ARG_CONFIG)
        }
    }
}
