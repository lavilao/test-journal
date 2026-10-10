package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A USER-MADE tool for the local Needle assistant — the in-app answer to
 * "let me build my own tools" (https://cactuscompute.com/blog/designing-tools-for-needle).
 *
 * Design rules baked into the creator UI, straight from Cactus' guide:
 *  - one tool per action (narrow beats broad);
 *  - every argument must be a span of what the user says, so params carry a
 *    "where in the sentence" description and optional defaults;
 *  - names the user would actually say (snake_case, unique);
 *  - description states the ACTIONS covered, not a category.
 *
 * [paramsJson]: [{"name":"texto","type":"string","description":"...","required":true,"default":""}]
 * [action]: note | task | event | append_note | open_url | reply
 * [actionConfig]: template; first rendered line is the title (note/task/event),
 * whole render is the body; for open_url it is the URL template.
 */
@Entity(
    tableName = "custom_tools",
    indices = [Index(value = ["name"], unique = true)]
)
data class CustomTool(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val label: String,
    val description: String,
    val paramsJson: String = "[]",
    val action: String = "note",
    val actionConfig: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
