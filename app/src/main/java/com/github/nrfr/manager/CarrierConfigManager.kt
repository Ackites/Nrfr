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
    private const val KEY_FORCE_HOME_NETWORK_BOOL = "force_home_network_bool"
    private const val KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY =
        "gsm_nonroaming_networks_string_array"
    private const val CHINA_MOBILE_46000 = "46000"

    fun getSimCards(context: Context): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()
        val subId1 = getSubIdForSlot(0)
        val subId2 = getSubIdForSlot(1)

        if (subId1 != null) {
            val config1 = getCurrentConfig(subId1)
            simCards.add(
                SimCardInfo(
                    1,
                    subId1,
                    getCarrierNameBySubId(context, subId1),
                    config1
                )
            )
        }
        if (subId2 != null) {
            val config2 = getCurrentConfig(subId2)
            simCards.add(
                SimCardInfo(
                    2,
                    subId2,
                    getCarrierNameBySubId(context, subId2),
                    config2
                )
            )
        }
        return simCards
    }

    private fun getSubIdForSlot(slotIndex: Int): Int? {
        val directSubId = runCatching {
            SubscriptionManager.getSubId(slotIndex)
                ?.firstOrNull { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
        }.getOrNull()
        return directSubId ?: PrivilegedCarrierConfigRunner.getSubIdForSlot(slotIndex)
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
        return try {
            val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .carrierConfigServiceRegisterer
                        .get()
                )
            )
            val config = carrierConfigLoader.getConfigForSubId(
                subId,
                "com.github.nrfr"
            ) ?: return emptyMap()

            val result = mutableMapOf<String, String>()
            config.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result["国家码"] = it
            }
            if (config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                    result["运营商名称"] = it
                }
            }
            result["Force Home"] =
                config.getBoolean(KEY_FORCE_HOME_NETWORK_BOOL, false).toString()
            config.getStringArray(KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY)?.let {
                if (it.isNotEmpty()) {
                    result["GSM 非漫游 MCCMNC"] = it.joinToString(",")
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun getCarrierNameBySubId(context: Context, subId: Int): String {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return ""
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.getNetworkOperatorName(subId)
            } else {
                val createForSubscriptionId = TelephonyManager::class.java.getMethod(
                    "createForSubscriptionId",
                    Int::class.javaPrimitiveType
                )
                val subTelephonyManager =
                    createForSubscriptionId.invoke(telephonyManager, subId) as TelephonyManager
                subTelephonyManager.networkOperatorName
            }
        } catch (_: Exception) {
            telephonyManager.networkOperatorName
        }
    }

    fun setChinaMobile46000NonRoaming(context: Context, subId: Int): Boolean {
        val bundle = PersistableBundle().apply {
            putStringArray(
                KEY_GSM_NONROAMING_NETWORKS_STRING_ARRAY,
                arrayOf(CHINA_MOBILE_46000)
            )
        }
        return overrideCarrierConfig(context, subId, bundle)
    }

    fun setForceHomeNetwork(context: Context, subId: Int): Boolean {
        val bundle = PersistableBundle().apply {
            putBoolean(KEY_FORCE_HOME_NETWORK_BOOL, true)
        }
        return overrideCarrierConfig(context, subId, bundle)
    }

    fun resetCarrierConfig(context: Context, subId: Int): Boolean {
        return overrideCarrierConfig(context, subId, null)
    }

    private fun overrideCarrierConfig(
        context: Context,
        subId: Int,
        bundle: PersistableBundle?
    ): Boolean {
        val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        return try {
            val persistent = Build.VERSION.SDK_INT < 36
            carrierConfigLoader.overrideConfig(subId, bundle, persistent)
            false
        } catch (e: SecurityException) {
            if (e.message?.contains("cannot be invoked by shell") == true) {
                PrivilegedCarrierConfigRunner.overrideConfig(context, subId, bundle)
                true
            } else {
                throw e
            }
        }
    }
}
