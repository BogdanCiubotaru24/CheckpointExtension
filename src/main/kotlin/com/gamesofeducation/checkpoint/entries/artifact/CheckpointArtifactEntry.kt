package com.gamesofeducation.checkpoint.entries.artifact

import com.typewritermc.core.books.pages.Colors
import com.typewritermc.core.extension.annotations.Entry
import com.typewritermc.engine.paper.entry.entries.ArtifactEntry

@Entry(
    "checkpoint_artifact",
    "Stores a player's checkpoint snapshot (facts, position, inventory).",
    Colors.MEDIUM_SEA_GREEN,
    "material-symbols:save"
)
class CheckpointArtifactEntry(
    override val id: String = "",
    override val name: String = "",
    override val artifactId: String = "",
) : ArtifactEntry

