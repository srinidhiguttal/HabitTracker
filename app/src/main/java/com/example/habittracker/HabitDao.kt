package com.example.habittracker

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Delete
    suspend fun deleteHabit(habit: Habit): Int

    @Update
    suspend fun updateHabit(habit: Habit): Int
}
