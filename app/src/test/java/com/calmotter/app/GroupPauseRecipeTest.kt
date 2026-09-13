package com.calmotter.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupPauseRecipeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun encodeThenDecodeRoundTrips() {
        val recipe = GroupPauseRecipe(
            durationMinutes = 30,
            startAtEpochMillis = now + 60_000L,
            groupTag = 12345,
        )
        val decoded = decodeGroupPauseRecipe(recipe.encode(), now = now)
        assertEquals(recipe.durationMinutes, decoded?.durationMinutes)
        assertEquals(recipe.startAtEpochMillis, decoded?.startAtEpochMillis)
        assertEquals(recipe.groupTag, decoded?.groupTag)
    }

    @Test
    fun corruptedCodeIsRejectedByChecksum() {
        val code = GroupPauseRecipe(30, now + 60_000L, 1).encode()
        // Cambia un carattere a metà stringa: il checksum non corrisponderà più.
        val tampered = code.toCharArray().also { it[3] = if (it[3] == 'A') 'B' else 'A' }.concatToString()
        assertNull(decodeGroupPauseRecipe(tampered, now = now))
    }

    @Test
    fun malformedCodeIsRejected() {
        assertNull(decodeGroupPauseRecipe("not-a-valid-code!!!", now = now))
        assertNull(decodeGroupPauseRecipe("", now = now))
    }

    @Test
    fun alreadyStartedRecipeIsRejected() {
        val code = GroupPauseRecipe(30, now - 1_000L, 1).encode()
        assertNull(decodeGroupPauseRecipe(code, now = now))
    }

    @Test
    fun tooFarInTheFutureIsRejected() {
        val code = GroupPauseRecipe(30, now + 60 * 60_000L, 1).encode()
        assertNull(decodeGroupPauseRecipe(code, now = now))
    }
}
