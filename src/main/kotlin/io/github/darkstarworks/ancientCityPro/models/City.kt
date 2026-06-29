package io.github.darkstarworks.ancientCityPro.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World

/**
 * A registered Ancient City.
 *
 * [region] is the envelope AABB (union of all structure pieces, possibly
 * Y-clamped). [origin] is the raw structure bounding-box min corner — the stable
 * identity used to dedup the same city seen across many chunk loads. [pieces]
 * are the per-structure-piece bounds used for exact chest provenance: a block
 * counts as city content only if it falls inside one of these.
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
) {
    fun getWorld(): World? = Bukkit.getWorld(world)

    /** Whether [loc] is anywhere within the city's envelope (protection-box test). */
    fun containsInRegion(loc: Location): Boolean =
        loc.world?.name == world && region.contains(loc)

    /**
     * Whether [loc] is inside an actual generated structure piece — the test that
     * decides if a container at [loc] is city loot vs. a player-built block.
     */
    fun inStructurePiece(loc: Location): Boolean {
        if (loc.world?.name != world) return false
        val x = loc.blockX; val y = loc.blockY; val z = loc.blockZ
        return pieces.any { it.contains(x, y, z) }
    }
}
