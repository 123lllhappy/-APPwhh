package com.example.wohenhao.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class WeatherInfo(
    val condition: String,  // sunny, cloudy, rainy, snowy
    val temperature: Int,
    val description: String
)

class WeatherHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "WeatherHelper"
        // 使用免费的OpenWeatherMap API（需要替换为实际API key）
        private const val API_KEY = "your_api_key_here"
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
        // 天气缓存：key=城市名，value=[WeatherInfo, timestamp]
        private val weatherCache = mutableMapOf<String, Pair<WeatherInfo, Long>>()
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 缓存30分钟
    }
    
    suspend fun getCurrentWeather(city: String = "Beijing"): WeatherInfo? {
        return withContext(Dispatchers.IO) {
            // 优先用缓存（30分钟内不重复请求）
            val cached = weatherCache[city]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_DURATION_MS) {
                Log.d(TAG, "Using cached weather for $city")
                return@withContext cached.first
            }
            
            try {
                val url = URL("$BASE_URL?q=$city&appid=$API_KEY&units=metric&lang=zh_cn")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val weather = parseWeatherResponse(response)
                    weatherCache[city] = weather to System.currentTimeMillis()
                    weather
                } else {
                    Log.e(TAG, "Weather API error: ${connection.responseCode}")
                    getDefaultWeather()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get weather", e)
                getDefaultWeather()
            }
        }
    }
    
    private fun parseWeatherResponse(response: String): WeatherInfo {
        val json = JSONObject(response)
        val weather = json.getJSONArray("weather").getJSONObject(0)
        val main = json.getJSONObject("main")
        
        val condition = when (weather.getString("main").lowercase()) {
            "clear" -> "sunny"
            "clouds" -> "cloudy"
            "rain", "drizzle" -> "rainy"
            "snow" -> "snowy"
            "thunderstorm" -> "rainy"
            else -> "cloudy"
        }
        
        return WeatherInfo(
            condition = condition,
            temperature = main.getInt("temp"),
            description = weather.getString("description")
        )
    }
    
    private fun getDefaultWeather(): WeatherInfo {
        return WeatherInfo(
            condition = "sunny",
            temperature = 20,
            description = "晴朗"
        )
    }
}