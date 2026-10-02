package com.github.nrfr.manager

import android.content.Context
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

        try {
            val subscriptionManager = SubscriptionManager.from(context)
            val subscriptions = subscriptionManager.activeSubscriptionInfoList ?: emptyList()

            subscriptions.forEachIndexed { index, info ->
                val subId = info.subscriptionId
                val config = getCurrentConfig(subId)
                simCards.add(
                    SimCardInfo(
                        index + 1,
                        subId,
                        getCarrierNameBySubId(context, subId),
                        config
                    )
                )
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return simCards
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
            val config = carrierConfigLoader.getConfigForSubId(subId, "com.github.nrfr")
                ?: return emptyMap()

            val result = mutableMapOf<String, String>()

            config.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result["国家码"] = it
            }

            if (config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                    result["运营商名称"] = it
                }
            }

            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun getCarrierNameBySubId(context: Context, subId: Int): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""

        return try {
            telephonyManager.createForSubscriptionId(subId).networkOperatorName
        } catch (_: Exception) {
            telephonyManager.networkOperatorName
        }
    }

    fun setCarrierConfig(subId: Int, countryCode: String?, carrierName: String? = null) {
        val bundle = PersistableBundle()

        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }

        overrideCarrierConfig(subId, bundle)
    }

    fun resetCarrierConfig(subId: Int) {
        overrideCarrierConfig(subId, null)
    }

    private fun overrideCarrierConfig(subId: Int, bundle: PersistableBundle?) {
        val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        carrierConfigLoader.overrideConfig(subId, bundle, true)
    }
}
