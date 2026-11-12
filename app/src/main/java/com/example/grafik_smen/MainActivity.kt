package com.example.grafik_smen

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShiftTrackerApp()
        }
    }
}

enum class AppTheme { LIGHT, DARK, SOFT_BLUE, SOFT_PINK }

data class ShiftData(
    val startTimeMin: Int = 0,
    val endTimeMin: Int = 0,
    val pay: Double = 0.0,
    val tips: Double = 0.0
) {
    val hours: Float
        get() {
            val diff = if (endTimeMin >= startTimeMin) endTimeMin - startTimeMin else (24 * 60 - startTimeMin) + endTimeMin
            return diff / 60f
        }
    val total: Double get() = pay + tips
}

class MutableSticker(
    val id: Long = System.currentTimeMillis(),
    val emoji: String,
    initialX: Float = 300f,
    initialY: Float = 500f,
    initialScale: Float = 1.5f,
    initialRotation: Float = 0f
) {
    var x by mutableStateOf(initialX)
    var y by mutableStateOf(initialY)
    var scale by mutableStateOf(initialScale)
    var rotation by mutableStateOf(initialRotation)
}

fun saveToPrefs(context: Context, shifts: Map<LocalDate, ShiftData>, rate: Float, stickers: Map<YearMonth, List<MutableSticker>>) {
    val prefs = context.getSharedPreferences("ShiftPrefs", Context.MODE_PRIVATE)

    val shiftsString = shifts.entries.joinToString(";") { (date, data) ->
        "$date,${data.startTimeMin},${data.endTimeMin},${data.pay},${data.tips}"
    }

    val stickersString = stickers.entries.joinToString("||") { (ym, list) ->
        val listString = list.joinToString(";") { "${it.emoji},${it.x},${it.y},${it.scale},${it.rotation}" }
        "$ym|$listString"
    }

    prefs.edit()
        .putString("shifts_data", shiftsString)
        .putFloat("hourly_rate", rate)
        .putString("stickers_data", stickersString)
        .apply()
}

fun loadShifts(context: Context): MutableMap<LocalDate, ShiftData> {
    val prefs = context.getSharedPreferences("ShiftPrefs", Context.MODE_PRIVATE)
    val data = prefs.getString("shifts_data", "") ?: ""
    val map = mutableMapOf<LocalDate, ShiftData>()
    if (data.isNotBlank()) {
        data.split(";").forEach { entry ->
            val parts = entry.split(",")
            if (parts.size == 5) {
                try { map[LocalDate.parse(parts[0])] = ShiftData(parts[1].toInt(), parts[2].toInt(), parts[3].toDouble(), parts[4].toDouble()) } catch (e: Exception) {}
            }
        }
    }
    return map
}

