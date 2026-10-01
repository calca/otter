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

    // --- specs/together-activity/ ---------------------------------------

    @Test
    fun theProposedActivityTravelsWithTheRecipe() {
        val now = 1_700_000_000_000L
        val recipe = GroupPauseRecipe(60, now + 60_000L, 42, activityId = 16)
        val code = recipe.encode()
        assertEquals(14, code.length)
        assertEquals(recipe.copy(startAtEpochMillis = (recipe.startAtEpochMillis / 1000) * 1000), decodeGroupPauseRecipe(code, now = now))
    }

    @Test
    fun anActivityThisPhoneDoesNotKnowIsRejected() {
        val now = 1_700_000_000_000L
        val code = GroupPauseRecipe(60, now + 60_000L, 42, activityId = 250).encode()
        assertNull(decodeGroupPauseRecipe(code, now = now))
    }

    @Test
    fun aCodeFromAnOlderVersionSaysSo() {
        val now = 1_700_000_000_000L
        val bytes = java.nio.ByteBuffer.allocate(8)
            .putInt(((now + 60_000L) / 1000).toInt()).put(30.toByte()).putShort(1).array()
        var xor = 0
        for (i in 0 until 7) xor = xor xor bytes[i].toInt()
        bytes[7] = xor.toByte()
        val oldCode = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertNull(decodeGroupPauseRecipe(oldCode, now = now))
        // ...e chi lo legge viene invitato ad aggiornare, non a riprovare.
        assertEquals(RecipeDecodeResult.OtherVersion, decodeGroupPauseRecipeResult(oldCode, now = now))
    }

    @Test
    fun aMistypedCodeIsInvalidNotAnotherVersion() {
        val now = 1_700_000_000_000L
        val code = GroupPauseRecipe(30, now + 60_000L, 1).encode()
        val typo = code.substring(0, 5) + (if (code[5] == 'A') 'B' else 'A') + code.substring(6)
        assertEquals(RecipeDecodeResult.Invalid, decodeGroupPauseRecipeResult(typo, now = now))
        assertEquals(RecipeDecodeResult.Invalid, decodeGroupPauseRecipeResult("ciao", now = now))
    }
}
