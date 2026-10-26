package it.emanuelemelini.mitayo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schemas are exported to app/schemas. From 0.1.0 the app is public: every schema change needs a migration. */
@Database(
    entities = [
        Anime::class,
        Genre::class,
        AnimeGenre::class,
        ListEntry::class,
        SkippedAnime::class,
        OutboxOp::class,
        RemoteListItem::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    abstract fun listDao(): ListDao

    abstract fun skippedDao(): SkippedDao

    abstract fun outboxDao(): OutboxDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mitayo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        /** v2 (the first builds after 0.1.0): no pending list any more, a swipe up asks the episodes at once. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `pending`")
            }
        }

        /**
         * v3 (1.0.0): the Japanese title of the anime. A separate version because v2 builds were
         * already installed; existing anime get it with the next daily catalog pass.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anime` ADD COLUMN `titleJa` TEXT")
            }
        }

        /** v4: the MAL score of the list entries (it comes with the next list sync) and of the changes to send. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `score` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `score` INTEGER")
            }
        }
    }
}

/** Statuses stored as MAL writes them, so the SQL can compare with 'completed'. Room adds the null checks. */
class Converters {
    @TypeConverter
    fun statusToString(status: ListStatus): String = status.apiValue

    @TypeConverter
    fun stringToStatus(value: String): ListStatus = ListStatus.fromApi(value) ?: ListStatus.PLAN_TO_WATCH
}
