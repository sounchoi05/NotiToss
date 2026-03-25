package com.ckchoi.notitoss.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_events ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<NotificationEventEntity>>

    @Query("SELECT * FROM notification_events ORDER BY receivedAt DESC")
    suspend fun getAll(): List<NotificationEventEntity>

    @Query(
        """
        SELECT * FROM notification_events
        WHERE sourceType = :sourceType
        AND sourcePackageName = :sourcePackageName
        AND body = :body
        AND receivedAt BETWEEN :windowStart AND :windowEnd
        ORDER BY receivedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findRecentDuplicate(
        sourceType: SourceType,
        sourcePackageName: String,
        body: String,
        windowStart: Long,
        windowEnd: Long,
    ): NotificationEventEntity?

    @Insert
    suspend fun insert(item: NotificationEventEntity): Long

    @Update
    suspend fun update(item: NotificationEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NotificationEventEntity>)

    @Query("DELETE FROM notification_events")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM notification_events
        WHERE sourceType = :sourceType
        AND id NOT IN (
            SELECT id FROM notification_events
            WHERE sourceType = :sourceType
            ORDER BY receivedAt DESC
            LIMIT :limitCount
        )
        """,
    )
    suspend fun pruneBySourceType(
        sourceType: SourceType,
        limitCount: Int,
    )
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM forward_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ForwardRuleEntity>>

    @Query("SELECT * FROM forward_rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<ForwardRuleEntity>

    @Query("SELECT * FROM forward_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<ForwardRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ForwardRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ForwardRuleEntity>)

    @Query("DELETE FROM forward_rules WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long)

    @Query("DELETE FROM forward_rules")
    suspend fun clearAll()
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM delivery_history ORDER BY deliveredAt DESC")
    fun observeAll(): Flow<List<DeliveryHistoryEntity>>

    @Query("SELECT * FROM delivery_history ORDER BY deliveredAt DESC")
    suspend fun getAll(): List<DeliveryHistoryEntity>

    @Insert
    suspend fun insert(item: DeliveryHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DeliveryHistoryEntity>)

    @Query("DELETE FROM delivery_history WHERE id = :deliveryId")
    suspend fun deleteById(deliveryId: Long)

    @Query("DELETE FROM delivery_history")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM delivery_history
        WHERE id NOT IN (
            SELECT id FROM delivery_history
            ORDER BY deliveredAt DESC
            LIMIT :limitCount
        )
        """,
    )
    suspend fun pruneToRecent(limitCount: Int)
}
