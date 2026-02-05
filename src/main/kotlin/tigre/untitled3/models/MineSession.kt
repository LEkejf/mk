package tigre.illusion.models

import org.bukkit.Material
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

data class MineSession(
    val playerUUID: UUID,
    var level: Int = 1,
    val virtualBlocks: MutableMap<Long, Material> = HashMap(),

    // Controle de mineração
    var currentMiningKey: Long? = null,
    var miningProgress: Float = 0f,
    var miningTask: BukkitTask? = null,
    var vortexActive: Boolean = false
) {
    fun getRadius(): Int = 4

    fun cancelMining() {
        miningTask?.cancel()
        miningTask = null
        currentMiningKey = null
        miningProgress = 0f
    }
}