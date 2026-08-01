package com.github.nrfr.manager

import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.TelephonyManager
import com.android.internal.telephony.ICarrierConfigLoader
import com.github.nrfr.model.SimCardInfo
import rikka.shizuku.ShizukuBinderWrapper

object CarrierConfigManager {
    fun getSimCards(context: Context): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()
        val subId1 = getSubIdForSlot(0)
        val subId2 = getSubIdForSlot(1)

        if (subId1 != null) {
            val config1 = getCurrentConfig(subId1)
            simCards.add(SimCardInfo(1, subId1, getCarrierNameBySubId(context, subId1), config1))
        }
        if (subId2 != null) {
            val config2 = getCurrentConfig(subId2)
            simCards.add(SimCardInfo(2, subId2, getCarrierNameBySubId(context, subId2), config2))
        }

        return simCards
    }

    /**
     * Android 16 on some vendor ROMs exposes [-1] through the legacy static
     * getSubId(slot) API even though the subscription service has a valid
     * slot-to-subscription mapping. Resolve that mapping through the Shizuku
     * shell only when the direct API does not return a usable ID.
     */
    private fun getSubIdForSlot(slotIndex: Int): Int? {
        val directSubId = runCatching {
            SubscriptionManager.getSubId(slotIndex)
                ?.firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        }.getOrNull()

        return directSubId ?: PrivilegedCarrierConfigRunner.getSubIdForSlot(slotIndex)
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
        try {
            val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .carrierConfigServiceRegisterer
                        .get()
                )
            )
            val config = carrierConfigLoader.getConfigForSubId(subId, "com.github.nrfr") ?: return emptyMap()

            val result = mutableMapOf<String, String>()

            // 获取国家码配置
            config.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result["国家码"] = it
            }

            // 获取运营商名称配置
            if (config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
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
                // Android 10 及以上使用新 API
                telephonyManager.getNetworkOperatorName(subId)
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

    fun setCarrierConfig(
        context: Context,
        subId: Int,
        countryCode: String?,
        carrierName: String? = null
    ): Boolean {
        val bundle = PersistableBundle()

        // 设置国家码
        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        // 设置运营商名称
        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }

        return overrideCarrierConfig(context, subId, bundle)
    }

    fun resetCarrierConfig(context: Context, subId: Int): Boolean {
        return overrideCarrierConfig(context, subId, null)
    }

    private fun overrideCarrierConfig(context: Context, subId: Int, bundle: PersistableBundle?): Boolean {
        val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        try {
            // Android 16 accepts the temporary override path on affected ROMs,
            // while persistent=true may be silently ignored or rejected later.
            val persistent = Build.VERSION.SDK_INT < 36
            carrierConfigLoader.overrideConfig(subId, bundle, persistent)
            return false
        } catch (e: SecurityException) {
            if (e.message?.contains("cannot be invoked by shell") == true) {
                PrivilegedCarrierConfigRunner.overrideConfig(context, subId, bundle)
                return true
            } else {
                throw e
            }
        }
    }
}
