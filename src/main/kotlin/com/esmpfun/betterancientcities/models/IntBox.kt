package com.esmpfun.betterancientcities.models

import org.bukkit.Location
import org.bukkit.util.BoundingBox
import kotlin.math.floor

/** An inclusive block-coordinate box. The owning [City] carries the world. */
data class IntBox(
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
) {
    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in minX..maxX && y in minY..maxY && z in minZ..maxZ

    fun contains(loc: Location): Boolean = contains(loc.blockX, loc.blockY, loc.blockZ)

    fun expanded(pad: Int): IntBox =
        IntBox(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad)

    companion object {
        /**
         * Structure and structure-piece boxes come from the server with an inclusive
         * max corner (CraftStructurePiece copies the block box as-is), so no -1 here.
         */
        fun fromBukkit(b: BoundingBox): IntBox = IntBox(
            floor(b.minX).toInt(), floor(b.minY).toInt(), floor(b.minZ).toInt(),
            floor(b.maxX).toInt(), floor(b.maxY).toInt(), floor(b.maxZ).toInt(),
        )

        /** The smallest box enclosing all of [boxes]. Throws on an empty list. */
        fun union(boxes: List<IntBox>): IntBox {
            require(boxes.isNotEmpty()) { "cannot union zero boxes" }
            return IntBox(
                boxes.minOf { it.minX }, boxes.minOf { it.minY }, boxes.minOf { it.minZ },
                boxes.maxOf { it.maxX }, boxes.maxOf { it.maxY }, boxes.maxOf { it.maxZ },
            )
        }
    }
}
