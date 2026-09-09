package com.gamesoundpro.app

import com.gamesoundpro.app.database.entity.SoundEntity
import com.gamesoundpro.app.domain.Category
import com.gamesoundpro.app.domain.LibraryFilter
import com.gamesoundpro.app.domain.SortOption
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {

    private fun sound(
        id: String,
        name: String,
        category: Category = Category.MEMES,
        packId: String? = null,
        favorite: Boolean = false,
        plays: Int = 0,
        createdAt: Long = 0,
        lastPlayedAt: Long? = null,
    ) = SoundEntity(
        id = id,
        name = name,
        filePath = "/data/$id.mp3",
        category = category.key,
        packId = packId,
        isFavorite = favorite,
        playCount = plays,
        createdAt = createdAt,
        lastPlayedAt = lastPlayedAt,
    )

    private val packNames = mapOf("p1" to "Troll Pack", "p2" to "Music Pack")

    @Test
    fun `search matches name case-insensitively`() {
        val sounds = listOf(sound("1", "BRUH"), sound("2", "OOF"))
        val result = LibraryFilter.apply(sounds, packNames, "bruh", null, SortOption.A_Z)
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search matches pack name`() {
        val sounds = listOf(sound("1", "Airhorn", packId = "p1"), sound("2", "OOF", packId = "p2"))
        val result = LibraryFilter.apply(sounds, packNames, "troll", null, SortOption.A_Z)
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun `search matches category label and key`() {
        val sounds = listOf(sound("1", "Airhorn", category = Category.EFFECTS), sound("2", "OOF"))
        assertEquals(listOf("1"), LibraryFilter.apply(sounds, packNames, "effects", null, SortOption.A_Z).map { it.id })
        assertEquals(listOf("1"), LibraryFilter.apply(sounds, packNames, "Effects", null, SortOption.A_Z).map { it.id })
    }

    @Test
    fun `category filter narrows results`() {
        val sounds = listOf(
            sound("1", "a", category = Category.MUSIC),
            sound("2", "b", category = Category.GAMING),
            sound("3", "c", category = Category.MUSIC),
        )
        val result = LibraryFilter.apply(sounds, packNames, "", Category.MUSIC, SortOption.A_Z)
        assertEquals(listOf("1", "3"), result.map { it.id })
    }

    @Test
    fun `most played sorts by play count`() {
        val sounds = listOf(
            sound("low", "low", plays = 2),
            sound("high", "high", plays = 10),
            sound("mid", "mid", plays = 5),
        )
        val result = LibraryFilter.apply(sounds, packNames, "", null, SortOption.MOST_PLAYED)
        assertEquals(listOf("high", "mid", "low"), result.map { it.id })
    }

    @Test
    fun `recently played puts never-played last`() {
        val sounds = listOf(
            sound("old", "old", lastPlayedAt = 100),
            sound("new", "new", lastPlayedAt = 900),
            sound("never", "never", lastPlayedAt = null),
        )
        val result = LibraryFilter.apply(sounds, packNames, "", null, SortOption.RECENTLY_PLAYED)
        assertEquals(listOf("new", "old", "never"), result.map { it.id })
    }

    @Test
    fun `a-z sorts case-insensitively`() {
        val sounds = listOf(sound("1", "banana"), sound("2", "Apple"), sound("3", "cherry"))
        val result = LibraryFilter.apply(sounds, packNames, "", null, SortOption.A_Z)
        assertEquals(listOf("2", "1", "3"), result.map { it.id })
    }
}
