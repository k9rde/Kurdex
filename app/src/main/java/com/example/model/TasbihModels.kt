package com.example.model

data class TasbihItem(
    val id: String,
    val titleTr: String,
    val titleAr: String,
    val meaningTr: String,
    val target: Int = 33,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val lapCount: Int = 0,
    val isCustom: Boolean = false,
    val category: String = "Genel"
)

data class TasbihHistoryRecord(
    val dateStr: String,
    val dhikrTitle: String,
    val count: Int,
    val timestamp: Long
)
