package tigre.illusion.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import tigre.illusion.IllusionPlugin

class MinaCommand(private val plugin: IllusionPlugin) : CommandExecutor {

    private val prefix = "§6§lMINA §8» §f"

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return true

        if (args.isEmpty()) {
            plugin.mineManager.sendToMine(sender)
            sender.sendMessage("$prefix§aConectando à sua instância privada...")
            return true
        }

        when (args[0].lowercase()) {
            "sair" -> {
                val manager = plugin.mineManager
                val uuid = sender.uniqueId

                if (manager.spectators.containsKey(uuid)) {
                    manager.stopSpectating(sender)

                    val exitLocation = Bukkit.getWorld("world")?.spawnLocation ?: sender.world.spawnLocation
                    sender.teleport(exitLocation)
                    return true
                }

                if (manager.activeSessions.containsKey(uuid)) {
                    manager.cleanupPlayer(sender)

                    val exitLocation = Bukkit.getWorld("world")?.spawnLocation ?: sender.world.spawnLocation
                    sender.teleport(exitLocation)

                    sender.sendMessage("$prefix§cVocê saiu da mina.")
                    sender.playSound(sender.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f)
                    return true
                }

                sender.sendMessage("$prefix§cVocê não está em uma sessão de mina ativa ou espectando.")
            }
            "resetar" -> {
                val session = plugin.mineManager.activeSessions[sender.uniqueId]
                if (session != null) {
                    plugin.mineManager.refreshMine(sender, session, true)
                    sender.sendMessage("$prefix§aSua mina foi §lREGENERADA §acom sucesso!")
                    sender.playSound(sender.location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f)
                } else {
                    sender.sendMessage("§c§lERRO! §7Você precisa estar dentro da mina para resetar.")
                    sender.playSound(sender.location, org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f)
                }
            }
            "espectar" -> {
                if (!sender.isOp) {
                    sender.sendMessage("$prefix§cErro! Apenas administradores podem usar isto.")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage("$prefix§cUse: /mina espectar <jogador>")
                    return true
                }
                val target = Bukkit.getPlayer(args[1])
                if (target == null || !plugin.mineManager.activeSessions.containsKey(target.uniqueId)) {
                    sender.sendMessage("$prefix§cJogador não encontrado ou não está na mina.")
                    return true
                }
                plugin.mineManager.startSpectating(sender, target)
                sender.sendMessage("$prefix§eModo espectador ativado em §f${target.name}§e.")
            }

            "visivel" -> {
                if (!sender.isOp) return true
                Bukkit.getOnlinePlayers().forEach { it.showPlayer(plugin, sender) }
                sender.sendMessage("$prefix§aVocê agora está §lVISÍVEL §apara todos.")
                sender.playSound(sender.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f)
            }
            else -> {
                sender.sendMessage("§c§lCOMANDO INVÁLIDO!")
                sender.sendMessage("§7Use: §f/mina§7, §f/mina sair §7ou §f/mina resetar")
            }
        }
        return true
    }
}