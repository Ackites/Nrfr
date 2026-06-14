package com.github.nrfr.manager

import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager as PlatformCarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.github.nrfr.model.SimCardInfo
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.lang.reflect.InvocationTargetException

object CarrierConfigManager {
    fun getSimCards(context: Context): List<SimCardInfo> {
        val simCards = getActiveSubscriptions(context).map { subscription ->
            val slot = subscription.simSlotIndex + 1
            val subId = subscription.subscriptionId
            val carrierName = subscription.carrierName?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: getCarrierNameBySubId(context, subId)
            SimCardInfo(slot, subId, carrierName, getCurrentConfig(subId))
        }.toMutableList()

        if (simCards.isNotEmpty()) return simCards

        for (slotIndex in 0 until getActiveModemCount(context).coerceAtLeast(2)) {
            val subId = getSubIdForSlot(slotIndex) ?: continue
            simCards.add(
                SimCardInfo(
                    slotIndex + 1,
                    subId,
                    getCarrierNameBySubId(context, subId),
                    getCurrentConfig(subId)
                )
            )
        }

        return simCards
    }

    private fun getActiveSubscriptions(context: Context): List<SubscriptionInfo> {
        return try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
                ?: return emptyList()
            subscriptionManager.activeSubscriptionInfoList
                ?.filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
                ?.sortedBy { it.simSlotIndex }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getActiveModemCount(context: Context): Int {
        return try {
            val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephonyManager?.activeModemCount ?: 2
            } else {
                telephonyManager?.phoneCount ?: 2
            }
        } catch (_: Exception) {
            2
        }
    }

    private fun getSubIdForSlot(slotIndex: Int): Int? {
        return try {
            val getSubId = SubscriptionManager::class.java.getDeclaredMethod(
                "getSubId",
                Int::class.javaPrimitiveType
            )
            @Suppress("UNCHECKED_CAST")
            (getSubId.invoke(null, slotIndex) as? IntArray)
                ?.firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        } catch (_: Exception) {
            null
        }
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
        try {
            val carrierConfigLoader = getCarrierConfigLoader()
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                invokeCarrierConfigLoader(
                    carrierConfigLoader,
                    "getConfigForSubIdWithFeature",
                    arrayOf(
                        Int::class.javaPrimitiveType!!,
                        String::class.java,
                        String::class.java
                    ),
                    subId,
                    "com.github.nrfr",
                    null
                )
            } else {
                invokeCarrierConfigLoader(
                    carrierConfigLoader,
                    "getConfigForSubId",
                    arrayOf(Int::class.javaPrimitiveType!!, String::class.java),
                    subId,
                    "com.github.nrfr"
                )
            } as? PersistableBundle ?: return emptyMap()

            val result = mutableMapOf<String, String>()

            // 获取国家码配置
            config.getString(PlatformCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result["国家码"] = it
            }

            // 获取运营商名称配置
            if (config.getBoolean(PlatformCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(PlatformCarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                    result["运营商名称"] = it
                }
            }

            return result
        } catch (e: Exception) {
            return emptyMap()
        }
    }

    private fun getCarrierNameBySubId(context: Context, subId: Int): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.createForSubscriptionId(subId).networkOperatorName
            } else {
                // Android 8-9 使用反射获取运营商名称
                val createForSubscriptionId = TelephonyManager::class.java.getMethod(
                    "createForSubscriptionId",
                    Int::class.javaPrimitiveType
                )
                val subTelephonyManager = createForSubscriptionId.invoke(telephonyManager, subId) as TelephonyManager
                subTelephonyManager.networkOperatorName
            }
        } catch (e: Exception) {
            // 如果获取失败，回退到默认的 TelephonyManager
            telephonyManager.networkOperatorName
        }
    }

    fun setCarrierConfig(subId: Int, countryCode: String?, carrierName: String? = null) {
        val bundle = PersistableBundle()

        // 设置国家码
        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                PlatformCarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        // 设置运营商名称
        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(PlatformCarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(PlatformCarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }

        overrideCarrierConfig(subId, bundle)
    }

    fun resetCarrierConfig(subId: Int) {
        overrideCarrierConfig(subId, null)
    }

    private fun overrideCarrierConfig(subId: Int, bundle: PersistableBundle?) {
        try {
            val carrierConfigLoader = getCarrierConfigLoader()
            try {
                overrideCarrierConfig(carrierConfigLoader, subId, bundle, persistent = true)
            } catch (e: InvocationTargetException) {
                val cause = e.targetException
                if (cause is SecurityException && cause.isPersistentWriteDenied()) {
                    overrideCarrierConfig(carrierConfigLoader, subId, bundle, persistent = false)
                } else {
                    throw e
                }
            }
        } catch (e: InvocationTargetException) {
            val cause = e.targetException
            if (cause is SecurityException) {
                throw IllegalStateException(buildSecurityMessage(cause), cause)
            }
            throw IllegalStateException("写入运营商配置失败：${cause.message ?: cause.javaClass.simpleName}", cause)
        } catch (e: Exception) {
            throw IllegalStateException("写入运营商配置失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    private fun overrideCarrierConfig(
        loader: Any,
        subId: Int,
        bundle: PersistableBundle?,
        persistent: Boolean
    ) {
        invokeCarrierConfigLoader(
            loader,
            "overrideConfig",
            arrayOf(
                Int::class.javaPrimitiveType!!,
                PersistableBundle::class.java,
                Boolean::class.javaPrimitiveType!!
            ),
            subId,
            bundle,
            persistent
        )
    }

    private fun getCarrierConfigLoader(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        val binder = getService.invoke(null, "carrier_config") as? IBinder
            ?: throw IllegalStateException("无法连接 carrier_config 系统服务")

        val stub = Class.forName("com.android.internal.telephony.ICarrierConfigLoader\$Stub")
        val asInterface = stub.getDeclaredMethod("asInterface", IBinder::class.java)
        return asInterface.invoke(null, ShizukuBinderWrapper(binder))
            ?: throw IllegalStateException("无法获取 carrier_config 接口")
    }

    private fun invokeCarrierConfigLoader(
        loader: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?
    ): Any? {
        val method = loader.javaClass.getMethod(methodName, *parameterTypes)
        return method.invoke(loader, *args)
    }

    private fun buildSecurityMessage(error: SecurityException): String {
        val message = error.message.orEmpty()
        return when {
            error.isPersistentWriteDenied() -> {
                "系统只允许系统应用持久修改运营商配置，已无法写入持久配置"
            }
            message.contains("shell", ignoreCase = true) -> {
                "Android 16 已禁止 adb/shell 模式修改运营商配置，请在 Shizuku 中使用 root 模式启动后重试"
            }
            Shizuku.getUid() != 0 -> {
                "当前 Shizuku 不是 root 模式，无法在此系统上修改运营商配置"
            }
            else -> "系统拒绝修改运营商配置：${message.ifBlank { error.javaClass.simpleName }}"
        }
    }

    private fun SecurityException.isPersistentWriteDenied(): Boolean {
        val message = message.orEmpty()
        return message.contains("persistent=true", ignoreCase = true) ||
            message.contains("persist", ignoreCase = true)
    }
}
