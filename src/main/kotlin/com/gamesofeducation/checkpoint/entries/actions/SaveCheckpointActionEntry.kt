package com.gamesofeducation.checkpoint.entries.actions

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.gamesofeducation.checkpoint.model.CheckpointMeta
import com.gamesofeducation.checkpoint.model.CheckpointPayload
import com.gamesofeducation.checkpoint.model.InventorySnapshot
import com.gamesofeducation.checkpoint.model.PositionSnapshot
import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.entries.Query
import com.typewritermc.core.entries.Ref
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
import com.typewritermc.engine.paper.entry.entries.getAssetFromFieldValue
import com.typewritermc.engine.paper.entry.entries.stringData
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.engine.paper.utils.toCoordinate
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Entry(
    "checkpoint_save_action",
    "Save all of the player's facts into a checkpoint artifact.",
    Colors.MEDIUM_SEA_GREEN,
    "material-symbols:save"
)
class SaveCheckpointActionEntry(
    override val id: String = "",
    override val name: String = "",
    val artifact: String = "",
) : ActionEntry {
    override val criteria get() = emptyList<Criteria>()
    override val modifiers get() = emptyList<Modifier>()
    override val triggers get() = emptyList<Ref<TriggerableEntry>>()

    override fun ActionTrigger.execute() {
        val artifactEntry = getAssetFromFieldValue(artifact).getOrNull() ?: return
        val gson: Gson = get(Gson::class.java, named("bukkitDataParser"))

        val facts = Query.find<ReadableFactEntry>()
            .mapNotNull { entry ->
                val value = entry.readForPlayersGroup(player).value
                if (value == 0) return@mapNotNull null
                entry.id to value
            }
            .toMap()

        val payload = CheckpointPayload(
            meta = CheckpointMeta(
                owner = player.uniqueId.toString(),
                ownerName = player.name,
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            ),
            facts = facts,
            position = PositionSnapshot(
                world = player.world.name,
                coordinate = player.location.toCoordinate(),
            ),
            inventory = InventorySnapshot(
                storage = player.inventory.storageContents.map { it?.clone() },
                armor = player.inventory.armorContents.map { it?.clone() },
                offhand = player.inventory.itemInOffHand.clone(),
            )
        )

        Dispatchers.UntickedAsync.launch {
            artifactEntry.stringData(gson.toJson(payload))
        }
    }
}
