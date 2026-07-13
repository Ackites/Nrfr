package com.github.nrfr.manager

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.IActivityManager
import android.app.Instrumentation
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.os.Process
import android.os.ServiceManager
import android.os.SystemClock
import android.system.Os
import android.telephony.CarrierConfigManager
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val ARG_CALLER_PID = "caller_pid"
private const val ARG_REQUEST_TOKEN = "request_token"
private const val REQUEST_TTL_MS = 10_000L

internal class PendingCarrierConfigRequest(
    val subId: Int,
    val bundle: PersistableBundle?,
    val createdAtElapsedMs: Long
)

object PrivilegedCarrierConfigRunner {
    private val pendingRequests = ConcurrentHashMap<String, PendingCarrierConfigRequest>()

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    fun overrideConfig(context: Context, subId: Int, bundle: PersistableBundle?) {
        val now = SystemClock.elapsedRealtime()
        pendingRequests.entries.removeAll { now - it.value.createdAtElapsedMs > REQUEST_TTL_MS }

        val token = UUID.randomUUID().toString()
        pendingRequests[token] = PendingCarrierConfigRequest(
            subId = subId,
            bundle = bundle?.let { PersistableBundle(it) },
            createdAtElapsedMs = now
        )

        val args = Bundle().apply {
            putInt(ARG_CALLER_PID, Process.myPid())
            putString(ARG_REQUEST_TOKEN, token)
        }

        val activity = ServiceManager.getService(Context.ACTIVITY_SERVICE)
        val activityManager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(activity))
        val component = ComponentName(context, PrivilegedCarrierConfigInstrumentation::class.java)
        val flags = ActivityManager.INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS or
            ActivityManager.INSTR_FLAG_NO_RESTART

        val started = try {
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
        } catch (t: Throwable) {
            pendingRequests.remove(token)
            throw t
        }
        if (!started) {
            pendingRequests.remove(token)
            throw IllegalStateException("Unable to start privileged carrier config instrumentation")
        }
    }

    private fun consumeRequest(token: String?, callerPid: Int): PendingCarrierConfigRequest? {
        if (callerPid != Process.myPid() || token.isNullOrEmpty()) {
            return null
        }

        val request = pendingRequests.remove(token) ?: return null
        if (SystemClock.elapsedRealtime() - request.createdAtElapsedMs > REQUEST_TTL_MS) {
            return null
        }
        return request
    }

    internal fun consumeRequest(arguments: Bundle): PendingCarrierConfigRequest? {
        return consumeRequest(
            token = arguments.getString(ARG_REQUEST_TOKEN),
            callerPid = arguments.getInt(ARG_CALLER_PID, 0)
        )
    }
}

class PrivilegedCarrierConfigInstrumentation : Instrumentation() {
    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")
    }

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)

        val request = PrivilegedCarrierConfigRunner.consumeRequest(arguments)
        if (request == null) {
            finishWithError("Invalid or expired privileged request")
            return
        }

        var activityManager: IActivityManager? = null
        var delegatedByThisCall = false
        var persistent = false
        var error: String? = null

        try {
            val activity = ServiceManager.getService(Context.ACTIVITY_SERVICE)
            val manager = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(activity))
            activityManager = manager
            manager.startDelegateShellPermissionIdentity(
                Os.getuid(),
                arrayOf(Manifest.permission.MODIFY_PHONE_STATE)
            )
            delegatedByThisCall = true

            try {
                overrideCarrierConfig(request, persistent = true)
                persistent = true
            } catch (e: SecurityException) {
                if (isKnownPersistentOverrideRestriction(e)) {
                    overrideCarrierConfig(request, persistent = false)
                } else {
                    throw e
                }
            }
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        } finally {
            if (delegatedByThisCall) {
                try {
                    activityManager?.stopDelegateShellPermissionIdentity()
                } catch (cleanupError: Throwable) {
                    if (error == null) {
                        error = cleanupError.message ?: cleanupError.javaClass.simpleName
                    }
                }
            }

            val result = Bundle().apply {
                putBoolean("success", error == null)
                putBoolean("persistent", persistent)
                error?.let { putString("error", it) }
            }
            finish(if (error == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    private fun overrideCarrierConfig(request: PendingCarrierConfigRequest, persistent: Boolean) {
        val manager = targetContext.getSystemService(CarrierConfigManager::class.java)
        manager.overrideConfig(request.subId, request.bundle, persistent)
    }

    private fun isKnownPersistentOverrideRestriction(error: SecurityException): Boolean {
        val message = error.message ?: return false
        return message.contains("persistent=true", ignoreCase = true) ||
            message.contains("system app", ignoreCase = true)
    }

    private fun finishWithError(message: String) {
        finish(
            Activity.RESULT_CANCELED,
            Bundle().apply {
                putBoolean("success", false)
                putString("error", message)
            }
        )
    }
}
