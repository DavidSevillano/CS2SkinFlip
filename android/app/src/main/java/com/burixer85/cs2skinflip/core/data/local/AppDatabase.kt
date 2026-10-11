package com.burixer85.cs2skinflip.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WatchlistEntity::class],
    version = 2,
    // Kept in app/schemas/ so every future migration can be checked against
    // the exact schema it starts from.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        /**
         * Every version bump needs one of these. The database used to fall back
         * to destructive migration, which wiped the user's watchlist on any
         * entity change.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // The local alerts table was never read: alerts live on the backend.
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS alerts")
                }
            },
        )
    }
}
