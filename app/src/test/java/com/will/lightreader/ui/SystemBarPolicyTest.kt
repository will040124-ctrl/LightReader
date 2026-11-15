package com.will.lightreader.ui

import org.junit.Assert.*
import org.junit.Test

class SystemBarPolicyTest {
    private val navigationOnly = SystemBarPolicy(true, false)
    private val fullscreen = SystemBarPolicy(true, true)

    @Test fun `outgoing reader updates keep the entering reader policy`() {
        val owners = SystemBarOwners()
        owners.update("outgoing", navigationOnly, true)
        owners.update("entering", fullscreen, true)
        owners.update("outgoing", SystemBarPolicy(false, false), true)
        assertEquals(fullscreen, owners.current)
        owners.remove("outgoing")
        assertEquals(fullscreen, owners.current)
    }

    @Test fun `paused owners yield and resume without losing their place`() {
        val owners = SystemBarOwners()
        owners.update("reader", navigationOnly, true)
        owners.update("overlay", fullscreen, true)
        owners.update("overlay", fullscreen, false)
        assertEquals(navigationOnly, owners.current)
        owners.update("reader", navigationOnly, false)
        assertNull(owners.current)
        owners.update("overlay", fullscreen, true)
        assertEquals(fullscreen, owners.current)
    }

    @Test fun `window baseline can be restored after the final owner leaves`() {
        val owners = SystemBarOwners()
        owners.update("reader", fullscreen, true)
        owners.remove("reader")
        assertTrue(owners.isEmpty)
        assertNull(owners.current)
    }
}
