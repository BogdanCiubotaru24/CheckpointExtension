package com.gamesofeducation.checkpoint.commands

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.typewritermc.core.entries.Query
import com.typewritermc.core.extension.annotations.TypewriterCommand
import com.typewritermc.engine.paper.command.dsl.DslCommandTree
import com.typewritermc.engine.paper.command.dsl.executePlayerOrTarget
import com.typewritermc.engine.paper.command.dsl.string
import com.typewritermc.engine.paper.command.dsl.argument
import com.typewritermc.engine.paper.command.dsl.sender
import com.typewritermc.engine.paper.command.dsl.ArgumentCommandTree
import com.typewritermc.engine.paper.command.dsl.ArgumentReference
import com.typewritermc.engine.paper.command.dsl.ExecutionContext
import com.typewritermc.engine.paper.entry.AssetManager
import com.typewritermc.engine.paper.entry.StagingManager
import com.typewritermc.engine.paper.entry.entries.ReadableFactEntry
import com.typewritermc.engine.paper.entry.entries.WritableFactEntry
import com.typewritermc.engine.paper.facts.RefreshFactTrigger
import com.typewritermc.engine.paper.utils.msg
import com.typewritermc.engine.paper.utils.sendMini
import com.typewritermc.engine.paper.entry.triggerFor
import com.typewritermc.core.entries.ref
import com.typewritermc.core.interaction.context
import com.typewritermc.core.utils.UntickedAsync
import com.typewritermc.core.utils.launch
import com.typewritermc.engine.paper.plugin
import com.typewritermc.engine.paper.utils.toBukkitLocation
import com.typewritermc.engine.paper.utils.toCoordinate
// Access dispatcher via generated getter to avoid private wrapper class
import com.gamesofeducation.checkpoint.entries.artifact.CheckpointArtifactEntry
import com.gamesofeducation.checkpoint.model.CheckpointPayload
import com.gamesofeducation.checkpoint.model.CheckpointMeta
import com.gamesofeducation.checkpoint.model.PositionSnapshot
import com.gamesofeducation.checkpoint.model.InventorySnapshot
import kotlinx.coroutines.Dispatchers
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@TypewriterCommand
fun DslCommandTree<CommandSourceStack, *>.checkpointCommands() {
    literal("checkpoint") {
        literal("save") {
            argument("name", CheckpointNameArgumentType(), String::class) { nameArg ->
                executePlayerOrTarget { target ->
                    val name = nameArg()
                    saveCheckpoint(target, name)
                }
            }
        }

        literal("apply") {
            // Name with suggestions from staging pages
            argument("name", CheckpointNameArgumentType(), String::class) { nameArg ->
                executePlayerOrTarget { target ->
                    val name = nameArg()
                    applyCheckpoint(target, name)
                }
            }
        }

        literal("delete") {
            argument("name", CheckpointNameArgumentType(), String::class) { nameArg ->
                // Confirm path: append -force to actually delete
                literal("-force") {
                    executes {
                        val staging: StagingManager = get(StagingManager::class.java)
                        val name = nameArg()
                        val (pageId, entryId) = findArtifactEntryAnywhere(staging, name)
                            ?: run {
                                sender.msg("<red>Checkpoint '<yellow>$name</yellow>' not found.")
                                return@executes
                            }
                        staging.deleteEntry(pageId, entryId)
                        sender.msg("Deleted checkpoint <green>$name</green>.")
                    }
                }

                // No -force: show confirmation hint
                executes {
                    val name = nameArg()
                    sender.msg("<yellow>Confirmation required.</yellow> Run <white>/tw checkpoint delete ${name} -force</white> to confirm.")
                }
            }
        }

        literal("list") {
            executes {
                val staging: StagingManager = get(StagingManager::class.java)
                val checkpoints = listAllCheckpoints(staging)
                if (checkpoints.isEmpty()) {
                    sender.msg("No checkpoints found.")
                    return@executes
                }
                sender.msg("<green>Checkpoints:</green>")
                checkpoints.groupBy { it.first }.forEach { (page, entries) ->
                    sender.msg("<gray>- Page:</gray> <blue>$page</blue>")
                    entries.forEach { (_, name) -> sender.msg("   • <white>$name</white>") }
                }
            }
        }
    }
}

private fun saveCheckpoint(player: Player, checkpointName: String) {
    val staging: StagingManager = get(StagingManager::class.java)
    val gson: Gson = get(Gson::class.java, named("bukkitDataParser"))
    val assetManager: AssetManager = get(AssetManager::class.java)

    // Collect facts
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

    // Ensure page exists
    val pageId = ensureCheckpointPage(staging)
    val (entryId, artifactId) = findOrCreateArtifactEntry(staging, pageId, checkpointName)

    Dispatchers.UntickedAsync.launch {
        val entry = CheckpointArtifactEntry(id = entryId, name = checkpointName, artifactId = artifactId)
        assetManager.storeStringAsset(entry, gson.toJson(payload))
        player.sendMini("<green>Checkpoint</green> '<blue>$checkpointName</blue>' saved.")
    }
}

