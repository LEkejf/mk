package tigre.illusion

import org.bukkit.plugin.java.JavaPlugin
import tigre.illusion.commands.MinaCommand
import tigre.illusion.listeners.MineListener
import tigre.illusion.managers.MineManager

class IllusionPlugin : JavaPlugin() {

    lateinit var mineManager: MineManager

    override fun onEnable() {
        mineManager = MineManager(this)

        server.pluginManager.registerEvents(MineListener(this), this)
        getCommand("mina")?.setExecutor(MinaCommand(this))

        logger.info("M-Mina carregado com sucesso")
    }

    override fun onDisable() {
        mineManager.activeSessions.clear()
    }
}