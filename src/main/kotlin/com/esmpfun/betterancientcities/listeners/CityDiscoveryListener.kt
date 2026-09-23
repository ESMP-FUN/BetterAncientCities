package com.esmpfun.betterancientcities.listeners

import com.esmpfun.betterancientcities.BetterAncientCities
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.generator.structure.Structure

/** Asks the server, on each chunk load, whether an Ancient City overlaps that chunk. */
class CityDiscoveryListener(private val plugin: BetterAncientCities) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!plugin.isReady) return
        val world = event.world
        if (world.environment != World.Environment.NORMAL) return
        val discovery = plugin.discoveryManager
        if (!discovery.enabled() || discovery.excluded(world)) return
        val structures = world.getStructures(event.chunk.x, event.chunk.z, Structure.ANCIENT_CITY)
        for (gs in structures) discovery.handle(world, gs)
    }
}
