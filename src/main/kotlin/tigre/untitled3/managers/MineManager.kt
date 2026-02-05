package tigre.illusion.managers

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import tigre.illusion.IllusionPlugin
import tigre.illusion.models.MineSession
import java.time.Duration
import java.util.*
import kotlin.math.max
import kotlin.math.min
import net.kyori.adventure.text.minimessage.MiniMessage

class MineManager(private val plugin: IllusionPlugin) {

    val activeSessions = HashMap<UUID, MineSession>()
    val spectators = HashMap<UUID, UUID>()

    val world = (Bukkit.getWorld("minecraft:overworld") ?: Bukkit.getWorld("world"))
        ?: throw IllegalStateException("Mundo principal não encontrado!")

    // --- CONFIGURAÇÕES DA MINA ---
    val spawnLocation = Location(world, 32.498, 165.0, 125.693, 0.0f, 0f)
    private val corner1 = Location(world, 42.336, 165.0, 138.647)
    private val corner2 = Location(world, 19.472, 176.0, 153.368)

    private val minX = min(corner1.blockX, corner2.blockX)
    private val maxX = max(corner1.blockX, corner2.blockX)
    private val minY = min(corner1.blockY, corner2.blockY)
    private val maxY = max(corner1.blockY, corner2.blockY)
    private val minZ = min(corner1.blockZ, corner2.blockZ)
    private val maxZ = max(corner1.blockZ, corner2.blockZ)

    private val centerX = (minX + maxX) / 2.0
    private val centerZ = (minZ + maxZ) / 2.0

    private val VORTEX_LOCATION = Location(world, 14.221, 182.53, 144.097)
    private val VORTEX_CHANCE = 0.15

