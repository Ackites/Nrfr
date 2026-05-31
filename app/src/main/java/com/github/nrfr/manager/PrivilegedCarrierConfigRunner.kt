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
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper

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
            overrideCarrierConfig(subId, bundle, persistent = true)
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