fun loadStickers(context: Context): MutableMap<YearMonth, MutableList<MutableSticker>> {
    val prefs = context.getSharedPreferences("ShiftPrefs", Context.MODE_PRIVATE)
    val data = prefs.getString("stickers_data", "") ?: ""
    val map = mutableMapOf<YearMonth, MutableList<MutableSticker>>()
    if (data.isNotBlank()) {
        data.split("||").forEach { monthBlock ->
            val parts = monthBlock.split("|")
            if (parts.size == 2) {
                val ym = YearMonth.parse(parts[0])
                val list = mutableListOf<MutableSticker>()
                parts[1].split(";").forEach { stickerData ->
                    val sParts = stickerData.split(",")
                    if (sParts.size == 5) {
                        try {
                            list.add(MutableSticker(System.currentTimeMillis(), sParts[0], sParts[1].toFloat(), sParts[2].toFloat(), sParts[3].toFloat(), sParts[4].toFloat()))
                        } catch (e: Exception) {}
                    }
                }
                map[ym] = list
            }
        }
    }
    return map
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftTrackerApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentTheme by remember { mutableStateOf(AppTheme.SOFT_PINK) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.of(2026, 10)) }

    val shifts = remember { mutableStateMapOf<LocalDate, ShiftData>() }
    val stickersMap = remember { mutableStateMapOf<YearMonth, MutableList<MutableSticker>>() }
    var hourlyRate by remember { mutableStateOf(0f) }
    var isLoaded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                saveToPrefs(context, shifts, hourlyRate, stickersMap)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        shifts.putAll(loadShifts(context))
        hourlyRate = context.getSharedPreferences("ShiftPrefs", Context.MODE_PRIVATE).getFloat("hourly_rate", 0f)
        stickersMap.putAll(loadStickers(context))
        isLoaded = true
    }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showRateDialog by remember { mutableStateOf(false) }
    var showStickerDialog by remember { mutableStateOf(false) }
    var editingSticker by remember { mutableStateOf<MutableSticker?>(null) }

    val backgroundColor = when (currentTheme) {
        AppTheme.LIGHT -> Color(0xFFF5F5F5)
        AppTheme.DARK -> Color(0xFF121212)
        AppTheme.SOFT_BLUE -> Color(0xFFE3F2FD)
        AppTheme.SOFT_PINK -> Color(0xFFF8BBD0)
    }
    val textColor = if (currentTheme == AppTheme.DARK) Color.White else Color(0xFF212121)
    val cardBgColor = if (currentTheme == AppTheme.DARK) Color(0xFF1E1E1E) else Color.White
    val cellBgColor = if (currentTheme == AppTheme.DARK) Color(0xFF2C2C2C) else Color(0xFFFFF0F5)
    val accentColor = when (currentTheme) {
        AppTheme.LIGHT -> Color(0xFF6200EE)
        AppTheme.DARK -> Color(0xFFBB86FC)
        AppTheme.SOFT_BLUE -> Color(0xFF1976D2)
        AppTheme.SOFT_PINK -> Color(0xFF8B4513)
    }

    val totalHoursAll = shifts.values.sumOf { it.hours.toDouble() }
    val totalMoneyAll = shifts.values.sumOf { it.total }

    if (!isLoaded) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("График смен", fontWeight = FontWeight.Bold, color = textColor) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    IconButton(onClick = { showStickerDialog = true }) {
                        Text("🌟", fontSize = 24.sp)
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Text("⋮", fontSize = 28.sp, color = textColor, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Установить ставку/час", fontWeight = FontWeight.Bold) }, onClick = { showRateDialog = true; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Очистить стикеры", color = Color.Red) }, onClick = { stickersMap[currentYearMonth]?.clear(); menuExpanded = false })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Светлая тема") }, onClick = { currentTheme = AppTheme.LIGHT; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Темная тема") }, onClick = { currentTheme = AppTheme.DARK; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Голубая тема") }, onClick = { currentTheme = AppTheme.SOFT_BLUE; menuExpanded = false })
                        DropdownMenuItem(text = { Text("Розовая тема") }, onClick = { currentTheme = AppTheme.SOFT_PINK; menuExpanded = false })
                    }
                }
            )
        },
        containerColor = backgroundColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { currentYearMonth = currentYearMonth.minusMonths(1) }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("←", fontSize = 18.sp) }
                    Text("${getMonthName(currentYearMonth.monthValue)} ${currentYearMonth.year}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Button(onClick = { currentYearMonth = currentYearMonth.plusMonths(1) }, colors = ButtonDefaults.buttonColors(containerColor = accentColor)) { Text("→", fontSize = 18.sp) }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { dayName ->
                        Text(text = dayName, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(getMonthDays(currentYearMonth)) { date ->
                        if (date != null) {
                            val shift = shifts[date]
                            val isWorked = shift != null && shift.hours > 0f

                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { selectedDate = date },
                                colors = CardDefaults.cardColors(containerColor = if (isWorked) accentColor.copy(alpha = 0.3f) else cellBgColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(text = "${date.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    if (isWorked) {
                                        Text(text = "${String.format("%.1f", shift!!.hours)}ч", fontSize = 11.sp, color = textColor)
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(cardBgColor, shape = RoundedCornerShape(12.dp)).padding(12.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Всего часов: ${String.format("%.1f", totalHoursAll)} ч", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("Общая з/п: ${String.format("%.0f", totalMoneyAll)} руб", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
                }
            }

            val currentStickers = stickersMap[currentYearMonth] ?: mutableListOf()
            currentStickers.forEach { sticker ->

                val resId = context.resources.getIdentifier(sticker.emoji, "drawable", context.packageName)

                val modifier = Modifier
                    .offset { IntOffset(sticker.x.roundToInt(), sticker.y.roundToInt()) }
                    .pointerInput(sticker.id) {
                        detectTapGestures(onTap = { editingSticker = sticker })
                    }
                    .pointerInput(sticker.id + 1L) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            sticker.x += dragAmount.x
                            sticker.y += dragAmount.y
                        }
                    }
                    .graphicsLayer {
                        scaleX = sticker.scale
                        scaleY = sticker.scale
                        rotationZ = sticker.rotation
                    }

                if (resId != 0) {
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = null,
                        modifier = modifier.size(100.dp)
                    )
                } else {
                    Text(
                        text = sticker.emoji,
                        fontSize = 36.sp,
                        modifier = modifier
                    )
                }
            }
        }

        if (editingSticker != null) {
            AlertDialog(
                onDismissRequest = { editingSticker = null },
                title = { Text("Настройка стикера", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Размер", fontWeight = FontWeight.Bold)
                        Slider(
                            value = editingSticker!!.scale,
                            onValueChange = { editingSticker!!.scale = it },
                            valueRange = 0.1f..8f
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Угол наклона", fontWeight = FontWeight.Bold)
                        Slider(
                            value = editingSticker!!.rotation,
                            onValueChange = { editingSticker!!.rotation = it },
                            valueRange = 0f..360f
                        )
                    }
                },
                confirmButton = { Button(onClick = { editingSticker = null }) { Text("Готово") } },
                dismissButton = {
                    TextButton(onClick = {
                        stickersMap[currentYearMonth]?.remove(editingSticker)
                        editingSticker = null
                    }) { Text("Удалить", color = Color.Red) }
                }
            )
        }

        if (showStickerDialog) {

            // Здесь добавлены твои картинки: meme_1 и meme_2
            val myStickersLibrary = listOf(
                "☕️", "🍕", "💰", "😴", "🎉", "🚀", "🌟", "🎃",
                "meme_1", "meme_2", "meme_3","meme_4"
            )

            AlertDialog(
                onDismissRequest = { showStickerDialog = false },
                title = { Text("Выберите стикер") },
                text = {
                    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                        items(myStickersLibrary) { item ->

                            val resId = context.resources.getIdentifier(item, "drawable", context.packageName)

                            if (resId != 0) {
                                Image(
                                    painter = painterResource(id = resId),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .padding(8.dp)
                                        .clickable {
                                            if (stickersMap[currentYearMonth] == null) stickersMap[currentYearMonth] = mutableListOf()
                                            stickersMap[currentYearMonth]?.add(MutableSticker(emoji = item))
                                            showStickerDialog = false
                                        }
                                )
                            } else {
                                Text(
                                    text = item,
                                    fontSize = 36.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .clickable {
                                            if (stickersMap[currentYearMonth] == null) stickersMap[currentYearMonth] = mutableListOf()
                                            stickersMap[currentYearMonth]?.add(MutableSticker(emoji = item))
                                            showStickerDialog = false
                                        }
                                )
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showStickerDialog = false }) { Text("Закрыть") } }
            )
        }

        if (showRateDialog) {
            var tempRate by remember { mutableStateOf(if (hourlyRate > 0) hourlyRate.toString() else "") }
            AlertDialog(onDismissRequest = { showRateDialog = false }, title = { Text("Настройки", fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(value = tempRate, onValueChange = { tempRate = it }, label = { Text("Ставка в час (руб)") }, singleLine = true) }, confirmButton = { Button(onClick = { hourlyRate = tempRate.toFloatOrNull() ?: 0f; showRateDialog = false }) { Text("Сохранить") } })
        }

        if (selectedDate != null) {
            val existingShift = shifts[selectedDate]
            var startTimeMin by remember { mutableStateOf(existingShift?.startTimeMin ?: (9 * 60)) }
            var endTimeMin by remember { mutableStateOf(existingShift?.endTimeMin ?: (18 * 60)) }
            var showStartPicker by remember { mutableStateOf(false) }
            var showEndPicker by remember { mutableStateOf(false) }
            val diff = if (endTimeMin >= startTimeMin) endTimeMin - startTimeMin else (24 * 60 - startTimeMin) + endTimeMin
            val hoursWorked = diff / 60f
            var inputPay by remember(startTimeMin, endTimeMin, hourlyRate) { mutableStateOf(if (existingShift != null && existingShift.startTimeMin == startTimeMin && existingShift.endTimeMin == endTimeMin && existingShift.pay > 0) existingShift.pay.toString() else if (hourlyRate > 0) (hoursWorked * hourlyRate).toString() else "") }
            var inputTips by remember { mutableStateOf(if (existingShift?.tips != null && existingShift.tips > 0) existingShift.tips.toString() else "") }

            AlertDialog(
                onDismissRequest = { selectedDate = null },
                title = { Text("Смена: $selectedDate", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            OutlinedButton(onClick = { showStartPicker = true }) { Text("Начало:\n${formatTime(startTimeMin)}", textAlign = TextAlign.Center) }
                            OutlinedButton(onClick = { showEndPicker = true }) { Text("Конец:\n${formatTime(endTimeMin)}", textAlign = TextAlign.Center) }
                        }
                        Text("Отработано: ${String.format("%.1f", hoursWorked)} часов", color = accentColor, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = inputPay, onValueChange = { inputPay = it }, label = { Text("Зарплата за смену (руб)") }, singleLine = true)
                        OutlinedTextField(value = inputTips, onValueChange = { inputTips = it }, label = { Text("Чаевые (руб)") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { val pay = inputPay.toDoubleOrNull() ?: 0.0; val tips = inputTips.toDoubleOrNull() ?: 0.0; if (hoursWorked > 0 || pay > 0 || tips > 0) shifts[selectedDate!!] = ShiftData(startTimeMin, endTimeMin, pay, tips) else shifts.remove(selectedDate!!); selectedDate = null }) { Text("Сохранить") } },
                dismissButton = { TextButton(onClick = { shifts.remove(selectedDate!!); selectedDate = null }) { Text("Удалить", color = Color.Red) } }
            )

            if (showStartPicker) {
                val timePickerState = rememberTimePickerState(initialHour = startTimeMin / 60, initialMinute = startTimeMin % 60, is24Hour = true)
                AlertDialog(onDismissRequest = { showStartPicker = false }, text = { TimePicker(state = timePickerState) }, confirmButton = { TextButton(onClick = { startTimeMin = timePickerState.hour * 60 + timePickerState.minute; showStartPicker = false }) { Text("ОК") } })
            }
            if (showEndPicker) {
                val timePickerState = rememberTimePickerState(initialHour = endTimeMin / 60, initialMinute = endTimeMin % 60, is24Hour = true)
                AlertDialog(onDismissRequest = { showEndPicker = false }, text = { TimePicker(state = timePickerState) }, confirmButton = { TextButton(onClick = { endTimeMin = timePickerState.hour * 60 + timePickerState.minute; showEndPicker = false }) { Text("ОК") } })
            }
        }
    }
}

fun formatTime(minutes: Int) = String.format("%02d:%02d", minutes / 60, minutes % 60)
fun getMonthDays(yearMonth: YearMonth): List<LocalDate?> {
    val firstDayOfMonth = yearMonth.atDay(1)
    val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value
    val daysInMonth = yearMonth.lengthOfMonth()
    val list = mutableListOf<LocalDate?>()
    for (i in 1 until dayOfWeekValue) list.add(null)
    for (day in 1..daysInMonth) list.add(yearMonth.atDay(day))
    return list
}
fun getMonthName(month: Int) = when (month) {
    1 -> "Январь"; 2 -> "Февраль"; 3 -> "Март"; 4 -> "Апрель"; 5 -> "Май"; 6 -> "Июнь"; 7 -> "Июль"; 8 -> "Август"; 9 -> "Сентябрь"; 10 -> "Октябрь"; 11 -> "Ноябрь"; 12 -> "Декабрь"
    else -> ""
}