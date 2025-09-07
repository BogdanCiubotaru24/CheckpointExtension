package com.gamesofeducation.checkpoint.model

import com.typewritermc.core.utils.point.Coordinate
import org.bukkit.inventory.ItemStack

data class CheckpointPayload(
    val meta: CheckpointMeta,
    val facts: Map<String, Int>,
    val position: PositionSnapshot? = null,
    val inventory: InventorySnapshot? = null,
)

data class CheckpointMeta(
    val owner: String,
    val ownerName: String,
    val createdAt: String,
)

data class PositionSnapshot(
    val world: String,
    val coordinate: Coordinate,
)

data class InventorySnapshot(
    val storage: List<ItemStack?>,
    val armor: List<ItemStack?>,
    val offhand: ItemStack?,
)

