package com.photoselectortoolbox.data.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "technique_facts",
    primaryKeys = ["uri", "kind"],
    indices = [
        Index(value = ["kind"], name = "idx_technique_facts_kind"),
        Index(value = ["uri", "fileSize", "lastModified", "detectorVersion"], name = "idx_technique_facts_cache_key"),
    ],
)
data class TechniqueFactEntity(
    val uri: String,
    val kind: String,
    val value: String,
    val confidence: Double,
    val geometryJson: String? = null,
    val fileSize: Long,
    val lastModified: Long,
    val detectorVersion: Int,
)
