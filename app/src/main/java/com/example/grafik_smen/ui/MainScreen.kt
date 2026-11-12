package com.example.grafik_smen.ui

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

import com.example.grafik_smen.model.AppTheme
import com.example.grafik_smen.model.CellShape
import com.example.grafik_smen.model.Currency
import com.example.grafik_smen.model.PaymentType
import com.example.grafik_smen.model.Shift
import com.example.grafik_smen.model.ShiftTemplate
import com.example.grafik_smen.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val currentMonth = viewModel.currentMonth
    val shifts = viewModel.shifts
    val colleagues = viewModel.colleagues
    val templates = viewModel.templates
    val hourlyRate = viewModel.hourlyRate
    val currentTheme = viewModel.currentTheme
    val currentCellShape = viewModel.currentCellShape
    val textScale = viewModel.textScale
    val currency = viewModel.selectedCurrency
    val monthlyBonus = viewModel.monthlyBonus
    val (monthlyHours, monthlyEarned) = viewModel.getMonthlyStats()

    val s11 = (11 * textScale).sp
    val s12 = (12 * textScale).sp
    val s14 = (14 * textScale).sp
    val s16 = (16 * textScale).sp
    val s18 = (18 * textScale).sp
    val s20 = (20 * textScale).sp
    val s22 = (22 * textScale).sp
    val s28 = (28 * textScale).sp

    var currentTab by remember { mutableIntStateOf(0) }
    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    var showMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showShiftDialog by remember { mutableStateOf(false) }
    var templateToEdit by remember { mutableStateOf<ShiftTemplate?>(null) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showChartColorDialog by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showBgMenu by remember { mutableStateOf(false) }

    // Состояние обучения
    var showTutorialDialog by remember { mutableStateOf(!viewModel.hasSeenTutorial) }

    val bgColor = when (currentTheme) {
        AppTheme.LIGHT -> Color(0xFFFAFAFA)
        AppTheme.DARK -> Color(0xFF121212)
        AppTheme.SOFT_PINK -> Color(0xFFFFF0F5)
        AppTheme.SOFT_BLUE -> Color(0xFFF0F8FF)
        AppTheme.LIGHT_GRAY -> Color(0xFFF5F5F5)
        AppTheme.DARK_GRAY -> Color(0xFF2C2C2C)
    }
    val isDark = currentTheme == AppTheme.DARK || currentTheme == AppTheme.DARK_GRAY
    val textColor = if (isDark) Color(0xFFE0E0E0) else Color(0xFF212121)
    val primaryColor = if (isDark) Color(0xFFBB86FC) else Color(0xFF3F51B5)
    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color.White

    val appColorScheme = if (isDark) darkColorScheme(background = bgColor, surface = cardColor, onSurface = textColor, primary = primaryColor)
    else lightColorScheme(background = bgColor, surface = cardColor, onSurface = textColor, primary = primaryColor)

    val cellShapeObj = when (currentCellShape) {
        CellShape.ROUNDED -> RoundedCornerShape(12.dp)
        CellShape.CIRCLE -> CircleShape
        CellShape.SQUARE -> RectangleShape
    }

    val monthsRu = listOf("ЯНВАРЬ", "ФЕВРАЛЬ", "МАРТ", "АПРЕЛЬ", "МАЙ", "ИЮНЬ", "ИЮЛЬ", "АВГУСТ", "СЕНТЯБРЬ", "ОКТЯБРЬ", "НОЯБРЬ", "ДЕКАБРЬ")
    val shiftColors = listOf(0xFF3F51B5, 0xFFFFA000, 0xFF388E3C, 0xFF1976D2, 0xFF512DA8, 0xFFD32F2F, 0xFF0097A6, 0xFFC2185B, 0xFF9E9E9E)

    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            viewModel.updateMonthBackground(currentMonth, it.toString())
        }
    }

    MaterialTheme(colorScheme = appColorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("График смен", fontWeight = FontWeight.Bold, color = textColor, fontSize = s22) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                    actions = {
                        if (currentTab == 0) {
                            TextButton(onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedDates = emptySet()
                            }) {
                                Text(if (isMultiSelectMode) "Готово" else "Выделить", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = s16)
                            }
                        }
                        IconButton(onClick = { showMenu = true }) { Text("⋮", fontSize = s28, color = textColor, fontWeight = FontWeight.Bold) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(cardColor)) {
                            DropdownMenuItem(text = { Text("Настройки", color = textColor, fontSize = s16) }, onClick = { showSettingsDialog = true; showMenu = false })
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = cardColor, tonalElevation = 4.dp) {
                    NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Text("📅", fontSize = s20) }, label = { Text("Календарь", color = textColor, fontSize = s12) })
                    NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Text("📑", fontSize = s20) }, label = { Text("Смены", color = textColor, fontSize = s12) })
                    NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Text("💰", fontSize = s20) }, label = { Text("Зарплата", color = textColor, fontSize = s12) })
                    NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Text("👥", fontSize = s20) }, label = { Text("Коллеги", color = textColor, fontSize = s12) })
                }
            },
            floatingActionButton = {
                if (currentTab == 0 || currentTab == 1) {
                    FloatingActionButton(
                        onClick = {
                            if (currentTab == 0) {
                                if (selectedDates.isEmpty()) selectedDates = setOf(LocalDate.now())
                                showShiftDialog = true
                            } else {
                                templateToEdit = null
                                showTemplateDialog = true
                            }
                        },
                        modifier = Modifier.padding(bottom = 72.dp),
                        containerColor = primaryColor,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Text("+", fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.offset(y = (-2).dp))
                    }
                }
            },
            containerColor = bgColor
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                if (currentTab == 0) {
                    val bgUriString = viewModel.monthBackgrounds[currentMonth.toString()]
                    if (bgUriString != null) {
                        val bgBitmap = rememberUriBitmap(uriString = bgUriString)
                        if (bgBitmap != null) {
                            Image(
                                bitmap = bgBitmap,
                                contentDescription = "Фон месяца",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize().background(bgColor.copy(alpha = 0.65f)))
                        }
                    }
                }

                if (currentTab == 0) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.changeMonth(-1) }) { Text("◀", fontSize = s18, color = primaryColor) }

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showMonthPicker = true }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("${monthsRu[currentMonth.monthValue - 1]} ${currentMonth.year}", fontSize = s18, fontWeight = FontWeight.Black, color = textColor)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▼", fontSize = 12.sp, color = primaryColor)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    IconButton(onClick = { showBgMenu = true }) { Text("🖼️", fontSize = 20.sp) }
                                    DropdownMenu(expanded = showBgMenu, onDismissRequest = { showBgMenu = false }, modifier = Modifier.background(cardColor)) {
                                        DropdownMenuItem(text = { Text("Установить фон", color = textColor) }, onClick = { imagePickerLauncher.launch("image/*"); showBgMenu = false })
                                        if (viewModel.monthBackgrounds.containsKey(currentMonth.toString())) {
                                            DropdownMenuItem(text = { Text("Удалить фон", color = Color.Red) }, onClick = { viewModel.updateMonthBackground(currentMonth, null); showBgMenu = false })
                                        }
                                    }
                                }
                                IconButton(onClick = { viewModel.changeMonth(1) }) { Text("▶", fontSize = s18, color = primaryColor) }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { day ->
                                Text(text = day, fontWeight = FontWeight.Bold, color = if (day == "СБ" || day == "ВС") Color.Red.copy(alpha = 0.7f) else Color.Gray, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center, fontSize = s14)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(alpha = 0.5f))

                        val daysInMonth = currentMonth.lengthOfMonth()
                        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value
                        val calendarCells = List(firstDayOfWeek - 1) { null } + (1..daysInMonth).map { currentMonth.atDay(it) }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(calendarCells) { date ->
                                if (date != null) {
                                    val shift = shifts[date]
                                    val isToday = date == LocalDate.now()
                                    val isSelected = selectedDates.contains(date)
                                    val cellColor = if (shift != null) Color(shift.colorArgb) else cardColor.copy(alpha = if (viewModel.monthBackgrounds.containsKey(currentMonth.toString())) 0.8f else 1f)

                                    val dayTextColor = if (shift != null) {
                                        val argb = cellColor.toArgb()
                                        val luminance = (0.299 * AndroidColor.red(argb) + 0.587 * AndroidColor.green(argb) + 0.114 * AndroidColor.blue(argb))
                                        if (luminance > 160) Color(0xFF1F1F1F) else Color.White
                                    } else {
                                        if (isToday) primaryColor else textColor
                                    }

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(cellShapeObj)
                                            .background(cellColor)
                                            .border(width = if (isSelected) 3.dp else 0.dp, color = if (isSelected) primaryColor else Color.Transparent, shape = cellShapeObj)
                                            .clickable {
                                                if (isMultiSelectMode) {
                                                    selectedDates = if (isSelected) selectedDates - date else selectedDates + date
                                                } else {
                                                    selectedDates = setOf(date)
                                                    showShiftDialog = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            color = dayTextColor,
                                            fontWeight = if (shift != null || isToday) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = s16
                                        )
                                        if (shift != null && shift.colleaguesIds.isNotEmpty()) {
                                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(6.dp).clip(CircleShape).background(if (cellColor.toArgb().let { 0.299 * AndroidColor.red(it) + 0.587 * AndroidColor.green(it) + 0.114 * AndroidColor.blue(it) } > 160) Color.Black else Color.Yellow))
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.aspectRatio(1f))
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Отработано", fontSize = s14, color = Color.Gray)
                                    Text("${String.format(Locale.US, "%.1f", monthlyHours)} ч.", fontWeight = FontWeight.Bold, color = textColor, fontSize = s20)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Зарплата", fontSize = s14, color = Color.Gray)
                                    Text("${String.format(Locale.US, "%.1f", monthlyEarned)} ${currency.symbol}", fontWeight = FontWeight.Black, color = primaryColor, fontSize = s20)
                                }
                            }
                        }
                    }
                }
                else if (currentTab == 1) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates) { tpl ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        templateToEdit = tpl
                                        showTemplateDialog = true
                                    }
                            ) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(tpl.colorArgb)))
                                        Column {
                                            Text(tpl.name, fontWeight = FontWeight.Bold, color = textColor, fontSize = s18)
                                            when (tpl.paymentType) {
                                                PaymentType.HOURLY -> Text("${tpl.startTime} - ${tpl.endTime}", fontSize = s12, color = Color.Gray)
                                                PaymentType.FIXED -> Text("Фиксированная выплата", fontSize = s12, color = primaryColor)
                                                PaymentType.NONE -> Text("Неоплачиваемая", fontSize = s12, color = Color.Gray)
                                            }
                                        }
                                    }
                                    IconButton(onClick = { viewModel.removeTemplate(tpl.id) }) { Text("✕", color = Color.Red, fontSize = s16) }
                                }
                            }
                        }
                    }
                }
                else if (currentTab == 2) {
                    val currentRate = viewModel.getExchangeRate(currency)

                    var rateInput by remember(currency, viewModel.liveRates) {
                        val conv = hourlyRate / currentRate
                        mutableStateOf(if (conv % 1.0 == 0.0) String.format(Locale.US, "%.0f", conv) else String.format(Locale.US, "%.2f", conv))
                    }
                    var bonusInput by remember(currency, viewModel.liveRates) {
                        val conv = monthlyBonus / currentRate
                        mutableStateOf(if (conv % 1.0 == 0.0) String.format(Locale.US, "%.0f", conv) else String.format(Locale.US, "%.2f", conv))
                    }

                    val (baseSalary, tipsAmount, bonusAmount) = viewModel.getSalaryBreakdown()
                    val totalIncome = baseSalary + tipsAmount + bonusAmount

                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, primaryColor)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Итого за ${monthsRu[currentMonth.monthValue - 1].lowercase()}", color = textColor, fontSize = s16)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("${String.format(Locale.US, "%.1f", monthlyEarned)} ${currency.symbol}", fontWeight = FontWeight.Black, color = primaryColor, fontSize = s28)

                                    if (totalIncome > 0) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                            Canvas(modifier = Modifier.size(110.dp)) {
                                                val baseAngle = (baseSalary / totalIncome * 360f).toFloat()
                                                val tipsAngle = (tipsAmount / totalIncome * 360f).toFloat()
                                                val bonusAngle = (bonusAmount / totalIncome * 360f).toFloat()

                                                var start = -90f
                                                if (baseSalary > 0) { drawArc(Color(viewModel.baseColor), start, baseAngle, useCenter = true); start += baseAngle }
                                                if (tipsAmount > 0) { drawArc(Color(viewModel.tipsColor), start, tipsAngle, useCenter = true); start += tipsAngle }
                                                if (bonusAmount > 0) { drawArc(Color(viewModel.bonusColor), start, bonusAngle, useCenter = true) }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(viewModel.baseColor))); Spacer(modifier = Modifier.width(8.dp)); Text("Ставка: ${String.format(Locale.US, "%.1f", baseSalary)} ${currency.symbol}", fontSize = s14, color = textColor) }
                                                Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(viewModel.tipsColor))); Spacer(modifier = Modifier.width(8.dp)); Text("Чаевые: ${String.format(Locale.US, "%.1f", tipsAmount)} ${currency.symbol}", fontSize = s14, color = textColor) }
                                                Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color(viewModel.bonusColor))); Spacer(modifier = Modifier.width(8.dp)); Text("Премия: ${String.format(Locale.US, "%.1f", bonusAmount)} ${currency.symbol}", fontSize = s14, color = textColor) }
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { showChartColorDialog = true },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                ) {
                                    Text("✏️", fontSize = 18.sp)
                                }
                            }
                        }

                        Text("Настройки Зарплаты", fontWeight = FontWeight.Bold, color = textColor, fontSize = s18)

                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = {
                                rateInput = it
                                it.replace(",", ".").toDoubleOrNull()?.let { v -> viewModel.updateHourlyRate(v) }
                            },
                            label = { Text("Часовая ставка (${currency.symbol})", color = textColor, fontSize = s12) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16)
                        )

                        OutlinedTextField(
                            value = bonusInput,
                            onValueChange = {
                                bonusInput = it
                                it.replace(",", ".").toDoubleOrNull()?.let { v -> viewModel.updateMonthlyBonus(v) }
                            },
                            label = { Text("Общая премия за месяц (${currency.symbol})", color = textColor, fontSize = s12) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16)
                        )

                        Text("Валюта:", fontWeight = FontWeight.Bold, color = textColor, fontSize = s14)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Currency.entries.forEach { curr ->
                                OutlinedButton(
                                    onClick = { viewModel.changeCurrency(curr) },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (currency == curr) primaryColor else Color.Transparent)
                                ) {
                                    Text(curr.name + " " + curr.symbol, fontSize = s12, color = if (currency == curr) Color.White else textColor)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
                else if (currentTab == 3) {
                    var newName by remember { mutableStateOf("") }
                    var newRole by remember { mutableStateOf("") }

                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("База сотрудников", fontWeight = FontWeight.Bold, color = textColor, fontSize = s18)

                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Добавить нового", fontWeight = FontWeight.Bold, color = textColor, fontSize = s14)
                                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Имя", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))
                                OutlinedTextField(value = newRole, onValueChange = { newRole = it }, label = { Text("Должность (Официант, Бармен...)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))
                                Button(onClick = { viewModel.addColleague(newName, newRole); newName = ""; newRole = "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Добавить", fontSize = s14) }
                            }
                        }

                        Text("Текущие сотрудники (${colleagues.size})", fontWeight = FontWeight.Bold, color = textColor, fontSize = s16)

                        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(colleagues) { colleague ->
                                Card(colors = CardDefaults.cardColors(containerColor = cardColor), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                                Text(colleague.name.take(1).uppercase(), color = primaryColor, fontWeight = FontWeight.Bold, fontSize = s16)
                                            }
                                            Column {
                                                Text(colleague.name, fontWeight = FontWeight.Bold, color = textColor, fontSize = s16)
                                                Text(colleague.role, fontSize = s12, color = Color.Gray)
                                            }
                                        }
                                        IconButton(onClick = { viewModel.removeColleague(colleague.id) }) { Text("✕", fontSize = s16, color = Color.Red) }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }

            // --- ОБУЧАЮЩИЙ ДИАЛОГ ---
            if (showTutorialDialog) {
                var currentTutorialStep by remember { mutableIntStateOf(0) }

                val tutorialSteps = listOf(
                    Pair("Добро пожаловать!", "Это приложение создано для удобного отслеживания твоего графика смен и автоматического расчета зарплаты.\n\nДавай посмотрим, как тут всё устроено!"),
                    Pair("📅 Календарь", "Нажимай на дни, чтобы добавить отработанную смену.\n\nНажми на название месяца сверху, чтобы быстро перейти к другому году или месяцу.\n\nКнопка 🖼️ позволит установить красивые обои для каждого месяца отдельно!"),
                    Pair("📑 Смены (Шаблоны)", "Здесь ты можешь создать свои стандартные смены (например, «Утро», «Вечер», «Отпуск»).\n\nЗадай им цвета, время и тип оплаты — и добавлять их в календарь станет можно в один клик!"),
                    Pair("💰 Зарплата", "Вся твоя статистика и доходы собраны тут.\n\nНажми на ✏️ в углу карточки, чтобы настроить цвета круговой диаграммы под себя.\n\nА если поменять валюту внизу экрана, все суммы автоматически пересчитаются по актуальному курсу!"),
                    Pair("👥 Коллеги", "Добавляй напарников в базу, а затем отмечай их при добавлении смены в календарь.\n\nТак ты всегда будешь помнить, с кем работал в тот или иной день.")
                )

                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = {
                        showTutorialDialog = false
                        viewModel.completeTutorial()
                    },
                    title = { Text(tutorialSteps[currentTutorialStep].first, fontWeight = FontWeight.Bold, color = textColor, fontSize = 22.sp) },
                    text = {
                        Column {
                            Text(
                                text = tutorialSteps[currentTutorialStep].second,
                                fontSize = 16.sp,
                                color = textColor,
                                modifier = Modifier.padding(bottom = 16.dp).heightIn(min = 120.dp)
                            )
                            // Индикаторы (точечки) прогресса
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                tutorialSteps.forEachIndexed { index, _ ->
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (index == currentTutorialStep) primaryColor else Color.Gray.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (currentTutorialStep < tutorialSteps.size - 1) {
                                    currentTutorialStep++
                                } else {
                                    showTutorialDialog = false
                                    viewModel.completeTutorial()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text(if (currentTutorialStep < tutorialSteps.size - 1) "Далее" else "Начать!", color = Color.White)
                        }
                    },
                    dismissButton = {
                        if (currentTutorialStep < tutorialSteps.size - 1) {
                            TextButton(onClick = {
                                showTutorialDialog = false
                                viewModel.completeTutorial()
                            }) {
                                Text("Пропустить", color = Color.Gray)
                            }
                        }
                    }
                )
            }

            if (showMonthPicker) {
                var pickerYear by remember { mutableIntStateOf(currentMonth.year) }
                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showMonthPicker = false },
                    title = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { pickerYear-- }) { Text("◀", color = primaryColor) }
                            Text("$pickerYear", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = textColor)
                            IconButton(onClick = { pickerYear++ }) { Text("▶", color = primaryColor) }
                        }
                    },
                    text = {
                        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(12) { monthIndex ->
                                val isSelected = currentMonth.year == pickerYear && currentMonth.monthValue == monthIndex + 1
                                OutlinedButton(
                                    onClick = {
                                        viewModel.setMonth(YearMonth.of(pickerYear, monthIndex + 1))
                                        showMonthPicker = false
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (isSelected) primaryColor else Color.Transparent),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(monthsRu[monthIndex].take(3), fontSize = 14.sp, color = if (isSelected) Color.White else textColor)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showMonthPicker = false }) { Text("Закрыть", color = Color.Gray) } }
                )
            }

            if (showChartColorDialog) {
                var editingBaseColor by remember { mutableLongStateOf(viewModel.baseColor) }
                var editingTipsColor by remember { mutableLongStateOf(viewModel.tipsColor) }
                var editingBonusColor by remember { mutableLongStateOf(viewModel.bonusColor) }
                var activeColorTab by remember { mutableIntStateOf(0) }

                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showChartColorDialog = false },
                    title = { Text("Цвета диаграммы", fontWeight = FontWeight.Bold, color = textColor, fontSize = s20) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(onClick = { activeColorTab = 0 }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (activeColorTab == 0) primaryColor else Color.Transparent)) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(editingBaseColor)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ставка", fontSize = 11.sp, color = if (activeColorTab == 0) Color.White else textColor)
                                }
                                OutlinedButton(onClick = { activeColorTab = 1 }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (activeColorTab == 1) primaryColor else Color.Transparent)) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(editingTipsColor)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Чаевые", fontSize = 11.sp, color = if (activeColorTab == 1) Color.White else textColor)
                                }
                                OutlinedButton(onClick = { activeColorTab = 2 }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (activeColorTab == 2) primaryColor else Color.Transparent)) {
                                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(editingBonusColor)))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Премия", fontSize = 11.sp, color = if (activeColorTab == 2) Color.White else textColor)
                                }
                            }

                            Text(
                                text = when(activeColorTab) { 0 -> "Выберите цвет для Ставки:"; 1 -> "Выберите цвет для Чаевых:"; else -> "Выберите цвет для Премии:" },
                                fontSize = s14,
                                color = textColor,
                                fontWeight = FontWeight.Bold
                            )

                            ColorWheel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { pickedColor ->
                                when (activeColorTab) {
                                    0 -> editingBaseColor = pickedColor
                                    1 -> editingTipsColor = pickedColor
                                    2 -> editingBonusColor = pickedColor
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.updateChartColors(editingBaseColor, editingTipsColor, editingBonusColor)
                                showChartColorDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) { Text("Сохранить", fontSize = s14) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChartColorDialog = false }) { Text("Отмена", color = Color.Gray, fontSize = s14) }
                    }
                )
            }

            if (showSettingsDialog) {
                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showSettingsDialog = false },
                    title = { Text("Настройки приложения", fontWeight = FontWeight.Bold, color = textColor, fontSize = s20) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Кнопка для повторного вызова обучения
                            Button(
                                onClick = {
                                    showSettingsDialog = false
                                    showTutorialDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, primaryColor)
                            ) {
                                Text("📖 Как пользоваться приложением?", color = primaryColor, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                            // Форма ячеек
                            Text("Форма дней в календаре:", fontWeight = FontWeight.Bold, color = textColor, fontSize = s14)
                            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(CellShape.entries) { shape ->
                                    OutlinedButton(
                                        onClick = { viewModel.changeCellShape(shape) },
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (currentCellShape == shape) primaryColor else Color.Transparent),
                                        contentPadding = PaddingValues(2.dp)
                                    ) { Text(shape.displayName, fontSize = 10.sp, color = if (currentCellShape == shape) Color.White else textColor) }
                                }
                            }

                            // Тема
                            Text("Тема оформления:", fontWeight = FontWeight.Bold, color = textColor, fontSize = s14)
                            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(AppTheme.entries) { theme ->
                                    OutlinedButton(onClick = { viewModel.changeTheme(theme) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (currentTheme == theme) primaryColor else Color.Transparent)) {
                                        Text(theme.displayName, fontSize = s11, color = if (currentTheme == theme) Color.White else textColor)
                                    }
                                }
                            }

                            // Размер текста
                            Text("Размер текста:", fontWeight = FontWeight.Bold, color = textColor, fontSize = s14)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(onClick = { viewModel.updateTextScale(0.85f) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (textScale == 0.85f) primaryColor else Color.Transparent)) { Text("Мелкий", fontSize = 11.sp, color = if (textScale == 0.85f) Color.White else textColor) }
                                OutlinedButton(onClick = { viewModel.updateTextScale(1f) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (textScale == 1f) primaryColor else Color.Transparent)) { Text("Средний", fontSize = 13.sp, color = if (textScale == 1f) Color.White else textColor) }
                                OutlinedButton(onClick = { viewModel.updateTextScale(1.15f) }, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (textScale == 1.15f) primaryColor else Color.Transparent)) { Text("Крупный", fontSize = 15.sp, color = if (textScale == 1.15f) Color.White else textColor) }
                            }
                        }
                    },
                    confirmButton = { Button(onClick = { showSettingsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Закрыть", fontSize = s14) } }
                )
            }

            if (showTemplateDialog) {
                val isEditing = templateToEdit != null
                var tplName by remember { mutableStateOf(templateToEdit?.name ?: "") }
                var tStartH by remember { mutableIntStateOf(templateToEdit?.startTime?.hour ?: 11) }
                var tStartM by remember { mutableIntStateOf(templateToEdit?.startTime?.minute ?: 0) }
                var tEndH by remember { mutableIntStateOf(templateToEdit?.endTime?.hour ?: 22) }
                var tEndM by remember { mutableIntStateOf(templateToEdit?.endTime?.minute ?: 0) }
                var tColor by remember { mutableLongStateOf(templateToEdit?.colorArgb ?: shiftColors[0]) }
                var tPaymentType by remember { mutableStateOf(templateToEdit?.paymentType ?: PaymentType.HOURLY) }
                var tIsNight by remember { mutableStateOf(templateToEdit?.isNightShift ?: false) }
                var tIsHoliday by remember { mutableStateOf(templateToEdit?.isHoliday ?: false) }
                var tNightMult by remember { mutableStateOf(templateToEdit?.nightMultiplier?.toString() ?: "1.5") }
                var tHolidayMult by remember { mutableStateOf(templateToEdit?.holidayMultiplier?.toString() ?: "2.0") }

                var showStart by remember { mutableStateOf(false) }
                var sShowEnd by remember { mutableStateOf(false) }
                var showCustomColorWheel by remember { mutableStateOf(false) }

                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showTemplateDialog = false },
                    title = { Text(if (isEditing) "Редактировать шаблон" else "Новый шаблон", color = textColor, fontSize = s20, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                            OutlinedTextField(value = tplName, onValueChange = { tplName = it }, label = { Text("Название (Смена, Отпуск)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))

                            Text("Оплата:", fontSize = s14, color = Color.Gray)
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PaymentType.entries.forEach { pt ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { tPaymentType = pt }, verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = tPaymentType == pt, onClick = { tPaymentType = pt })
                                        Text(pt.displayName, color = textColor, fontSize = s14)
                                    }
                                }
                            }

                            if (tPaymentType == PaymentType.HOURLY) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    OutlinedButton(onClick = { showStart = true }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("С: ${String.format(Locale.US, "%02d:%02d", tStartH, tStartM)}", color = textColor, fontSize = s12) }
                                    OutlinedButton(onClick = { sShowEnd = true }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("До: ${String.format(Locale.US, "%02d:%02d", tEndH, tEndM)}", color = textColor, fontSize = s12) }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = tIsNight, onCheckedChange = { tIsNight = it })
                                    Text("Ночная смена", fontSize = s14, color = textColor)
                                }
                                if (tIsNight) {
                                    OutlinedTextField(
                                        value = tNightMult,
                                        onValueChange = { tNightMult = it },
                                        label = { Text("Коэффициент ночных", fontSize = s12) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s14)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = tIsHoliday, onCheckedChange = { tIsHoliday = it })
                                    Text("Праздничный день", fontSize = s14, color = textColor)
                                }
                                if (tIsHoliday) {
                                    OutlinedTextField(
                                        value = tHolidayMult,
                                        onValueChange = { tHolidayMult = it },
                                        label = { Text("Коэффициент праздничных", fontSize = s12) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s14)
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Цвет:", fontSize = s14, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(tColor)).border(1.dp, textColor.copy(alpha = 0.5f), CircleShape))
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { showCustomColorWheel = !showCustomColorWheel }) { Text(if (showCustomColorWheel) "Готовые" else "Свой цвет 🎨", color = primaryColor, fontSize = s12) }
                            }

                            if (showCustomColorWheel) {
                                ColorWheel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { selectedLongColor -> tColor = selectedLongColor }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    shiftColors.forEach { colorVal ->
                                        val isColorSelected = tColor == colorVal
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(colorVal)).border(width = if (isColorSelected) 3.dp else 0.dp, color = if (isColorSelected) textColor else Color.Transparent, shape = CircleShape).clickable { tColor = colorVal })
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.saveTemplate(
                                id = if (isEditing) templateToEdit?.id else null,
                                name = tplName,
                                startH = tStartH,
                                startM = tStartM,
                                endH = tEndH,
                                endM = tEndM,
                                color = tColor,
                                pt = tPaymentType,
                                isNight = tIsNight,
                                isHol = tIsHoliday,
                                nightMultiplier = tNightMult.replace(",", ".").toDoubleOrNull() ?: 1.5,
                                holidayMultiplier = tHolidayMult.replace(",", ".").toDoubleOrNull() ?: 2.0
                            )
                            showTemplateDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Сохранить", fontSize = s14) }
                    },
                    dismissButton = { TextButton(onClick = { showTemplateDialog = false }) { Text("Отмена", color = Color.Gray, fontSize = s14) } }
                )
                if (showStart) { val st = rememberTimePickerState(initialHour = tStartH, initialMinute = tStartM, is24Hour = true); AlertDialog(containerColor = cardColor, onDismissRequest = { showStart = false }, text = { TimePicker(st) }, confirmButton = { TextButton(onClick = { tStartH = st.hour; tStartM = st.minute; showStart = false }) { Text("ОК", color = primaryColor, fontSize = s14) } }) }
                if (sShowEnd) { val st = rememberTimePickerState(initialHour = tEndH, initialMinute = tEndM, is24Hour = true); AlertDialog(containerColor = cardColor, onDismissRequest = { sShowEnd = false }, text = { TimePicker(st) }, confirmButton = { TextButton(onClick = { tEndH = st.hour; tEndM = st.minute; sShowEnd = false }) { Text("ОК", color = primaryColor, fontSize = s14) } }) }
            }

            if (showShiftDialog) {
                val existingShift = if (selectedDates.size == 1) shifts[selectedDates.first()] else null
                var startHour by remember { mutableIntStateOf(existingShift?.startTime?.hour ?: 11) }
                var startMinute by remember { mutableIntStateOf(existingShift?.startTime?.minute ?: 0) }
                var endHour by remember { mutableIntStateOf(existingShift?.endTime?.hour ?: 22) }
                var endMinute by remember { mutableIntStateOf(existingShift?.endTime?.minute ?: 0) }
                var shiftName by remember { mutableStateOf(existingShift?.shiftName ?: "") }
                var selectedColor by remember { mutableLongStateOf(existingShift?.colorArgb ?: shiftColors[0]) }

                var paymentType by remember { mutableStateOf(existingShift?.paymentType ?: PaymentType.HOURLY) }
                var isNight by remember { mutableStateOf(existingShift?.isNightShift ?: false) }
                var isHoliday by remember { mutableStateOf(existingShift?.isHoliday ?: false) }
                var nightMult by remember { mutableStateOf(existingShift?.nightMultiplier?.toString() ?: "1.5") }
                var holidayMult by remember { mutableStateOf(existingShift?.holidayMultiplier?.toString() ?: "2.0") }

                var inputFixedAmount by remember { mutableStateOf(if (existingShift != null && existingShift.fixedPaymentAmount > 0) existingShift.fixedPaymentAmount.toString() else "") }
                var inputTips by remember { mutableStateOf(if (existingShift != null && existingShift.tips > 0) existingShift.tips.toString() else "") }

                var selectedColleagues by remember { mutableStateOf(existingShift?.colleaguesIds ?: emptyList()) }
                var sShowStart by remember { mutableStateOf(false) }
                var sShowEnd by remember { mutableStateOf(false) }
                var showCustomColorWheel by remember { mutableStateOf(false) }

                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showShiftDialog = false; if (!isMultiSelectMode) selectedDates = emptySet() },
                    title = { Text(if (selectedDates.size == 1) "Смена: ${selectedDates.first()}" else "Смена для ${selectedDates.size} дней", fontWeight = FontWeight.Bold, fontSize = s18, color = textColor) },
                    text = {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Быстрый выбор из шаблонов:", fontSize = s14, color = Color.Gray)
                            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 120.dp)) {
                                items(templates) { tpl ->
                                    OutlinedButton(
                                        onClick = {
                                            shiftName = tpl.name
                                            startHour = tpl.startTime.hour
                                            startMinute = tpl.startTime.minute
                                            endHour = tpl.endTime.hour
                                            endMinute = tpl.endTime.minute
                                            selectedColor = tpl.colorArgb
                                            paymentType = tpl.paymentType
                                            isNight = tpl.isNightShift
                                            isHoliday = tpl.isHoliday
                                            nightMult = tpl.nightMultiplier.toString()
                                            holidayMult = tpl.holidayMultiplier.toString()
                                        },
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(tpl.colorArgb)))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(tpl.name, fontSize = s11, color = textColor, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))

                            OutlinedTextField(value = shiftName, onValueChange = { shiftName = it }, label = { Text("Название (необязательно)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))

                            Text("Оплата за день:", fontSize = s14, color = Color.Gray)
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PaymentType.entries.forEach { pt ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable { paymentType = pt }, verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = paymentType == pt, onClick = { paymentType = pt })
                                        Text(pt.displayName, color = textColor, fontSize = s14)
                                    }
                                }
                            }

                            if (paymentType == PaymentType.HOURLY) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    OutlinedButton(onClick = { sShowStart = true }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("С:\n${String.format(Locale.US, "%02d:%02d", startHour, startMinute)}", textAlign = TextAlign.Center, color = textColor, fontSize = s12) }
                                    OutlinedButton(onClick = { sShowEnd = true }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("До:\n${String.format(Locale.US, "%02d:%02d", endHour, endMinute)}", textAlign = TextAlign.Center, color = textColor, fontSize = s12) }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = isNight, onCheckedChange = { isNight = it })
                                    Text("Ночная смена", fontSize = s14, color = textColor)
                                }
                                if (isNight) {
                                    OutlinedTextField(
                                        value = nightMult,
                                        onValueChange = { nightMult = it },
                                        label = { Text("Коэффициент ночных", fontSize = s12) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s14)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(checked = isHoliday, onCheckedChange = { isHoliday = it })
                                    Text("Праздничный день", fontSize = s14, color = textColor)
                                }
                                if (isHoliday) {
                                    OutlinedTextField(
                                        value = holidayMult,
                                        onValueChange = { holidayMult = it },
                                        label = { Text("Коэффициент праздничных", fontSize = s12) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s14)
                                    )
                                }

                                OutlinedTextField(value = inputTips, onValueChange = { inputTips = it }, label = { Text("Чаевые за день (в RUB)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))
                            } else if (paymentType == PaymentType.FIXED) {
                                OutlinedTextField(value = inputFixedAmount, onValueChange = { inputFixedAmount = it }, label = { Text("Сумма за день (в RUB)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))
                                OutlinedTextField(value = inputTips, onValueChange = { inputTips = it }, label = { Text("Чаевые за день (в RUB)", color = textColor, fontSize = s12) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = s16))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Цвет:", fontSize = s14, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(selectedColor)).border(1.dp, textColor.copy(alpha = 0.5f), CircleShape))
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = { showCustomColorWheel = !showCustomColorWheel }) { Text(if (showCustomColorWheel) "Готовые" else "Свой цвет 🎨", color = primaryColor, fontSize = s12) }
                            }

                            if (showCustomColorWheel) {
                                ColorWheel(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { pickedColor -> selectedColor = pickedColor }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    shiftColors.forEach { colorVal ->
                                        val isColorSelected = selectedColor == colorVal
                                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(colorVal)).border(width = if (isColorSelected) 3.dp else 0.dp, color = if (isColorSelected) textColor else Color.Transparent, shape = CircleShape).clickable { selectedColor = colorVal })
                                    }
                                }
                            }

                            if (colleagues.isNotEmpty() && paymentType != PaymentType.NONE) {
                                Text("Напарники:", fontWeight = FontWeight.Bold, fontSize = s14, color = textColor)
                                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 120.dp)) {
                                    items(colleagues) { colleague ->
                                        val isSelected = selectedColleagues.contains(colleague.id)
                                        FilterChip(selected = isSelected, onClick = { selectedColleagues = if (isSelected) selectedColleagues - colleague.id else selectedColleagues + colleague.id }, label = { Text(colleague.name, fontSize = s12) })
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val newShift = Shift(
                                    date = LocalDate.now(),
                                    startTime = LocalTime.of(startHour, startMinute),
                                    endTime = LocalTime.of(endHour, endMinute),
                                    hourlyRate = hourlyRate,
                                    fixedPaymentAmount = inputFixedAmount.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                    tips = inputTips.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                    colleaguesIds = selectedColleagues,
                                    shiftName = shiftName,
                                    colorArgb = selectedColor,
                                    paymentType = paymentType,
                                    isNightShift = isNight,
                                    isHoliday = isHoliday,
                                    nightMultiplier = nightMult.replace(",", ".").toDoubleOrNull() ?: 1.5,
                                    holidayMultiplier = holidayMult.replace(",", ".").toDoubleOrNull() ?: 2.0
                                )
                                viewModel.saveShiftsForDates(selectedDates, newShift)
                                selectedDates = emptySet()
                                isMultiSelectMode = false
                                showShiftDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) { Text("Сохранить", fontSize = s14) }
                    },
                    dismissButton = {
                        if (existingShift != null && selectedDates.size == 1) { TextButton(onClick = { viewModel.deleteShift(selectedDates.first()); selectedDates = emptySet(); showShiftDialog = false }) { Text("Удалить", color = Color.Red, fontSize = s14) } }
                        else { TextButton(onClick = { if (!isMultiSelectMode) selectedDates = emptySet(); showShiftDialog = false }) { Text("Отмена", color = Color.Gray, fontSize = s14) } }
                    }
                )

                if (sShowStart) { val st = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true); AlertDialog(containerColor = cardColor, onDismissRequest = { sShowStart = false }, text = { TimePicker(st) }, confirmButton = { TextButton(onClick = { startHour = st.hour; startMinute = st.minute; sShowStart = false }) { Text("ОК", color = primaryColor, fontSize = s14) } }) }
                if (sShowEnd) { val st = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true); AlertDialog(containerColor = cardColor, onDismissRequest = { sShowEnd = false }, text = { TimePicker(st) }, confirmButton = { TextButton(onClick = { endHour = st.hour; endMinute = st.minute; sShowEnd = false }) { Text("ОК", color = primaryColor, fontSize = s14) } }) }
            }
        }
    }
}

