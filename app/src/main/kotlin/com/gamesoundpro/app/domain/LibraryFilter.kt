package com.gamesoundpro.app.domain

import com.gamesoundpro.app.database.entity.SoundEntity

/**
 * Pure search / filter / sort logic shared by the soundboard and My Sounds screens
 * (kept dependency-free so it can be unit-tested).
 * Search matches sound name, category (label or key) and pack name.
 */
object LibraryFilter {

    fun apply(
        sounds: List<SoundEntity>,
        packNames: Map<String, String>,
        query: String,
        category: Category?,
        sort: SortOption,
    ): List<SoundEntity> {
        val q = query.trim()
        val filtered = sounds.filter { sound ->
            val matchesCategory = category == null || sound.category == category.key
            val matchesQuery = q.isBlank() ||
                sound.name.contains(q, ignoreCase = true) ||
                sound.category.contains(q, ignoreCase = true) ||
                Category.fromKey(sound.category).label.contains(q, ignoreCase = true) ||
                (sound.packId != null && packNames[sound.packId]?.contains(q, ignoreCase = true) == true)
            matchesCategory && matchesQuery
        }
        return sort.sort(filtered)
    }
}
