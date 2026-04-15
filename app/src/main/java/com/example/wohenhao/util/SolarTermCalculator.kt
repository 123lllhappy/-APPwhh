package com.example.wohenhao.util

import java.util.*

data class SolarTerm(
    val name: String,
    val englishName: String,
    val season: String,
    val isSpecial: Boolean = false
)

class SolarTermCalculator {
    
    companion object {
        private val SOLAR_TERMS = arrayOf(
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
            "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
            "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
            "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
        )
        
        private val SOLAR_TERMS_EN = arrayOf(
            "xiaohan", "dahan", "lichun", "yushui", "jingzhe", "chunfen",
            "qingming", "guyu", "lixia", "xiaoman", "mangzhong", "xiazhi",
            "xiaoshu", "dashu", "liqiu", "chushu", "bailu", "qiufen",
            "hanlu", "shuangjiang", "lidong", "xiaoxue", "daxue", "dongzhi"
        )
        
        // 特殊节气（二分二至和四立）
        private val SPECIAL_TERMS = setOf("春分", "夏至", "秋分", "冬至", "立春", "立夏", "立秋", "立冬")
    }
    
    fun getCurrentSolarTerm(): SolarTerm {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        
        // 简化的节气计算（基于日期估算）
        val termIndex = calculateSolarTermIndex(year, dayOfYear)
        val termName = SOLAR_TERMS[termIndex]
        val termNameEn = SOLAR_TERMS_EN[termIndex]
        
        val season = when (termIndex) {
            in 2..7 -> "spring"   // 立春到谷雨
            in 8..13 -> "summer"  // 立夏到大暑
            in 14..19 -> "autumn" // 立秋到霜降
            else -> "winter"      // 立冬到大寒
        }
        
        return SolarTerm(
            name = termName,
            englishName = termNameEn,
            season = season,
            isSpecial = SPECIAL_TERMS.contains(termName)
        )
    }
    
    private fun calculateSolarTermIndex(year: Int, dayOfYear: Int): Int {
        // 简化算法：每个节气约15天，从小寒开始
        // 小寒大约在1月5日（第5天）
        val adjustedDay = if (dayOfYear >= 5) dayOfYear - 5 else dayOfYear + 360
        val termIndex = (adjustedDay / 15) % 24
        return termIndex
    }
    
    fun getSeason(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "spring"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "summer"
            Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> "autumn"
            else -> "winter"
        }
    }
}