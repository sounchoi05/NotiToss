package com.ckchoi.notitoss.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Database(
    entities = [
        NotificationEventEntity::class,
        ForwardRuleEntity::class,
        DeliveryHistoryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun ruleDao(): RuleDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notitoss.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notification_events ADD COLUMN attachmentUris TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "ALTER TABLE delivery_history ADD COLUMN attachmentUris TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE forward_rules ADD COLUMN cardColorHex TEXT",
                )
                db.execSQL(
                    "ALTER TABLE delivery_history ADD COLUMN cardColorHex TEXT",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE delivery_history ADD COLUMN appliedRuleId INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE delivery_history ADD COLUMN appliedRuleName TEXT",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE forward_rules ADD COLUMN excludedAppPackages TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE forward_rules ADD COLUMN telegramTargets TEXT NOT NULL DEFAULT '[]'",
                )
            }
        }
    }
}

class AppTypeConverters {
    private val json = Json

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(ListSerializer(String.serializer()), value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }
    }

    @TypeConverter
    fun fromActionList(value: List<DeliveryActionType>): String {
        return json.encodeToString(ListSerializer(DeliveryActionType.serializer()), value)
    }

    @TypeConverter
    fun toActionList(value: String): List<DeliveryActionType> {
        return if (value.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(DeliveryActionType.serializer()), value)
        }
    }

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromActionType(value: DeliveryActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): DeliveryActionType = DeliveryActionType.valueOf(value)

    @TypeConverter
    fun fromStatus(value: DeliveryStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): DeliveryStatus = DeliveryStatus.valueOf(value)

}
