package com.esmpfun.betterancientcities.models

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.util.BoundingBox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CityBoundsTest {

    // The server hands structure boxes over with the max corner already inclusive.
    @Test
    fun `structure box keeps its far edge`() {
        val box = IntBox.fromBukkit(BoundingBox(-120.0, -51.0, 300.0, -101.0, -40.0, 318.0))
        assertEquals(IntBox(-120, -51, 300, -101, -40, 318), box)
        assertTrue(box.contains(-101, -40, 318))
        assertFalse(box.contains(-100, -40, 318))
    }

    @Test
    fun `piece padding reaches around every side`() {
        val world = mockk<World> { every { name } returns "world" }
        val other = mockk<World> { every { name } returns "other" }
        val piece = IntBox(0, -50, 0, 9, -45, 9)
        val city = City(1, "world", piece, Triple(0, -50, 0), listOf(piece), 0L, approved = true)
        assertTrue(city.inStructurePiece(Location(world, 9.0, -45.0, 9.0)))
        assertTrue(city.inStructurePiece(Location(world, -3.0, -53.0, 12.0), 3))
        assertFalse(city.inStructurePiece(Location(world, -4.0, -50.0, 0.0), 3))
        assertFalse(city.inStructurePiece(Location(world, 10.0, -50.0, 0.0)))
        assertFalse(city.inStructurePiece(Location(other, 5.0, -48.0, 5.0)))
        assertTrue(city.containsInPaddedRegion(Location(world, 12.0, -50.0, 0.0), 3))
    }
}
