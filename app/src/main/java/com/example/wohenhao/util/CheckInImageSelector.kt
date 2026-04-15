package com.example.wohenhao.util

import android.content.Context
import com.example.wohenhao.R

data class CheckInImage(
    val resourceId: Int,
    val title: String,
    val subtitle: String,
    val type: String // "weather", "solar_term", "season"
)

class CheckInImageSelector(private val context: Context) {
    
    private val weatherHelper = WeatherHelper(context)
    private val solarTermCalculator = SolarTermCalculator()
    
    suspend fun selectTodayImage(): CheckInImage {
        val solarTerm = solarTermCalculator.getCurrentSolarTerm()
        val weather = weatherHelper.getCurrentWeather()
        
        return when {
            // 优先级1: 特殊节气
            solarTerm.isSpecial -> {
                CheckInImage(
                    resourceId = getSolarTermImageResource(solarTerm.englishName),
                    title = "今日${solarTerm.name}",
                    subtitle = getSolarTermDescription(solarTerm.name),
                    type = "solar_term"
                )
            }
            // 优先级2: 天气 + 当前节气
            weather != null -> {
                CheckInImage(
                    resourceId = getWeatherImageResource(weather.condition, solarTerm.season),
                    title = "${weather.description}·${solarTerm.name}",
                    subtitle = "今日已平安打卡，${weather.temperature}°C",
                    type = "weather"
                )
            }
            // 优先级3: 普通节气
            else -> {
                CheckInImage(
                    resourceId = getSolarTermImageResource(solarTerm.englishName),
                    title = solarTerm.name,
                    subtitle = getSolarTermDescription(solarTerm.name),
                    type = "solar_term"
                )
            }
        }
    }
    
    private fun getSolarTermImageResource(englishName: String): Int {
        // 根据节气英文名获取图片资源
        val resourceName = "solar_term_$englishName"
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (resourceId != 0) resourceId else getDefaultSeasonImage()
    }
    
    private fun getWeatherImageResource(condition: String, season: String): Int {
        // 根据天气和季节获取图片资源
        val resourceName = "weather_${condition}_$season"
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (resourceId != 0) {
            resourceId
        } else {
            // 备用：只根据天气
            val fallbackName = "weather_$condition"
            val fallbackId = context.resources.getIdentifier(fallbackName, "drawable", context.packageName)
            if (fallbackId != 0) fallbackId else getDefaultSeasonImage()
        }
    }
    
    private fun getDefaultSeasonImage(): Int {
        val season = solarTermCalculator.getSeason()
        val resourceName = "season_$season"
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        return if (resourceId != 0) resourceId else R.drawable.ic_launcher_foreground
    }
    
    private fun getSolarTermDescription(termName: String): String {
        return when (termName) {
            "立春" -> "春回大地，万物复苏"
            "雨水" -> "春雨润物，生机盎然"
            "惊蛰" -> "春雷响动，万物萌发"
            "春分" -> "昼夜平分，春意正浓"
            "清明" -> "天清地明，踏青时节"
            "谷雨" -> "雨生百谷，春意盎然"
            "立夏" -> "夏日初临，绿意正浓"
            "小满" -> "麦穗渐满，夏意渐浓"
            "芒种" -> "麦收时节，忙碌充实"
            "夏至" -> "白昼最长，夏意正浓"
            "小暑" -> "暑热初临，清凉自在"
            "大暑" -> "酷暑时节，注意防暑"
            "立秋" -> "秋意初现，凉风习习"
            "处暑" -> "暑气渐消，秋高气爽"
            "白露" -> "露珠晶莹，秋意渐浓"
            "秋分" -> "昼夜平分，秋意正浓"
            "寒露" -> "露水渐寒，秋意深浓"
            "霜降" -> "霜叶满山，秋意正浓"
            "立冬" -> "冬日初临，注意保暖"
            "小雪" -> "雪花初降，冬意渐浓"
            "大雪" -> "雪花纷飞，银装素裹"
            "冬至" -> "白昼最短，冬意正浓"
            "小寒" -> "寒意渐浓，注意保暖"
            "大寒" -> "严寒时节，温暖如春"
            else -> "今日已平安打卡"
        }
    }
}