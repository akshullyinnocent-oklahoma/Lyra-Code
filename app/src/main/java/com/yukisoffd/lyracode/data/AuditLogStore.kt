package com.yukisoffd.lyracode.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AuditEntry(
    val id: Long,
    val createdAt: Long,
    val kind: String,
    val title: String,
    val detail: String,
)

class AuditLogStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "lyra_audit.db",
    null,
    1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                detail TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(kind: String, title: String, detail: String) {
        writableDatabase.insert(
            "audit_log",
            null,
            ContentValues().apply {
                put("created_at", System.currentTimeMillis())
                put("kind", kind.take(48))
                put("title", title.take(256))
                put("detail", detail.take(500_000))
            },
        )
    }

    fun recent(limit: Int = 100): List<AuditEntry> {
        val cursor = readableDatabase.query(
            "audit_log",
            arrayOf("id", "created_at", "kind", "title", "detail"),
            null,
            null,
            null,
            null,
            "id DESC",
            limit.coerceIn(1, 500).toString(),
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    add(
                        AuditEntry(
                            id = it.getLong(0),
                            createdAt = it.getLong(1),
                            kind = it.getString(2),
                            title = it.getString(3),
                            detail = it.getString(4),
                        ),
                    )
                }
            }
        }
    }

    fun clear() {
        writableDatabase.delete("audit_log", null, null)
    }
}