private fun applyCheckpoint(player: Player, checkpointName: String) {
    val staging: StagingManager = get(StagingManager::class.java)
    val gson: Gson = get(Gson::class.java, named("bukkitDataParser"))
    val assetManager: AssetManager = get(AssetManager::class.java)

    val found = findArtifactEntryAnywhere(staging, checkpointName)
        ?: run {
            player.msg("<red>Checkpoint '<yellow>$checkpointName</yellow>' not found.")
            return
        }
    val (pageId, entryId, artifactId) = found

    Dispatchers.UntickedAsync.launch {
        val entry = CheckpointArtifactEntry(id = entryId, name = checkpointName, artifactId = artifactId)
        val json = assetManager.fetchStringAsset(entry) ?: run {
            player.msg("<red>Checkpoint data missing for '<yellow>$checkpointName</yellow>'.")
            return@launch
        }

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
            } catch (_: Throwable) {}
        }

        // Apply facts
        payload.facts.forEach { (factId, value) ->
            val fact = Query.findById<WritableFactEntry>(factId) ?: return@forEach
            try {
                fact.write(player, value)
                if (fact is ReadableFactEntry) {
                    RefreshFactTrigger(fact.ref()).triggerFor(player, context())
                }
            } catch (_: Throwable) {}
        }

        // Apply Bukkit-affecting changes on the main thread
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
                } catch (_: Throwable) {}
            }
            true
        }).get()

        player.sendMini("<green>Applied checkpoint</green> '<blue>$checkpointName</blue>'.")
    }
}

// Find checkpoint entry (by name) across all pages; returns Triple(pageId, entryId, artifactId)
private fun findArtifactEntryAnywhere(staging: StagingManager, name: String): Triple<String, String, String>? {
    staging.pages.forEach { (pageId, page) ->
        val entries = page.getAsJsonArray("entries") ?: return@forEach
        entries.forEach { el ->
            val obj = el.asJsonObject
            if (obj.get("type")?.asString == "checkpoint_artifact") {
                val n = obj.get("name")?.asString ?: return@forEach
                if (n.equals(name, ignoreCase = true)) {
                    val entryId = obj.get("id")?.asString ?: return@forEach
                    val artifactId = obj.get("artifactId")?.asString ?: return@forEach
                    return Triple(pageId, entryId, artifactId)
                }
            }
        }
    }
    return null
}

// Build a list of all checkpoints grouped by page (pageId to name)
private fun listAllCheckpoints(staging: StagingManager): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    staging.pages.forEach { (pageId, page) ->
        val entries = page.getAsJsonArray("entries") ?: return@forEach
        entries.forEach { el ->
            val obj = el.asJsonObject
            if (obj.get("type")?.asString == "checkpoint_artifact") {
                val name = obj.get("name")?.asString ?: obj.get("id")?.asString ?: "(unknown)"
                list.add(pageId to name)
            }
        }
    }
    return list.sortedBy { it.second.lowercase() }
}

// Utility to locate the page containing an entry id
private fun findPageIdForEntry(staging: StagingManager, entryId: String): String? {
    return staging.pages.entries.firstOrNull { (_, page) ->
        page.getAsJsonArray("entries")?.any { it.asJsonObject["id"]?.asString == entryId } == true
    }?.key
}

// Ensure a static page exists for checkpoints and return its id
private const val CHECKPOINT_PAGE_ID = "checkpoint_artifacts"
private fun ensureCheckpointPage(staging: StagingManager): String {
    staging.pages[CHECKPOINT_PAGE_ID]?.let { return CHECKPOINT_PAGE_ID }

    val page = JsonObject().apply {
        addProperty("id", CHECKPOINT_PAGE_ID)
        addProperty("name", "Checkpoint Artifacts")
        addProperty("type", "static")
        add("entries", com.google.gson.JsonArray())
    }
    staging.createPage(page)
    return CHECKPOINT_PAGE_ID
}

// Returns Pair<entryId, artifactId>
private fun findOrCreateArtifactEntry(
    staging: StagingManager,
    pageId: String,
    checkpointName: String,
): Pair<String, String> {
    val page = staging.pages[pageId]
    val existing = findArtifactEntry(page, checkpointName)
    if (existing != null) return existing

    val entryId = "cp_" + UUID.randomUUID().toString().replace("-", "").take(12)
    val artifactId = UUID.randomUUID().toString()
    val entry = JsonObject().apply {
        addProperty("id", entryId)
        addProperty("name", checkpointName)
        addProperty("type", "checkpoint_artifact")
        addProperty("artifactId", artifactId)
    }
    staging.createEntry(pageId, entry)
    return entryId to artifactId
}

private fun findArtifactEntry(page: JsonObject?, checkpointName: String): Pair<String, String>? {
    page ?: return null
    val entries = page.getAsJsonArray("entries") ?: return null
    for (el in entries) {
        val obj = el.asJsonObject
        val type = obj.get("type")?.asString ?: continue
        if (type != "checkpoint_artifact") continue
        val name = obj.get("name")?.asString ?: continue
        if (!name.equals(checkpointName, ignoreCase = true)) continue
        val entryId = obj.get("id")?.asString ?: continue
        val artifactId = obj.get("artifactId")?.asString ?: continue
        return entryId to artifactId
    }
    return null
}


