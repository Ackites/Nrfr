package com.github.nrfr.data

object PresetCarriers {
    data class CarrierPreset(
        val name: String,
        val displayName: String,
        val region: String
    )

    val presets = listOf(
        // 中国大陆运营商
        CarrierPreset("中国移动", "China Mobile", "CN"),
        CarrierPreset("中国联通", "China Unicom", "CN"),
        CarrierPreset("中国电信", "China Telecom", "CN"),

        // 中国香港运营商
        CarrierPreset("中国移动香港", "CMHK", "HK"),
        CarrierPreset("香港电讯", "HKT", "HK"),
        CarrierPreset("3香港", "3HK", "HK"),
        CarrierPreset("SmarTone", "SmarTone", "HK"),

        // 中国澳门运营商
        CarrierPreset("澳门电讯", "CTM", "MO"),
        CarrierPreset("3澳门", "3 Macau", "MO"),

        // 中国台湾运营商
        CarrierPreset("中华电信", "Chunghwa Telecom", "TW"),
        CarrierPreset("台湾大哥大", "Taiwan Mobile", "TW"),
        CarrierPreset("远传电信", "FarEasTone", "TW"),

        // 日本运营商
        CarrierPreset("NTT docomo", "NTT docomo", "JP"),
        CarrierPreset("au", "au by KDDI", "JP"),
        CarrierPreset("Softbank", "Softbank", "JP"),
        CarrierPreset("Rakuten", "Rakuten Mobile", "JP"),

        // 韩国运营商
        CarrierPreset("SK Telecom", "SK Telecom", "KR"),
        CarrierPreset("KT", "KT Corporation", "KR"),
        CarrierPreset("LG U+", "LG U+", "KR"),

        // 美国运营商
        CarrierPreset("AT&T", "AT&T", "US"),
        CarrierPreset("T-Mobile", "T-Mobile USA", "US"),
        CarrierPreset("Verizon", "Verizon", "US"),
        CarrierPreset("Sprint", "Sprint", "US"),

        // 英国运营商
        CarrierPreset("EE", "EE", "GB"),
        CarrierPreset("O2", "O2 UK", "GB"),
        CarrierPreset("Three", "Three UK", "GB"),
        CarrierPreset("Vodafone", "Vodafone UK", "GB"),

        // 新加坡运营商
        CarrierPreset("Singtel", "Singtel", "SG"),
        CarrierPreset("StarHub", "StarHub", "SG"),
        CarrierPreset("M1", "M1", "SG"),

        // 马来西亚运营商
        CarrierPreset("Maxis", "Maxis", "MY"),
        CarrierPreset("Celcom", "Celcom", "MY"),
        CarrierPreset("Digi", "Digi", "MY"),
        CarrierPreset("U Mobile", "U Mobile", "MY"),

        // 泰国运营商
        CarrierPreset("AIS", "AIS", "TH"),
        CarrierPreset("DTAC", "DTAC", "TH"),
        CarrierPreset("True Move H", "True Move H", "TH"),

        // 越南运营商
        CarrierPreset("Viettel", "Viettel Mobile", "VN"),
        CarrierPreset("Vinaphone", "Vinaphone", "VN"),
        CarrierPreset("Mobifone", "Mobifone", "VN"),

        // 印度尼西亚运营商
        CarrierPreset("Telkomsel", "Telkomsel", "ID"),
        CarrierPreset("Indosat", "Indosat Ooredoo", "ID"),
        CarrierPreset("XL Axiata", "XL Axiata", "ID"),

        // 菲律宾运营商
        CarrierPreset("Globe", "Globe Telecom", "PH"),
        CarrierPreset("Smart", "Smart Communications", "PH"),
        CarrierPreset("DITO", "DITO Telecommunity", "PH"),

        // 印度运营商
        CarrierPreset("Jio", "Reliance Jio", "IN"),
        CarrierPreset("Airtel", "Bharti Airtel", "IN"),
        CarrierPreset("Vi", "Vodafone Idea", "IN"),

        // 澳大利亚运营商
        CarrierPreset("Telstra", "Telstra", "AU"),
        CarrierPreset("Optus", "Optus", "AU"),
        CarrierPreset("Vodafone", "Vodafone AU", "AU"),

        // 加拿大运营商
        CarrierPreset("Bell", "Bell Mobility", "CA"),
        CarrierPreset("Rogers", "Rogers Wireless", "CA"),
        CarrierPreset("Telus", "Telus Mobility", "CA"),

        // 德国运营商
        CarrierPreset("Telekom", "T-Mobile DE", "DE"),
        CarrierPreset("Vodafone", "Vodafone DE", "DE"),
        CarrierPreset("O2", "O2 DE", "DE"),

        // 法国运营商
        CarrierPreset("Orange", "Orange FR", "FR"),
        CarrierPreset("SFR", "SFR", "FR"),
        CarrierPreset("Free", "Free Mobile", "FR"),
        CarrierPreset("Bouygues", "Bouygues Telecom", "FR"),

        // 意大利运营商
        CarrierPreset("TIM", "Telecom Italia", "IT"),
        CarrierPreset("Vodafone", "Vodafone IT", "IT"),
        CarrierPreset("Wind Tre", "Wind Tre", "IT"),

        // 西班牙运营商
        CarrierPreset("Movistar", "Movistar", "ES"),
        CarrierPreset("Vodafone", "Vodafone ES", "ES"),
        CarrierPreset("Orange", "Orange ES", "ES"),

        // 俄罗斯运营商
        CarrierPreset("MTS", "MTS", "RU"),
        CarrierPreset("MegaFon", "MegaFon", "RU"),
        CarrierPreset("Beeline", "Beeline", "RU"),

        // 土耳其运营商
        CarrierPreset("Turkcell", "Turkcell", "TR"),
        CarrierPreset("Vodafone", "Vodafone TR", "TR"),
        CarrierPreset("Türk Telekom", "Türk Telekom", "TR"),

        // 巴西运营商
        CarrierPreset("Vivo", "Vivo", "BR"),
        CarrierPreset("Claro", "Claro", "BR"),
        CarrierPreset("TIM", "TIM Brasil", "BR"),

        // 自定义选项
        CarrierPreset("自定义", "", "")
    )
} 