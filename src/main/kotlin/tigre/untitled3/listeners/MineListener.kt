package tigre.illusion.listeners

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import tigre.illusion.IllusionPlugin

class MineListener(private val plugin: IllusionPlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEvent) {
        val player = e.player
        val session = plugin.mineManager.activeSessions[player.uniqueId] ?: return

        if (e.action != Action.LEFT_CLICK_AIR && e.action != Action.LEFT_CLICK_BLOCK) return

        val item = player.inventory.itemInMainHand
        if (!item.type.toString().endsWith("_PICKAXE")) return

        val eyeLoc = player.eyeLocation
        val direction = eyeLoc.direction.multiply(0.2)
        val currentPos = eyeLoc.toVector()

        val world = player.world

        for (i in 1..25) {
            currentPos.add(direction)

            val blockX = currentPos.blockX
            val blockY = currentPos.blockY
            val blockZ = currentPos.blockZ

            val key = plugin.mineManager.toKey(blockX, blockY, blockZ)

            if (session.virtualBlocks.containsKey(key)) {
                e.isCancelled = true
                plugin.mineManager.breakBlock(player, blockX, blockY, blockZ, key)
                player.swingMainHand()
                return
            }
        }
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        plugin.mineManager.cleanupPlayer(e.player)
    }

    @EventHandler
    fun onHunger(e: FoodLevelChangeEvent) {
        if (plugin.mineManager.activeSessions.containsKey(e.entity.uniqueId)) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onDamage(e: EntityDamageEvent) {
        if (plugin.mineManager.activeSessions.containsKey(e.entity.uniqueId)) {
            e.isCancelled = true
        }
    }
}