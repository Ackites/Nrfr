package com.github.nrfr.data

object CountryPresets {
    data class CountryInfo(
        val code: String,
        val name: String
    )

    val countries = listOf(
        CountryInfo("CN", "中国"),
        CountryInfo("HK", "中国香港"),
        CountryInfo("MO", "中国澳门"),
        CountryInfo("TW", "中国台湾"),
        CountryInfo("JP", "日本"),
        CountryInfo("KR", "韩国"),
        CountryInfo("US", "美国"),
        CountryInfo("GB", "英国"),
        CountryInfo("DE", "德国"),
        CountryInfo("FR", "法国"),
        CountryInfo("IT", "意大利"),
        CountryInfo("ES", "西班牙"),
        CountryInfo("PT", "葡萄牙"),
        CountryInfo("RU", "俄罗斯"),
        CountryInfo("IN", "印度"),
        CountryInfo("AU", "澳大利亚"),
        CountryInfo("NZ", "新西兰"),
        CountryInfo("SG", "新加坡"),
        CountryInfo("MY", "马来西亚"),
        CountryInfo("TH", "泰国"),
        CountryInfo("VN", "越南"),
        CountryInfo("ID", "印度尼西亚"),
        CountryInfo("PH", "菲律宾"),
        CountryInfo("CA", "加拿大"),
        CountryInfo("MX", "墨西哥"),
        CountryInfo("BR", "巴西"),
        CountryInfo("AR", "阿根廷"),
        CountryInfo("ZA", "南非"),
        CountryInfo("TR", "土耳其")
    ).sortedBy { it.name }  // 按国家名称排序
} 