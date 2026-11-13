package com.example.grafik_smen.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.reflect.Type
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

import com.example.grafik_smen.model.AppTheme
import com.example.grafik_smen.model.CellShape
import com.example.grafik_smen.model.Colleague
import com.example.grafik_smen.model.Currency
import com.example.grafik_smen.model.PaymentType
import com.example.grafik_smen.model.Shift
import com.example.grafik_smen.model.ShiftTemplate

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("GrafikData", Context.MODE_PRIVATE)

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, object : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
            override fun serialize(src: LocalDate, typeOfSrc: Type, context: JsonSerializationContext) = JsonPrimitive(src.toString())
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalDate = LocalDate.parse(json.asString)
        })
        .registerTypeAdapter(LocalTime::class.java, object : JsonSerializer<LocalTime>, JsonDeserializer<LocalTime> {
            override fun serialize(src: LocalTime, typeOfSrc: Type, context: JsonSerializationContext) = JsonPrimitive(src.toString())
            override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LocalTime = LocalTime.parse(json.asString)
        })
        .create()

    // Флаг первого запуска (для обучения)
    var hasSeenTutorial by mutableStateOf(false); private set

    var currentTheme by mutableStateOf(AppTheme.DARK); private set
    var currentCellShape by mutableStateOf(CellShape.ROUNDED); private set
    var currentMonth by mutableStateOf(YearMonth.now()); private set
    var textScale by mutableFloatStateOf(1f); private set
    var selectedCurrency by mutableStateOf(Currency.RUB); private set

    // Храним премии отдельно для каждого месяца (ключ "YYYY-MM" -> сумма)
    var monthlyBonuses by mutableStateOf(emptyMap<String, Double>()); private set

    // Возвращает премию только для текущего открытого в календаре месяца
    val monthlyBonus: Double
        get() = monthlyBonuses[currentMonth.toString()] ?: 0.0

    var hourlyRate by mutableDoubleStateOf(250.0); private set

    var baseColor by mutableLongStateOf(0xFF3F51B5); private set
    var tipsColor by mutableLongStateOf(0xFF4CAF50); private set
    var bonusColor by mutableLongStateOf(0xFFFFC107); private set

    var liveRates by mutableStateOf(emptyMap<String, Double>()); private set

    var colleagues by mutableStateOf(emptyList<Colleague>()); private set
    var shifts by mutableStateOf(emptyMap<LocalDate, Shift>()); private set
    var templates by mutableStateOf(emptyList<ShiftTemplate>()); private set

    var monthBackgrounds by mutableStateOf(emptyMap<String, String>()); private set

    init {
        loadData()
        fetchLiveCurrencyRates()
    }

    private fun fetchLiveCurrencyRates() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://open.er-api.com/v6/latest/RUB")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    if (json.optString("result") == "success") {
                        val ratesObj = json.getJSONObject("rates")
                        val map = mutableMapOf<String, Double>()
                        val keys = ratesObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            map[key] = ratesObj.getDouble(key)
                        }
                        withContext(Dispatchers.Main) { liveRates = map }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun getExchangeRate(curr: Currency = selectedCurrency): Double {
        if (curr == Currency.RUB) return 1.0
        val rateFromApi = liveRates[curr.name]
        if (rateFromApi != null && rateFromApi > 0.0) return 1.0 / rateFromApi
        return curr.rateToRub
    }

    private fun loadData() {
        hasSeenTutorial = prefs.getBoolean("hasSeenTutorial", false)
        hourlyRate = prefs.getFloat("hourlyRate", 250f).toDouble()
        textScale = prefs.getFloat("textScale", 1f)
        currentTheme = AppTheme.valueOf(prefs.getString("theme", AppTheme.DARK.name) ?: AppTheme.DARK.name)
        currentCellShape = CellShape.valueOf(prefs.getString("cellShape", CellShape.ROUNDED.name) ?: CellShape.ROUNDED.name)
        selectedCurrency = Currency.valueOf(prefs.getString("currency", Currency.RUB.name) ?: Currency.RUB.name)

        baseColor = prefs.getLong("baseColor", 0xFF3F51B5)
        tipsColor = prefs.getLong("tipsColor", 0xFF4CAF50)
        bonusColor = prefs.getLong("bonusColor", 0xFFFFC107)

        // Загружаем премии по месяцам
        val bonusesJson = prefs.getString("monthlyBonuses", null)
        if (bonusesJson != null) {
            try {
                val jsonMap = JSONObject(bonusesJson)
                val map = mutableMapOf<String, Double>()
                val keys = jsonMap.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonMap.getDouble(key)
                }
                monthlyBonuses = map
            } catch (_: Exception) {}
        }

        val colJson = prefs.getString("colleagues", null)
        if (colJson != null) {
            val arr = gson.fromJson(colJson, Array<Colleague>::class.java)
            if (arr != null) colleagues = arr.toList()
        }

        val tplJson = prefs.getString("templates", null)
        if (tplJson != null) {
            val arr = gson.fromJson(tplJson, Array<ShiftTemplate>::class.java)
            if (arr != null) templates = arr.toList()
        } else {
            templates = listOf(
                ShiftTemplate(name = "Утро", startTime = LocalTime.of(11, 0), endTime = LocalTime.of(22, 0), colorArgb = 0xFFFFA000, paymentType = PaymentType.HOURLY),
                ShiftTemplate(name = "День", startTime = LocalTime.of(14, 0), endTime = LocalTime.of(23, 0), colorArgb = 0xFF388E3C, paymentType = PaymentType.HOURLY),
                ShiftTemplate(name = "Вечер", startTime = LocalTime.of(16, 0), endTime = LocalTime.of(0, 0), colorArgb = 0xFF1976D2, paymentType = PaymentType.HOURLY)
            )
        }

        val shiftsJson = prefs.getString("shifts", null)
        if (shiftsJson != null) {
            val arr = gson.fromJson(shiftsJson, Array<ShiftPair>::class.java)
            if (arr != null) shifts = arr.associate { LocalDate.parse(it.date) to it.shift }
        }

        val bgJson = prefs.getString("monthBackgrounds", null)
        if (bgJson != null) {
            try {
                val jsonMap = JSONObject(bgJson)
                val map = mutableMapOf<String, String>()
                val keys = jsonMap.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonMap.getString(key)
                }
                monthBackgrounds = map
            } catch (_: Exception) {}
        }
    }

    private fun saveData() {
        val listForSave = shifts.map { ShiftPair(it.key.toString(), it.value) }
        prefs.edit().apply {
            putFloat("hourlyRate", hourlyRate.toFloat())
            putString("monthlyBonuses", gson.toJson(monthlyBonuses))
            putFloat("textScale", textScale)
            putString("theme", currentTheme.name)
            putString("cellShape", currentCellShape.name)
            putString("currency", selectedCurrency.name)
            putLong("baseColor", baseColor)
            putLong("tipsColor", tipsColor)
            putLong("bonusColor", bonusColor)
            putString("colleagues", gson.toJson(colleagues))
            putString("templates", gson.toJson(templates))
            putString("shifts", gson.toJson(listForSave))
            putString("monthBackgrounds", gson.toJson(monthBackgrounds))
            apply()
        }
    }

    fun completeTutorial() {
        hasSeenTutorial = true
        prefs.edit().putBoolean("hasSeenTutorial", true).apply()
    }

    fun changeTheme(theme: AppTheme) { currentTheme = theme; saveData() }
    fun changeCellShape(shape: CellShape) { currentCellShape = shape; saveData() }
    fun changeMonth(offset: Long) { currentMonth = currentMonth.plusMonths(offset) }
    fun setMonth(yearMonth: YearMonth) { currentMonth = yearMonth }
    fun updateTextScale(scale: Float) { textScale = scale; saveData() }
    fun changeCurrency(curr: Currency) { selectedCurrency = curr; saveData() }
    fun updateHourlyRate(newRate: Double) { hourlyRate = newRate * getExchangeRate(); saveData() }

    // Сохраняет премию строго для текущего выбранного месяца
    fun updateMonthlyBonus(bonus: Double) {
        val map = monthlyBonuses.toMutableMap()
        val rawBonus = bonus * getExchangeRate()
        if (rawBonus <= 0.0) {
            map.remove(currentMonth.toString())
        } else {
            map[currentMonth.toString()] = rawBonus
        }
        monthlyBonuses = map
        saveData()
    }

    fun updateChartColors(newBase: Long, newTips: Long, newBonus: Long) { baseColor = newBase; tipsColor = newTips; bonusColor = newBonus; saveData() }

    fun updateMonthBackground(month: YearMonth, uriString: String?) {
        val map = monthBackgrounds.toMutableMap()
        if (uriString.isNullOrEmpty()) map.remove(month.toString())
        else map[month.toString()] = uriString
        monthBackgrounds = map
        saveData()
    }

    fun addColleague(name: String, role: String) {
        if (name.isNotBlank() && role.isNotBlank()) { colleagues = colleagues + Colleague(name = name, role = role); saveData() }
    }
    fun removeColleague(id: String) { colleagues = colleagues.filter { it.id != id }; saveData() }

    fun saveTemplate(id: String?, name: String, startH: Int, startM: Int, endH: Int, endM: Int, color: Long, pt: PaymentType, isNight: Boolean, isHol: Boolean, nightMultiplier: Double = 1.5, holidayMultiplier: Double = 2.0) {
        val nt = ShiftTemplate(id = id ?: java.util.UUID.randomUUID().toString(), name = name, startTime = LocalTime.of(startH, startM), endTime = LocalTime.of(endH, endM), colorArgb = color, paymentType = pt, isNightShift = isNight, isHoliday = isHol, nightMultiplier = nightMultiplier, holidayMultiplier = holidayMultiplier)
        templates = if (id != null && templates.any { it.id == id }) templates.map { if (it.id == id) nt else it } else templates + nt
        saveData()
    }

    fun removeTemplate(id: String) { templates = templates.filter { it.id != id }; saveData() }

    fun saveShiftsForDates(dates: Set<LocalDate>, shiftData: Shift) {
        val map = shifts.toMutableMap()
        for (date in dates) map[date] = shiftData.copy(id = java.util.UUID.randomUUID().toString(), date = date)
        shifts = map
        saveData()
    }

    fun deleteShift(date: LocalDate) {
        val map = shifts.toMutableMap()
        map.remove(date)
        shifts = map
        saveData()
    }

    fun getMonthlyStats(): Pair<Double, Double> {
        val monthShifts = shifts.filterKeys { YearMonth.from(it) == currentMonth }.values
        val totalHours = monthShifts.sumOf { it.hoursWorked }
        val currentMonthBonus = monthlyBonuses[currentMonth.toString()] ?: 0.0
        val rawMoney = monthShifts.sumOf { it.calculateEarnings(hourlyRate) } + currentMonthBonus
        return Pair(totalHours, rawMoney / getExchangeRate(selectedCurrency))
    }

    fun getSalaryBreakdown(): Triple<Double, Double, Double> {
        val monthShifts = shifts.filterKeys { YearMonth.from(it) == currentMonth }.values
        val base = monthShifts.sumOf {
            when (it.paymentType) {
                PaymentType.HOURLY -> {
                    var rate = hourlyRate
                    if (it.isHoliday) rate *= it.holidayMultiplier else if (it.isNightShift) rate *= it.nightMultiplier
                    it.hoursWorked * rate
                }
                PaymentType.FIXED -> it.fixedPaymentAmount
                PaymentType.NONE -> 0.0
            }
        }
        val rate = getExchangeRate(selectedCurrency)
        val currentMonthBonus = monthlyBonuses[currentMonth.toString()] ?: 0.0
        return Triple(base / rate, monthShifts.sumOf { it.tips } / rate, currentMonthBonus / rate)
    }
}

data class ShiftPair(val date: String, val shift: Shift)