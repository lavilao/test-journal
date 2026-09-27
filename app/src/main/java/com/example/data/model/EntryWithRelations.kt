package com.example.data.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class EntryWithRelations(
    @Embedded
    val entry: JournalEntry,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryEntityCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "entityId"
        )
    )
    val entities: List<EntityItem> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val mediaItems: List<MediaItem> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val entityMentions: List<EntityMention> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val suggestedTags: List<SuggestedTag> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val pages: List<JournalPage> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val audioRecords: List<AudioRecordItem> = emptyList()
)

data class RelatedEntryDetail(
    val entry: JournalEntry,
    val relationship: Relationship
)

data class EntityWithEntries(
    @Embedded
    val entity: EntityItem,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryEntityCrossRef::class,
            parentColumn = "entityId",
            entityColumn = "entryId"
        )
    )
    val entries: List<JournalEntry> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "entityId"
    )
    val mentions: List<EntityMention> = emptyList()
)

// Graph Models for Visual Canvas
enum class GraphNodeType {
    ENTRY,
    PERSON,
    PLACE,
    ORGANIZATION,
    TOPIC,
    TAG,
    EVENT,
    PROJECT
}

data class GraphNode(
    val id: String,
    val rawId: Long,
    val label: String,
    val type: GraphNodeType,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val size: Float = 28f
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val label: String = "",
    val weight: Float = 1.0f
)

data class KnowledgeGraphData(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList()
)

data class HybridSearchResult(
    val entry: JournalEntry,
    val matchedScore: Float,
    val matchedReason: String,
    val snippet: String,
    val matchedTags: List<String> = emptyList(),
    val matchedEntities: List<String> = emptyList(),
    val matchedOcrTerms: List<String> = emptyList()
)
