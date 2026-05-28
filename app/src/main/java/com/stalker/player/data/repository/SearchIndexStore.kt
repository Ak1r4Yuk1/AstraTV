package com.stalker.player.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.stalker.player.data.model.Channel

class SearchIndexStore(context: Context) : SQLiteOpenHelper(
    context,
    DB_NAME,
    null,
    DB_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE search_index (
                portal_key TEXT NOT NULL,
                item_type TEXT NOT NULL,
                item_id TEXT NOT NULL,
                name TEXT NOT NULL,
                cmd TEXT NOT NULL DEFAULT '',
                screenshot_uri TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                year TEXT NOT NULL DEFAULT '',
                movie_id TEXT NOT NULL DEFAULT '',
                series_id TEXT NOT NULL DEFAULT '',
                stream_url TEXT NOT NULL DEFAULT '',
                stream_format TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (portal_key, item_type, item_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_search_lookup ON search_index(portal_key, item_type, name COLLATE NOCASE)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS search_index")
        onCreate(db)
    }

    fun replaceIndex(portalKey: String, itemType: String, items: List<Channel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                "search_index",
                "portal_key = ? AND item_type = ?",
                arrayOf(portalKey, itemType)
            )
            items.forEach { item ->
                val itemId = item.id.ifBlank { item.seriesId.ifBlank { item.movieId } }
                if (itemId.isBlank() || item.name.isBlank()) return@forEach
                db.insertWithOnConflict(
                    "search_index",
                    null,
                    ContentValues().apply {
                        put("portal_key", portalKey)
                        put("item_type", itemType)
                        put("item_id", itemId)
                        put("name", item.name)
                        put("cmd", item.cmd)
                        put("screenshot_uri", item.screenshotUri)
                        put("description", item.description)
                        put("year", item.year)
                        put("movie_id", item.movieId)
                        put("series_id", item.seriesId)
                        put("stream_url", item.streamUrl)
                        put("stream_format", item.streamFormat)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun search(portalKey: String, itemType: String, query: String, limit: Int = 200): List<Channel> {
        if (query.isBlank()) return emptyList()
        val escaped = buildString(query.length) {
            query.forEach { ch ->
                when (ch) {
                    '%', '_' -> append('\\').append(ch)
                    else -> append(ch)
                }
            }
        }
        val db = readableDatabase
        val cursor = db.query(
            "search_index",
            arrayOf(
                "item_id",
                "name",
                "cmd",
                "screenshot_uri",
                "description",
                "year",
                "movie_id",
                "series_id",
                "stream_url",
                "stream_format"
            ),
            "portal_key = ? AND item_type = ? AND name LIKE ? ESCAPE '\\'",
            arrayOf(portalKey, itemType, "%$escaped%"),
            null,
            null,
            "name COLLATE NOCASE ASC",
            limit.toString()
        )
        cursor.use {
            val results = mutableListOf<Channel>()
            while (it.moveToNext()) {
                results += Channel(
                    id = it.getString(0).orEmpty(),
                    name = it.getString(1).orEmpty(),
                    cmd = it.getString(2).orEmpty(),
                    itemType = itemType,
                    screenshotUri = it.getString(3).orEmpty(),
                    description = it.getString(4).orEmpty(),
                    year = it.getString(5).orEmpty(),
                    movieId = it.getString(6).orEmpty(),
                    seriesId = it.getString(7).orEmpty(),
                    streamUrl = it.getString(8).orEmpty(),
                    streamFormat = it.getString(9).orEmpty()
                )
            }
            return results
        }
    }

    fun clearPortal(portalKey: String) {
        writableDatabase.delete("search_index", "portal_key = ?", arrayOf(portalKey))
    }

    companion object {
        private const val DB_NAME = "search_index.db"
        private const val DB_VERSION = 1
    }
}
