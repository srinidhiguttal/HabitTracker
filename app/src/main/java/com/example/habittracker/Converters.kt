package com.example.habittracker

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Converters {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, dateFormatter) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.format(timeFormatter)

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, timeFormatter) }

    @TypeConverter
    fun fromLocalDateSet(value: Set<LocalDate>?): String? {
        return value?.joinToString(",") { it.format(dateFormatter) }
    }

    @TypeConverter
    fun toLocalDateSet(value: String?): Set<LocalDate>? {
        if (value.isNullOrEmpty()) return emptySet()
        return value.split(",").map { LocalDate.parse(it, dateFormatter) }.toSet()
    }
}
