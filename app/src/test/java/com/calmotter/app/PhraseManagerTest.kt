package com.calmotter.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhraseManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        PhraseManager.resetInstanceForTests()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun defaultIsEnabled() {
        val manager = PhraseManager.getInstance(context)
        assertTrue(manager.isEnabled())
    }

    @Test
    fun disablingPersistsAndRandomPhraseReturnsNull() {
        val manager = PhraseManager.getInstance(context)

        manager.setEnabled(false)

        assertFalse(manager.isEnabled())
        assertNull(manager.randomPhrase())
    }

    @Test
    fun enablingReturnsNonBlankPhrase() {
        val manager = PhraseManager.getInstance(context)

        manager.setEnabled(true)
        val phrase = manager.randomPhrase()

        assertNotNull(phrase)
        assertTrue(phrase!!.isNotBlank())
    }
}
