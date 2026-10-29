package it.emanuelemelini.mitayo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.emanuelemelini.mitayo.data.anilist.AniListAcknowledged
import it.emanuelemelini.mitayo.data.anilist.AniListConflict
import it.emanuelemelini.mitayo.data.anilist.AniListDao
import it.emanuelemelini.mitayo.data.anilist.AniListEntry
import it.emanuelemelini.mitayo.data.anilist.AniListLogDao
import it.emanuelemelini.mitayo.data.anilist.AniListOp
import it.emanuelemelini.mitayo.data.anilist.AniListSession
import it.emanuelemelini.mitayo.data.anilist.AniListSessionEvent

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
        SwipeRecord::class,
        AniListEntry::class,
        AniListOp::class,
        AniListConflict::class,
        AniListSession::class,
        AniListSessionEvent::class,
        AniListAcknowledged::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    abstract fun listDao(): ListDao

    abstract fun skippedDao(): SkippedDao

    abstract fun outboxDao(): OutboxDao

    abstract fun historyDao(): HistoryDao

    abstract fun aniListDao(): AniListDao

    abstract fun aniListLogDao(): AniListLogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mitayo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

        /**
         * v5 (1.2.0): when an anime airs each week, for the new episode reminder (it comes with
         * the next list sync), and the swipe history.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anime` ADD COLUMN `broadcastDay` TEXT")
                db.execSQL("ALTER TABLE `anime` ADD COLUMN `broadcastTime` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `swipe_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`animeId` INTEGER NOT NULL, `action` TEXT NOT NULL, `episodes` INTEGER NOT NULL, " +
                        "`score` INTEGER NOT NULL, `previousStatus` TEXT, `previousEpisodes` INTEGER NOT NULL, " +
                        "`previousScore` INTEGER NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_swipe_history_animeId` ON `swipe_history` (`animeId`)")
            }
        }

        /**
         * v6: the AniList link (entries with the base of the sync, changes to send, conflicts).
         * Empty until AniList is linked; when the link is switched off they stay, empty.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_entries` (`malId` INTEGER NOT NULL, `mediaId` INTEGER, " +
                        "`checkedAt` INTEGER NOT NULL, `entryId` INTEGER, `synced` INTEGER NOT NULL, `malStatus` TEXT, " +
                        "`malEpisodes` INTEGER NOT NULL, `malScore` INTEGER NOT NULL, `aniListStatus` TEXT, " +
                        "`aniListProgress` INTEGER NOT NULL, `aniListScoreRaw` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`malId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_outbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`malId` INTEGER NOT NULL, `status` TEXT, `progress` INTEGER NOT NULL, `scoreRaw` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_conflicts` (`malId` INTEGER NOT NULL, `title` TEXT, " +
                        "`malStatus` TEXT, `malEpisodes` INTEGER NOT NULL, `malScore` INTEGER NOT NULL, " +
                        "`aniListStatus` TEXT, `aniListProgress` INTEGER NOT NULL, `aniListScoreRaw` INTEGER NOT NULL, " +
                        "`foundAt` INTEGER NOT NULL, PRIMARY KEY(`malId`))"
                )
            }
        }

        /**
         * v7: the AniList sync log (sessions and their events, the changes to send in their
         * session) and the anime the user acknowledged as not on AniList.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `anilist_outbox` ADD COLUMN `sessionId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `state` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `endedAt` INTEGER, `notice` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_session_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `malId` INTEGER, " +
                        "`title` TEXT, `status` TEXT, `episodes` INTEGER NOT NULL, `score` INTEGER NOT NULL, " +
                        "`code` INTEGER, `count` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_anilist_session_events_sessionId` ON `anilist_session_events` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anilist_acknowledged` (`malId` INTEGER NOT NULL, `at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`malId`))"
                )
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

    @TypeConverter
    fun actionToString(action: SwipeAction): String = action.name

    @TypeConverter
    fun stringToAction(value: String): SwipeAction = SwipeAction.entries.firstOrNull { it.name == value } ?: SwipeAction.SKIPPED
}