@Composable
fun rememberUriBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString != null) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(uriString)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                } catch (e: Exception) { e.printStackTrace() }
            }
        } else {
            bitmap = null
        }
    }
    return bitmap
}

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    onColorChanged: (Long) -> Unit
) {
    var touchOffset by remember { mutableStateOf<Offset?>(null) }
    val rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) { detectDragGestures { change, _ -> touchOffset = change.position } }
            .pointerInput(Unit) { detectTapGestures { offset -> touchOffset = offset } }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(brush = Brush.sweepGradient(rainbow, center = center), radius = radius, center = center)
        drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White, Color.Transparent), center = center, radius = radius), radius = radius, center = center)

        touchOffset?.let { offset ->
            val dx = offset.x - center.x
            val dy = offset.y - center.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val angle = atan2(dy.toDouble(), dx.toDouble())

            val constrainedDist = dist.coerceAtMost(radius)
            val selX = center.x + constrainedDist * cos(angle).toFloat()
            val selY = center.y + constrainedDist * sin(angle).toFloat()

            var hue = Math.toDegrees(angle).toFloat()
            if (hue < 0) hue += 360f
            val sat = (constrainedDist / radius).coerceIn(0f, 1f)

            val hsvColor = AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f))
            onColorChanged(hsvColor.toLong() and 0xFFFFFFFF)

            drawCircle(color = Color.White, radius = 14.dp.toPx(), center = Offset(selX, selY), style = Stroke(width = 3.dp.toPx()))
            drawCircle(color = Color(hsvColor), radius = 12.dp.toPx(), center = Offset(selX, selY))
        }
    }
}