package com.mobilelinux.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class BrowserHistoryItem(
    val id: Long,
    val title: String,
    val url: String,
    val timestamp: Long
)

/**
 * Local SQLite storage for MobileLinux Dev Browser history.
 * Zero-cloud, strictly private on-device browsing record with manual cleanup.
 */
class BrowserHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                visited_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_visited_at ON history(visited_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS history")
        onCreate(db)
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url == "about:blank" || url.startsWith("data:")) return

        try {
            val displayTitle = if (title.isBlank() || title.startsWith("http://") || title.startsWith("https://")) {
                url
            } else {
                title
            }
            val now = System.currentTimeMillis()
            val db = writableDatabase

            // If identical URL visited, remove old row to bump to top
            db.delete("history", "url = ?", arrayOf(url))

            val values = ContentValues().apply {
                put("title", displayTitle)
                put("url", url)
                put("visited_at", now)
            }
            db.insert("history", null, values)

            // Keep table capped at max 1000 items to avoid storage growth
            db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC LIMIT 1000)")
        } catch (ignored: Exception) {}
    }

    fun getHistory(query: String? = null): List<BrowserHistoryItem> {
        val list = mutableListOf<BrowserHistoryItem>()
        try {
            val db = readableDatabase
            val cursor = if (query.isNullOrBlank()) {
                db.query(
                    "history",
                    arrayOf("id", "title", "url", "visited_at"),
                    null,
                    null,
                    null,
                    null,
                    "visited_at DESC",
                    "300"
                )
            } else {
                val q = "%${query.trim()}%"
                db.query(
                    "history",
                    arrayOf("id", "title", "url", "visited_at"),
                    "title LIKE ? OR url LIKE ?",
                    arrayOf(q, q),
                    null,
                    null,
                    "visited_at DESC",
                    "300"
                )
            }

            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow("id")
                val titleCol = c.getColumnIndexOrThrow("title")
                val urlCol = c.getColumnIndexOrThrow("url")
                val timeCol = c.getColumnIndexOrThrow("visited_at")

                while (c.moveToNext()) {
                    list.add(
                        BrowserHistoryItem(
                            id = c.getLong(idCol),
                            title = c.getString(titleCol),
                            url = c.getString(urlCol),
                            timestamp = c.getLong(timeCol)
                        )
                    )
                }
            }
        } catch (ignored: Exception) {}
        return list
    }

    fun deleteItem(id: Long): Boolean {
        return try {
            writableDatabase.delete("history", "id = ?", arrayOf(id.toString())) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun clearAllHistory(): Boolean {
        return try {
            writableDatabase.delete("history", null, null) >= 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val DATABASE_NAME = "dev_browser_history.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: BrowserHistoryDbHelper? = null

        fun getInstance(context: Context): BrowserHistoryDbHelper {
            return instance ?: synchronized(this) {
                instance ?: BrowserHistoryDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
