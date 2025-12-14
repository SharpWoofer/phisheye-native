package com.caihongqi.phisheye.scam

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Entity(tableName = "detection_history")
data class DetectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val prediction: String,
    val confidence: Float,
    val analysisSource: String,
    val reason: String?,
    val timestamp: Long
)

@Dao
interface DetectionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DetectionHistoryEntity)

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DetectionHistoryEntity>>

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT :maxRows")
    suspend fun getRecent(maxRows: Int): List<DetectionHistoryEntity>

    @Query(
        "DELETE FROM detection_history WHERE id NOT IN (" +
            "SELECT id FROM detection_history ORDER BY timestamp DESC LIMIT :maxRows" +
            ")"
    )
    suspend fun trimToSize(maxRows: Int)

    @Query("DELETE FROM detection_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM detection_history")
    suspend fun count(): Int

    @Query("DELETE FROM detection_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [DetectionHistoryEntity::class], version = 1, exportSchema = false)
abstract class DetectionHistoryDatabase : RoomDatabase() {
    abstract fun detectionHistoryDao(): DetectionHistoryDao

    companion object {
        @Volatile
        private var Instance: DetectionHistoryDatabase? = null

        fun getInstance(context: Context): DetectionHistoryDatabase {
            return Instance ?: synchronized(this) {
                Instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DetectionHistoryDatabase::class.java,
                    "detection_history.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

class DetectionHistoryRepository private constructor(
    private val dao: DetectionHistoryDao
) {
    suspend fun logDetection(analyzed: AnalyzedNotification) = withContext(Dispatchers.IO) {
        val entity = DetectionHistoryEntity(
            packageName = analyzed.info.packageName,
            appName = analyzed.info.appName,
            title = analyzed.info.title,
            text = analyzed.info.text,
            prediction = analyzed.prediction,
            confidence = analyzed.confidence,
            analysisSource = analyzed.analysisSource,
            reason = analyzed.reason,
            timestamp = System.currentTimeMillis()
        )
        dao.insert(entity)
        dao.trimToSize(MAX_ROWS)
    }

    fun getDetectionHistory(): Flow<List<DetectionHistoryEntity>> = dao.getAll()

    suspend fun getDetectionCount(): Int =
        withContext(Dispatchers.IO) { dao.count() }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    companion object {
        private const val MAX_ROWS = 200

        @Volatile
        private var Instance: DetectionHistoryRepository? = null

        fun getInstance(context: Context): DetectionHistoryRepository {
            return Instance ?: synchronized(this) {
                Instance ?: DetectionHistoryRepository(
                    DetectionHistoryDatabase.getInstance(context).detectionHistoryDao()
                ).also { Instance = it }
            }
        }
    }
}
