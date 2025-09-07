package com.gamesofeducation.checkpoint.commands

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.typewritermc.engine.paper.entry.StagingManager
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import org.koin.java.KoinJavaComponent.get
import java.util.concurrent.CompletableFuture

/**
 * A custom argument type that suggests checkpoint artifact names from the StagingManager pages.
 * Converts to the raw String (no validation), so it works for both existing and new names.
 */
class CheckpointNameArgumentType : CustomArgumentType.Converted<String, String> {
    override fun convert(nativeType: String): String = nativeType

    override fun getNativeType(): ArgumentType<String> = StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remaining
        val staging: StagingManager = org.koin.java.KoinJavaComponent.get(StagingManager::class.java)
        val pages: Collection<JsonObject> = staging.pages.values
        for (page in pages) {
            val entries = page.getAsJsonArray("entries") ?: continue
            for (el in entries) {
                val obj = el.asJsonObject
                val type = obj.get("type")?.asString
                if (type == "checkpoint_artifact") {
                    val name = obj.get("name")?.asString ?: obj.get("id")?.asString
                    if (!name.isNullOrBlank() && name.startsWith(input, ignoreCase = true)) {
                        builder.suggest(name)
                    }
                }
            }
        }
        return builder.buildFuture()
    }
}
