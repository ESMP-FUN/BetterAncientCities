package io.github.darkstarworks.ancientCityPro.managers

import org.bukkit.Material

/**
 * The set of block types considered Ancient City *structure* material — what
 * protection guards.
 *
 * Deliberately EXCLUDES blocks that also generate naturally underground (plain
 * `DEEPSLATE`, `COBBLED_DEEPSLATE` + its stairs/slab/wall), mirroring TCP's
 * discovery predicate: this is what makes palette-aware protection work. Inside
 * the padded city envelope, only worked/structural blocks here are protected;
 * the natural deepslate, ores, and caves in the margin stay fully mineable.
 *
 * Used together with a city's padded region: a block is protected iff it's
 * inside the padded envelope AND its type is in [BLOCKS].
 */
object CityPalette {

    val BLOCKS: Set<Material> = buildSet {
        // Worked deepslate (bricks / tiles / polished / chiseled / reinforced).
        // NOT plain DEEPSLATE or COBBLED_DEEPSLATE — those generate naturally.
        addAll(
            listOf(
                Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
                Material.DEEPSLATE_BRICK_STAIRS, Material.DEEPSLATE_BRICK_SLAB, Material.DEEPSLATE_BRICK_WALL,
                Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES,
                Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.DEEPSLATE_TILE_WALL,
                Material.POLISHED_DEEPSLATE, Material.POLISHED_DEEPSLATE_STAIRS,
                Material.POLISHED_DEEPSLATE_SLAB, Material.POLISHED_DEEPSLATE_WALL,
                Material.CHISELED_DEEPSLATE,
                Material.REINFORCED_DEEPSLATE,
            )
        )
        // Sculk family.
        addAll(
            listOf(
                Material.SCULK, Material.SCULK_VEIN, Material.SCULK_SENSOR,
                Material.SCULK_SHRIEKER, Material.SCULK_CATALYST,
            )
        )
        // Soul-themed lighting / blocks.
        addAll(
            listOf(
                Material.SOUL_SAND, Material.SOUL_SOIL, Material.SOUL_LANTERN,
                Material.SOUL_TORCH, Material.SOUL_WALL_TORCH, Material.SOUL_FIRE,
                Material.SOUL_CAMPFIRE,
            )
        )
        // Wool + carpet decoration (the palette the centre/structures use).
        addAll(
            listOf(
                Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.CYAN_WOOL,
                Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.BLACK_WOOL,
                Material.LIGHT_BLUE_CARPET, Material.BLUE_CARPET, Material.CYAN_CARPET,
                Material.GRAY_CARPET, Material.LIGHT_GRAY_CARPET, Material.BLACK_CARPET,
            )
        )
        // Misc fixtures / decoration.
        addAll(
            listOf(
                Material.CHAIN, Material.LANTERN, Material.CANDLE, Material.COBWEB,
            )
        )
        // Loot containers (also guarded by the loot system, but protect the block too).
        addAll(
            listOf(
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            )
        )
    }

    fun contains(material: Material): Boolean = material in BLOCKS
}
