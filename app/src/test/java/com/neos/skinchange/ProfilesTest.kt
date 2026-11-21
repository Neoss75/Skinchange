package com.neos.skinchange

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesTest {

    @Test
    fun nextIdTakesTheSmallestFreeNumber() {
        assertEquals("1", Profiles.nextId(emptyList()))
        assertEquals("2", Profiles.nextId(listOf("1")))
        assertEquals("2", Profiles.nextId(listOf("1", "3")))
        assertEquals("4", Profiles.nextId(listOf("3", "1", "2")))
    }
}
