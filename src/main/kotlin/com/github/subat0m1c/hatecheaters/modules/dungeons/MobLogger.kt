package com.github.subat0m1c.hatecheaters.modules.dungeons

import com.github.subat0m1c.hatecheaters.HateCheaters.Companion.launch
import com.github.subat0m1c.hatecheaters.utils.LogHandler
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.odinmain.events.impl.RoomEnterEvent
import me.odinmain.features.Module
import me.odinmain.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.network.play.server.S0CPacketSpawnPlayer
import net.minecraft.network.play.server.S0FPacketSpawnMob
import net.minecraft.util.BlockPos
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object MobLogger : Module(
    "Mob Logger",
    description = "Logs dungeon mob spawns to a file.",
) {
    val saved = mutableMapOf<String, HashMap<Int, ArrayList<MobData>>>()
    var instance = 0

    val toCheck = hashSetOf<EntityLivingBase>()

    private lateinit var file: File
    private fun newFile() {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val dir = File("config/hatecheaters/mod_logs/dungeon_mobs")
        if (!dir.exists()) dir.mkdirs()
        file = File(dir, "dungeon_mobs_$date.json")
        file.createNewFile()
    }

    init {
        newFile()
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        toCheck.removeAll { entity ->
            val armorStand =
                mc.theWorld.getEntityByID(entity.entityId + 1) as? EntityArmorStand ?: return@removeAll true
            event.room?.getRelativeCoords(entity.position)
            event.room?.let { room ->
                room.roomComponents.forEach {
                    val x = it.x - 16.0
                    val endX = it.x + 16.0
                    val y = it.z - 16.0
                    val endY = it.x + 16.0

                    if (entity.posX in x..endX && entity.posZ in y..endY) {
                        saved.getOrPut(room.data.name) { HashMap() }
                            .getOrPut(instance) { ArrayList() }
                            .add(MobData(armorStand.name, V3(BlockPos(entity.position))))
                        return@removeAll true
                    }
                }
            }
            false
        }
    }

    init {
        onPacket<S0FPacketSpawnMob> {
            val entity = mc.theWorld.getEntityByID(it.entityID) as? EntityLivingBase ?: return@onPacket
            toCheck.add(entity)
        }
        onPacket<S0CPacketSpawnPlayer> {
            val entity = mc.theWorld.getEntityByID(it.entityID) as? EntityLivingBase ?: return@onPacket
            toCheck.add(entity)
        }
        onWorldLoad {
            if (saved.isNotEmpty()) {
                launch {
                    try {
                        file.bufferedWriter().use {
                            it.write(json.encodeToString(saved))
                        }
                    } catch (e: Exception) {
                        LogHandler.Logger.warning("Failed to save dungeon mob log: ${e.message}")
                    } finally {
                        saved.clear()
                        newFile()
                    }
                }
            }
            toCheck.clear()
            instance++
        }
    }

    @Serializable
    data class MobData(
        val name: String,
        val coords: V3
    )

    /// just to ensure its kotlinx serialization compatible
    @Serializable
    data class V3(
        val x: Double,
        val y: Double,
        val z: Double
    ) {
        constructor(pos: BlockPos) : this(pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5)
    }

    val json = Json { prettyPrint = true }
}