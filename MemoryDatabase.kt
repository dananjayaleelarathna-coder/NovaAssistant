package com.nova.assistant.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "memory_facts")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,       // e.g. "nickname", "favorite_app", "preferred_language"
    val value: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_commands")
data class CustomCommand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trigger: String,           // e.g. "Good morning Nova"
    val actionsJson: String        // ordered list of CommandIntent names + params, serialized
)

@Entity(tableName = "conversation_history")
data class ConversationEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,      // "user" | "assistant"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFact(fact: MemoryFact)

    @Query("SELECT * FROM memory_facts ORDER BY createdAt DESC")
    fun observeFacts(): Flow<List<MemoryFact>>

    @Query("DELETE FROM memory_facts WHERE key = :key")
    suspend fun deleteFact(key: String)

    @Query("DELETE FROM memory_facts")
    suspend fun clearFacts()

    @Insert
    suspend fun insertCommand(command: CustomCommand)

    @Query("SELECT * FROM custom_commands")
    fun observeCustomCommands(): Flow<List<CustomCommand>>

    @Delete
    suspend fun deleteCommand(command: CustomCommand)

    @Insert
    suspend fun insertConversation(entry: ConversationEntry)

    @Query("SELECT * FROM conversation_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentConversation(limit: Int = 50): Flow<List<ConversationEntry>>

    @Query("DELETE FROM conversation_history")
    suspend fun clearConversation()
}

@Database(
    entities = [MemoryFact::class, CustomCommand::class, ConversationEntry::class],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null

        fun get(context: Context): MemoryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, MemoryDatabase::class.java, "nova_memory.db"
                ).build().also { INSTANCE = it }
            }
    }
}

/** Thin repository so the UI/engine layers never touch DAOs directly. */
class MemoryManager(context: Context) {
    private val dao = MemoryDatabase.get(context).memoryDao()

    fun facts(): Flow<List<MemoryFact>> = dao.observeFacts()
    suspend fun remember(key: String, value: String) = dao.upsertFact(MemoryFact(key = key, value = value))
    suspend fun forget(key: String) = dao.deleteFact(key)
    suspend fun clearAll() = dao.clearFacts()

    fun customCommands(): Flow<List<CustomCommand>> = dao.observeCustomCommands()
    suspend fun addCustomCommand(trigger: String, actionsJson: String) =
        dao.insertCommand(CustomCommand(trigger = trigger, actionsJson = actionsJson))
    suspend fun removeCustomCommand(command: CustomCommand) = dao.deleteCommand(command)

    fun recentConversation(limit: Int = 50): Flow<List<ConversationEntry>> = dao.observeRecentConversation(limit)
    suspend fun logTurn(role: String, text: String) = dao.insertConversation(ConversationEntry(role = role, text = text))
    suspend fun clearConversationHistory() = dao.clearConversation()
}
