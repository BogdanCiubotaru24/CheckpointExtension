package com.gamesofeducation.checkpoint.entries.actions

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.gamesofeducation.checkpoint.model.CheckpointMeta
import com.gamesofeducation.checkpoint.model.CheckpointPayload
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.ref
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.core.interaction.context
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.entry.Criteria
import com.typewritermc.engine.paper.entry.Modifier
import com.typewritermc.engine.paper.entry.TriggerableEntry
import com.typewritermc.engine.paper.entry.entries.ActionEntry
import com.typewritermc.engine.paper.entry.entries.ActionTrigger
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.entry.entries.WritableFactEntry
import com.typewritermc.engine.paper.facts.RefreshFactTrigger
import com.typewritermc.engine.paper.entry.entries.getAssetFromFieldValue
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.toBukkitLocation
import kotlinx.coroutines.Dispatchers
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get

@Entry(
    "checkpoint_apply_action",
    "Apply a checkpoint to the player, replacing all facts.",
    Colors.MEDIUM_SEA_GREEN,
    "material-symbols:restore"
)
class ApplyCheckpointActionEntry(
    override val id: String = "",
    override val name: String = "",
    val checkpoint: String = "",
) : ActionEntry {
    override val criteria get() = emptyList<Criteria>()
    override val modifiers get() = emptyList<Modifier>()
    override val triggers get() = emptyList<Ref<TriggerableEntry>>()

    override fun ActionTrigger.execute() {
        val artifactEntry = getAssetFromFieldValue(checkpoint).getOrNull() ?: return
        val gson: Gson = get(Gson::class.java, named("bukkitDataParser"))

        Dispatchers.UntickedAsync.launch {
            val json = artifactEntry.stringData() ?: return@launch
            val payload = try {
                gson.fromJson(json, CheckpointPayload::class.java)
            } catch (_: Throwable) {
                val root = JsonParser.parseString(json).asJsonObject
                val factsObj: JsonObject = root.getAsJsonObject("facts") ?: JsonObject()
                CheckpointPayload(
                    meta = CheckpointMeta("", "", ""),
                    facts = factsObj.entrySet().associate { it.key to it.value.asInt },
                    position = null,
                    inventory = null,
                )
            }

            // Reset all writable facts
            Query.find<WritableFactEntry>().forEach { entry ->
                try {
                    entry.write(player, 0)
                } catch (_: Throwable) {
                }
            }

            // Apply facts
            payload.facts.forEach { (entryId, value) ->
                val entry = Query.findById<WritableFactEntry>(entryId) ?: return@forEach
                try {
                    entry.write(player, value)
                    if (entry is ReadableFactEntry) {
                        RefreshFactTrigger(entry.ref()).triggerFor(player, context())
                    }
                } catch (_: Throwable) {
                }
            }

            // Run Bukkit changes on sync thread
            plugin.server.scheduler.callSyncMethod(plugin, java.util.concurrent.Callable {
                // Apply position
                payload.position?.let { pos ->
                    val world = Bukkit.getWorld(pos.world) ?: player.world
                    player.teleport(pos.coordinate.toBukkitLocation(world))
                }

                // Apply inventory
                payload.inventory?.let { inv ->
                    try {
                        val pInv = player.inventory
                        pInv.storageContents = arrayOfNulls<ItemStack>(pInv.storageContents.size)
                        pInv.storageContents = inv.storage.toTypedArray()
                        pInv.armorContents = inv.armor.toTypedArray()
                        inv.offhand?.let { pInv.setItemInOffHand(it) } ?: run { pInv.setItemInOffHand(org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR)) }
                        player.updateInventory()
                    } catch (_: Throwable) {
                    }
                }
                true
            }).get()
        }
    }
}

