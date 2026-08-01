package com.example.habittracker

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val completedDates: Set<LocalDate> = emptySet()
) {
    fun isCompletedOn(date: LocalDate): Boolean = completedDates.contains(date)
    
    @get:Ignore
    val currentStreak: Int
        get() {
            var streak = 0
            var date = LocalDate.now()
            if (!isCompletedOn(date)) {
                date = date.minusDays(1)
            }
            while (isCompletedOn(date)) {
                streak++
                date = date.minusDays(1)
            }
            return streak
        }

    @get:Ignore
    val completionRate: Float
        get() {
            if (completedDates.isEmpty()) return 0f
            val daysTracked = 30
            val startDate = LocalDate.now().minusDays(daysTracked.toLong())
            val completedInLast30 = completedDates.count { it.isAfter(startDate) || it.isEqual(startDate) }
            return (completedInLast30.toFloat() / daysTracked.toFloat()).coerceIn(0f, 1f)
        }
}
