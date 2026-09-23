package com.esmpfun.betterancientcities.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * A registered Ancient City. [region] encloses all [pieces]; [origin] is the
 * structure's min corner, the identity every chunk of the city agrees on. A block
 * belongs to the city only if it falls inside one of the [pieces].
 */
data class City(
    val id: Int,
    val world: String,
    val region: IntBox,
    val origin: Triple<Int, Int, Int>,
    val pieces: List<IntBox>,
    val createdAt: Long,
    val lastReset: Long? = null,
    val snapshotFile: String? = null,
    /** False while waiting for approval; loot and protection stay off until then. */
    val approved: Boolean = false,
) {
    fun getWorld(): World? = Bukkit.getWorld(world)

    fun containsInRegion(loc: Location): Boolean = containsInPaddedRegion(loc, 0)

    fun containsInPaddedRegion(loc: Location, pad: Int): Boolean {
        if (loc.world?.name != world) return false
        return within(region, loc.blockX, loc.blockY, loc.blockZ, pad)
    }

    fun inStructurePiece(loc: Location): Boolean = inStructurePiece(loc, 0)

    /** Whether [loc] is inside a structure piece grown by [pad] blocks on every side. */
    fun inStructurePiece(loc: Location, pad: Int): Boolean {
        if (loc.world?.name != world) return false
        val x = loc.blockX; val y = loc.blockY; val z = loc.blockZ
        return pieces.any { within(it, x, y, z, pad) }
    }

    private fun within(b: IntBox, x: Int, y: Int, z: Int, pad: Int): Boolean =
        x >= b.minX - pad && x <= b.maxX + pad &&
            y >= b.minY - pad && y <= b.maxY + pad &&
            z >= b.minZ - pad && z <= b.maxZ + pad
}
