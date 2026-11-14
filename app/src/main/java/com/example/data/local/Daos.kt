package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM hosts ORDER BY COALESCE(lastConnectedAt, createdAt) DESC")
    fun observeAllHosts(): Flow<List<HostEntity>>

    @Query("SELECT * FROM hosts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHost(host: HostEntity): Long

    @Update
    suspend fun updateHost(host: HostEntity)

    @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :id")
    suspend fun updateLastConnected(id: Long, timestamp: Long)

    @Query("DELETE FROM hosts WHERE id = :id")
    suspend fun deleteHostById(id: Long)
}

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
    fun observeAllKeys(): Flow<List<SshKeyEntity>>

    @Query("SELECT * FROM ssh_keys WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SshKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKey(key: SshKeyEntity): Long

    @Query("DELETE FROM ssh_keys WHERE id = :id")
    suspend fun deleteKeyById(id: Long)
}

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts ORDER BY firstTrustedAt DESC")
    fun observeAllKnownHosts(): Flow<List<KnownHostEntity>>

    @Query("SELECT * FROM known_hosts WHERE hostKeyId = :hostKeyId LIMIT 1")
    suspend fun getByHostKeyId(hostKeyId: String): KnownHostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnownHost(knownHost: KnownHostEntity)

    @Query("DELETE FROM known_hosts WHERE hostKeyId = :hostKeyId")
    suspend fun deleteKnownHost(hostKeyId: String)

    @Query("DELETE FROM known_hosts")
    suspend fun clearAll()
}

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY sortOrder ASC, id ASC")
    fun observeAllSnippets(): Flow<List<SnippetEntity>>

    @Query("SELECT COUNT(*) FROM snippets")
    suspend fun countSnippets(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: SnippetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<SnippetEntity>)

    @Update
    suspend fun updateSnippet(snippet: SnippetEntity)

    @Query("DELETE FROM snippets WHERE id = :id")
    suspend fun deleteSnippetById(id: Long)
}

@Dao
interface SessionHistoryDao {
    @Query("SELECT * FROM session_history ORDER BY startedAt DESC")
    fun observeAllHistory(): Flow<List<SessionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: SessionHistoryEntity): Long

    @Query(
        "UPDATE session_history SET endedAt = :endedAt, status = :status, " +
            "errorMessage = :errorMessage, transcriptPreview = :transcriptPreview WHERE id = :id"
    )
    suspend fun finalizeHistory(
        id: Long,
        endedAt: Long,
        status: String,
        errorMessage: String?,
        transcriptPreview: String
    )

    @Query("DELETE FROM session_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM session_history")
    suspend fun clearAll()
}
