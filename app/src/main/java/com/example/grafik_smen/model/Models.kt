package com.example.grafik_smen.model

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class AppTheme(val displayName: String) {
    LIGHT("Светлая"), DARK("Темная"), SOFT_PINK("Розовая"),
    SOFT_BLUE("Голубая"), LIGHT_GRAY("Светло-серая"), DARK_GRAY("Темно-серая")
}

enum class CellShape(val displayName: String) {
    ROUNDED("Скругленные"), CIRCLE("Круглые"), SQUARE("Квадратные")
}

enum class Currency(val symbol: String, val rateToRub: Double) {
    RUB("₽", 1.0),
    BYN("Br", 27.5),
    EUR("€", 102.0),
    USD("$", 95.0)
}

enum class PaymentType(val displayName: String) {
    HOURLY("Почасовая"),
    FIXED("Фикс. сумма"),
    NONE("Неоплачиваемая")
}

data class Colleague(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String
)

data class ShiftTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val colorArgb: Long,
    val paymentType: PaymentType = PaymentType.HOURLY,
    val isNightShift: Boolean = false,
    val isHoliday: Boolean = false,
    val nightMultiplier: Double = 1.5,
    val holidayMultiplier: Double = 2.0
)

data class Shift(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hourlyRate: Double,
    val fixedPaymentAmount: Double = 0.0,
    val tips: Double = 0.0,
    val colleaguesIds: List<String> = emptyList(),
    val shiftName: String = "",
    val colorArgb: Long = 0xFF3F51B5,
    val paymentType: PaymentType = PaymentType.HOURLY,
    val isNightShift: Boolean = false,
    val isHoliday: Boolean = false,
    val nightMultiplier: Double = 1.5,
    val holidayMultiplier: Double = 2.0
) {
    val hoursWorked: Double
        get() {
            if (paymentType != PaymentType.HOURLY) return 0.0
            val startMinutes = startTime.hour * 60 + startTime.minute
            val endMinutes = endTime.hour * 60 + endTime.minute
            val diff = if (endMinutes >= startMinutes) {
                endMinutes - startMinutes
            } else {
                (24 * 60 - startMinutes) + endMinutes
            }
            return diff / 60.0
        }

    fun calculateEarnings(baseHourlyRate: Double): Double {
        return when (paymentType) {
            PaymentType.HOURLY -> {
                val hours = hoursWorked
                var rate = baseHourlyRate
                if (isHoliday) {
                    rate *= holidayMultiplier
                } else if (isNightShift) {
                    rate *= nightMultiplier
                }
                (hours * rate) + tips
            }
            PaymentType.FIXED -> fixedPaymentAmount + tips
            PaymentType.NONE -> 0.0
        }
    }
}