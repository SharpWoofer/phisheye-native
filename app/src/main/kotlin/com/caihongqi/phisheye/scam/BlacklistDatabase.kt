package com.caihongqi.phisheye.phisheye

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "blacklist_entries")
data class BlacklistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String, // E.164 format or raw
    val source: String, // "manual", "authority", etc.
    val timestamp: Long
)

@Dao
interface BlacklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlacklistEntry)

    @Query("SELECT COUNT(*) FROM blacklist_entries WHERE phoneNumber = :number")
    suspend fun isBlacklisted(number: String): Int

    @Query("DELETE FROM blacklist_entries WHERE phoneNumber = :number")
    suspend fun remove(number: String)

    @Query("DELETE FROM blacklist_entries")
    suspend fun clear()
}

@Database(entities = [BlacklistEntry::class], version = 1, exportSchema = false)
abstract class BlacklistDatabase : RoomDatabase() {
    abstract fun blacklistDao(): BlacklistDao

    companion object {
        @Volatile
        private var Instance: BlacklistDatabase? = null

        fun getInstance(context: Context): BlacklistDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlacklistDatabase::class.java,
                    "blacklist.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class BlacklistRepository private constructor(
    private val dao: BlacklistDao
) {
    suspend fun addNumber(number: String, source: String) = withContext(Dispatchers.IO) {
        dao.insert(BlacklistEntry(phoneNumber = number, source = source, timestamp = System.currentTimeMillis()))
    }

    suspend fun isBlacklisted(number: String): Boolean = withContext(Dispatchers.IO) {
        dao.isBlacklisted(number) > 0
    }

    companion object {
        @Volatile
        private var Instance: BlacklistRepository? = null

        fun getInstance(context: Context): BlacklistRepository {
            return Instance ?: synchronized(this) {
                Instance ?: BlacklistRepository(
                    BlacklistDatabase.getInstance(context).blacklistDao()
                ).also { Instance = it }
            }
        }
    }
}
