package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.EntityType
import com.example.data.model.RelationshipType

class Converters {
    @TypeConverter
    fun fromEntityType(value: EntityType): String = value.name

    @TypeConverter
    fun toEntityType(value: String): EntityType = runCatching {
        EntityType.valueOf(value)
    }.getOrDefault(EntityType.OTHER)

    @TypeConverter
    fun fromRelationshipType(value: RelationshipType): String = value.name

    @TypeConverter
    fun toRelationshipType(value: String): RelationshipType = runCatching {
        RelationshipType.valueOf(value)
    }.getOrDefault(RelationshipType.SEMANTIC_SIMILARITY)
}