    fun toKey(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x3FFFFFF) or ((z.toLong() and 0x3FFFFFF) shl 26) or ((y.toLong() and 0xFFF) shl 52)
    }

    fun fromKey(key: Long): Vector {
        val x = (key shl 38 shr 38).toInt()
        val y = (key ushr 52).toInt()
        val z = (key shl 12 shr 38).toInt()
        return Vector(x, y, z)
    }

    fun triggerVortex(player: Player, session: MineSession) {
        if (session.vortexActive) return
        session.vortexActive = true

        val initialBlocks = session.virtualBlocks.size
        val maxPullLimit = (initialBlocks * 0.85).toInt().coerceAtLeast(1)
        var totalPulledInThisEvent = 0

        // --- DEFINIÇÃO ---
        val movementRadius = 2.0
        val limitMinX = centerX - movementRadius
        val limitMaxX = centerX + movementRadius
        val limitMinZ = centerZ - movementRadius
        val limitMaxZ = centerZ + movementRadius

        val group = getRecipientGroup(player)
        val randomAngle = Math.random() * Math.PI * 2
        var currentDriftX = Math.cos(randomAngle) * 0.15
        var currentDriftZ = Math.sin(randomAngle) * 0.15

        val mm = MiniMessage.miniMessage()

        group.forEach {

            val mainTitle = mm.deserialize("<gradient:#FF5555:#FFAA00><b>VÓRTICE</b></gradient>")

            val subTitle = mm.deserialize("<gray>A instabilidade atingiu a mina de <white>${it.name}</white>!")

            val titleTimes = Title.Times.times(
                java.time.Duration.ofMillis(500),
                java.time.Duration.ofMillis(3000),
                java.time.Duration.ofMillis(500)
            )

            val title = Title.title(mainTitle, subTitle, titleTimes)

            it.showTitle(title)

            it.playSound(it.location, org.bukkit.Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f)
        }

        updateVortexHole(player, Material.END_PORTAL)

        val spawnedItems = mutableListOf<org.bukkit.entity.Item>()
        var ticks = 0
        val tornadoDuration = 230

        var currentTornadoY = maxY.toDouble() + 3.0
        var currentTornadoX = centerX
        var currentTornadoZ = centerZ

        object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!player.isOnline || !activeSessions.containsKey(player.uniqueId)) {
                    cleanupVortex(player, spawnedItems, session)
                    this.cancel()
                    return
                }

                val tornadoActive = ticks <= tornadoDuration

                if (currentTornadoY > minY) currentTornadoY -= 0.08

                val nextX = currentTornadoX + currentDriftX + (Math.sin(ticks * 0.1) * 0.05)
                if (nextX <= limitMinX || nextX >= limitMaxX) {
                    currentDriftX *= -1.0
                }

                val nextZ = currentTornadoZ + currentDriftZ + (Math.cos(ticks * 0.1) * 0.05)
                if (nextZ <= limitMinZ || nextZ >= limitMaxZ) {
                    currentDriftZ *= -1.0
                }

                currentTornadoX = (currentTornadoX + currentDriftX + (Math.sin(ticks * 0.1) * 0.05))
                    .coerceIn(limitMinX, limitMaxX)
                currentTornadoZ = (currentTornadoZ + currentDriftZ + (Math.cos(ticks * 0.1) * 0.05))
                    .coerceIn(limitMinZ, limitMaxZ)

                val tornadoLoc = Location(world, currentTornadoX, currentTornadoY, currentTornadoZ)

                if (ticks % 25 == 0 && tornadoActive) {
                    group.forEach { recipient ->
                        recipient.playSound(tornadoLoc, org.bukkit.Sound.ITEM_ELYTRA_FLYING, 0.6f, 0.6f)
                        recipient.playSound(tornadoLoc, org.bukkit.Sound.ENTITY_BREEZE_IDLE_GROUND, 0.5f, 0.8f)
                    }
                }
                if (tornadoActive && totalPulledInThisEvent < maxPullLimit) {
                    var pulledInThisTick = 0
                    val checkRadius = 1

                    for (x in -checkRadius..checkRadius) {
                        for (z in -checkRadius..checkRadius) {
                            if (pulledInThisTick >= 10) break

                            for (yOffset in 0 downTo -1) {
                                val targetY = (currentTornadoY + yOffset).toInt()
                                if (targetY < minY || targetY > maxY) continue

                                val targetX = currentTornadoX.toInt() + x
                                val targetZ = currentTornadoZ.toInt() + z
                                val key = toKey(targetX, targetY, targetZ)

                                val mat = session.virtualBlocks[key] ?: continue

                                session.virtualBlocks.remove(key)
                                totalPulledInThisEvent++
                                pulledInThisTick++

                                val blockLoc = Location(world, targetX.toDouble(), targetY.toDouble(), targetZ.toDouble())
                                player.sendBlockChange(blockLoc, Material.AIR.createBlockData())

                                if (spawnedItems.size < 60) {
                                    val item = world.dropItem(blockLoc.add(0.5, 0.2, 0.5), ItemStack(mat)) {
                                        it.pickupDelay = 32767
                                        it.setGravity(false)
                                        it.isGlowing = true
                                        it.isInvulnerable = true
                                    }
                                    spawnedItems.add(item)
                                } else {
                                    player.inventory.addItem(ItemStack(mat))
                                }
                            }
                        }
                    }
                }

                processItemPhysics(player, spawnedItems, tornadoLoc, tornadoActive)
                if (ticks % 2 == 0) renderTornadoEffects(player, tornadoLoc, ticks)

                if (!tornadoActive && spawnedItems.isEmpty()) {
                    cleanupVortex(player, spawnedItems, session)
                    this.cancel()
                    return
                }
                ticks++
            }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    // --- MÉTODOS  ---

    private fun processItemPhysics(player: Player, items: MutableList<org.bukkit.entity.Item>, tornadoLoc: Location, active: Boolean) {
        val it = items.iterator()
        val portalVec = VORTEX_LOCATION.toVector()

        while (it.hasNext()) {
            val item = it.next()
            if (!item.isValid) { it.remove(); continue }

            val itemLoc = item.location.toVector()
            if (active) {
                val target = tornadoLoc.clone().add(0.0, 4.0, 0.0).toVector()
                val toTornado = target.subtract(itemLoc).normalize()

                val orbit = Vector(-toTornado.z, 0.0, toTornado.x).multiply(0.25)

                item.velocity = item.velocity.add(toTornado.multiply(0.15))
                    .add(orbit)
                    .multiply(0.9)

                if (item.location.distanceSquared(tornadoLoc) < 2.5) {
                    player.inventory.addItem(item.itemStack)
                    item.remove()
                    it.remove()
                    continue
                }
            } else {
                val toPortal = portalVec.clone().subtract(itemLoc)
                if (toPortal.lengthSquared() < 2.0) {
                    player.inventory.addItem(item.itemStack)
                    player.playSound(player.location, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 2.0f)
                    item.remove()
                    it.remove()
                } else {
                    item.velocity = toPortal.normalize().multiply(0.7)
                }
            }
        }
    }

    private fun updateVortexHole(player: Player, material: Material) {
        val data = material.createBlockData()
        val recipients = getRecipientGroup(player)

        for (x in -4..4) {
            for (z in -4..4) {
                val loc = VORTEX_LOCATION.clone().add(x.toDouble(), 0.0, z.toDouble())
                recipients.forEach { it.sendBlockChange(loc, data) }
            }
        }
    }

    private fun renderTornadoEffects(player: Player, loc: Location, ticks: Int) {
        val recipients = getRecipientGroup(player)

        val brownColor = org.bukkit.Color.fromRGB(83, 50, 24)
        val dustOptions = org.bukkit.Particle.DustOptions(brownColor, 1.2f)

        val arms = 4

        for (h in 0..8) {
            val heightFactor = h.toDouble() * 0.4

            for (arm in 0 until arms) {
                val angle = (ticks * 0.8) + (h * 0.5) + (arm * (Math.PI * 2 / arms))
                val radius = 0.3 + (h * 0.15)

                val pLoc = loc.clone().add(
                    Math.cos(angle) * radius,
                    heightFactor,
                    Math.sin(angle) * radius
                )

                recipients.forEach {
                    it.spawnParticle(org.bukkit.Particle.DUST, pLoc, 4, 0.2, 0.2, 0.2, 0.0, dustOptions)

                    if (h % 4 == 0 && Math.random() < 0.05) {
                        it.spawnParticle(org.bukkit.Particle.GUST, pLoc, 1, 0.0, 0.0, 0.0, 0.01)
                    }

                    if (h == 0) {
                        it.spawnParticle(org.bukkit.Particle.DUST, pLoc, 2, 0.1, 0.0, 0.1, 0.05, dustOptions)
                    }
                }
            }
        }
    }

    private fun cleanupVortex(player: Player, items: MutableList<org.bukkit.entity.Item>, session: MineSession) {
        if (player.isOnline) {
            updateVortexHole(player, Material.AIR)

            val recipients = getRecipientGroup(player)
            recipients.forEach { recipient ->
                recipient.stopSound(org.bukkit.Sound.ITEM_ELYTRA_FLYING)
                recipient.stopSound(org.bukkit.Sound.ENTITY_BREEZE_IDLE_GROUND)
            }
        }
        items.forEach { if (it.isValid) it.remove() }
        items.clear()
        session.vortexActive = false
    }

    // --- GERENCIAMENTO ---

    fun sendToMine(player: Player) {
        player.teleport(spawnLocation)
        player.sendActionBar(Component.text("MINA PRIVADA ACESSADA").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))

        giveTools(player)
        hidePlayerFromOthers(player)

        player.allowFlight = true
        player.isFlying = true
        player.foodLevel = 20
        player.health = 20.0
        player.gameMode = org.bukkit.GameMode.SURVIVAL

        val session = activeSessions.computeIfAbsent(player.uniqueId) { MineSession(player.uniqueId) }
        refreshMine(player, session, fullReset = true)
    }

    fun removePlayer(player: Player) {
        val session = activeSessions.remove(player.uniqueId)
        if (session != null) {
            session.virtualBlocks.clear()
            player.allowFlight = false
            player.isFlying = false
            Bukkit.getOnlinePlayers().forEach {
                player.showPlayer(plugin, it)
                it.showPlayer(plugin, player)
            }
            clearMineItems(player)
        }
    }

    fun cleanupPlayer(player: Player) {
        restoreVisualBlocks(player)

        removePlayer(player)
    }

    private fun restoreVisualBlocks(player: Player) {
        val radius = 10
        val startX = (centerX - radius).toInt()
        val endX = (centerX + radius).toInt()
        val startZ = (centerZ - radius).toInt()
        val endZ = (centerZ + radius).toInt()

        for (y in minY..maxY) {
            for (x in startX..endX) {
                for (z in startZ..endZ) {
                    val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    player.sendBlockChange(loc, world.getBlockAt(loc).blockData)
                }
            }
        }
    }

    fun refreshMine(player: Player, session: MineSession, fullReset: Boolean) {
        val radius = session.getRadius()
        val packetMap = HashMap<Location, BlockData>()

        if (fullReset) {
            session.virtualBlocks.clear()
            val startX = (centerX - radius).toInt()
            val endX = (centerX + radius).toInt()
            val startZ = (centerZ - radius).toInt()
            val endZ = (centerZ + radius).toInt()

            for (y in minY..maxY) {
                for (x in startX..endX) {
                    for (z in startZ..endZ) {
                        val mat = pickRandomBlock(session.level)
                        session.virtualBlocks[toKey(x, y, z)] = mat
                        packetMap[Location(world, x.toDouble(), y.toDouble(), z.toDouble())] = mat.createBlockData()
                    }
                }
            }
        }
        if (packetMap.isNotEmpty()) player.sendMultiBlockChange(packetMap)
    }

    fun breakBlock(player: Player, x: Int, y: Int, z: Int, key: Long) {
        val session = activeSessions[player.uniqueId] ?: return
        val mat = session.virtualBlocks.remove(key) ?: return

        val loc = Location(player.world, x.toDouble() + 0.5, y.toDouble() + 0.5, z.toDouble() + 0.5)
        player.sendBlockChange(loc, Material.AIR.createBlockData())
        player.playSound(loc, org.bukkit.Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f)
        player.spawnParticle(org.bukkit.Particle.BLOCK, loc, 10, 0.2, 0.2, 0.2, 0.1, mat.createBlockData())

        if (Math.random() < VORTEX_CHANCE) {
            triggerVortex(player, session)
        } else {
            player.inventory.addItem(ItemStack(mat))
        }

        activeSessions[player.uniqueId] ?: return
        val airData = Material.AIR.createBlockData()
        val blockLoc = Location(player.world, x.toDouble(), y.toDouble(), z.toDouble())

        spectators.filter { it.value == player.uniqueId }.forEach { (specId, _) ->
            Bukkit.getPlayer(specId)?.sendBlockChange(blockLoc, airData)
        }
    }

    private fun pickRandomBlock(level: Int): Material {
        val rand = Math.random()
        return when {
            rand < 0.6 -> Material.STONE_BRICKS
            rand < 0.9 -> Material.CRACKED_STONE_BRICKS
            else -> Material.CHISELED_STONE_BRICKS
        }
    }

    private fun giveTools(player: Player) {
        val mm = MiniMessage.miniMessage()
        val pick = ItemStack(Material.DIAMOND_PICKAXE)
        val meta = pick.itemMeta ?: return

        meta.displayName(mm.deserialize("<!italic><aqua>⛏</aqua> <gradient:#55FFFF:#00AAFF><b>PICARETA SÍSMICA</b></gradient> <dark_aqua>✦</dark_aqua>"))

        val lore = listOf(
            mm.deserialize(""),
            mm.deserialize("<!italic><gray>Especialmente forjada para extrair</gray>"),
            mm.deserialize("<!italic><gray>minérios da <gradient:#FF5555:#FFAA00>/mina</gradient>.</gray>"),
            mm.deserialize(""),
            mm.deserialize("<!italic><dark_gray>» </dark_gray><white>Eficiência:</white> <yellow>V</yellow>"),
            mm.deserialize("<!italic><dark_gray>» </dark_gray><white>Habilidade:</white> <aqua>Vórtice ⚡</aqua>"),
            mm.deserialize(""),
            mm.deserialize("<!italic><gradient:#55FFFF:#00AAFF><b>RARIDADE ÉPICA</b></gradient>")
        )
        meta.lore(lore)

        meta.addEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY, 5, true)
        meta.setUnbreakable(true)

        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE)
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES)

        pick.itemMeta = meta
        player.inventory.setItem(0, pick)
    }

    private fun hidePlayerFromOthers(player: Player) {
        Bukkit.getOnlinePlayers().forEach { other ->

            if (spectators[other.uniqueId] == player.uniqueId) {
                other.showPlayer(plugin, player)
                return@forEach
            }

            if (other != player) {
                other.hidePlayer(plugin, player)
                player.hidePlayer(plugin, other)
            }
        }
    }

    private fun clearMineItems(player: Player) {
        player.inventory.contents.forEach { item ->
            if (item != null && item.type.toString().endsWith("_PICKAXE")) {
                if (item.itemMeta?.hasEnchant(org.bukkit.enchantments.Enchantment.EFFICIENCY) == true) {
                    player.inventory.remove(item)
                }
            }
        }
    }

    // --- SPEC ---

    fun startSpectating(admin: Player, target: Player) {
        val session = activeSessions[target.uniqueId] ?: return

        spectators[admin.uniqueId] = target.uniqueId
        admin.gameMode = org.bukkit.GameMode.SPECTATOR

        admin.showPlayer(plugin, target)
        admin.teleport(target.location)

        Bukkit.getOnlinePlayers().forEach { if (it != admin) it.hidePlayer(plugin, admin) }

        val packetMap = HashMap<Location, BlockData>()
        session.virtualBlocks.forEach { (key, mat) ->
            val pos = fromKey(key)
            packetMap[Location(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())] = mat.createBlockData()
        }
        admin.sendMultiBlockChange(packetMap)

        if (session.vortexActive) {
            val data = Material.END_PORTAL.createBlockData()
            for (x in -4..4) {
                for (z in -4..4) {
                    val loc = VORTEX_LOCATION.clone().add(x.toDouble(), 0.0, z.toDouble())
                    admin.sendBlockChange(loc, data)
                }
            }
        }

        object : org.bukkit.scheduler.BukkitRunnable() {
            override fun run() {
                if (!admin.isOnline || spectators[admin.uniqueId] != target.uniqueId) {
                    this.cancel()
                    return
                }

                val mm = MiniMessage.miniMessage()
                val targetName = target.name

                val msg = mm.deserialize(
                    "<white><b>ESPECTANDO</b></white> <dark_gray>»</dark_gray> " +
                            "<gradient:#FFAA00:#FFFF55><b>${targetName.uppercase()}</b></gradient> " +
                            "<dark_gray>•</dark_gray> <gray>Para sair use <yellow><b>/mina sair</b></yellow>"
                )

                admin.sendActionBar(msg)
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    fun stopSpectating(admin: Player) {
        spectators.remove(admin.uniqueId)
        admin.gameMode = org.bukkit.GameMode.SURVIVAL

        restoreVisualBlocks(admin)

        Bukkit.getOnlinePlayers().forEach { it.showPlayer(plugin, admin) }

        admin.sendMessage("§e§lSPEC §8» §fVocê parou de espectar.")
    }

    private fun getRecipientGroup(player: Player): List<Player> {
        val recipients = mutableListOf(player)
        spectators.filter { it.value == player.uniqueId }.forEach { (specId, _) ->
            Bukkit.getPlayer(specId)?.let { recipients.add(it) }
        }
        return recipients
    }
}