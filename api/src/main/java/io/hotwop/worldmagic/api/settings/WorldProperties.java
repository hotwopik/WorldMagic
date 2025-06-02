package io.hotwop.worldmagic.api.settings;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.jetbrains.annotations.Nullable;

/**
 * Some world properties
 *
 * @param seed Generation seed, null to random
 * @param generateStructures Structure generation configuration
 * @param bonusChest Start bonus chest
 * @param defaultGamemode Default world gamemode
 * @param difficulty World difficulty
 */
public record WorldProperties(
    @Nullable Long seed,
    boolean generateStructures,
    boolean bonusChest,
    GameMode defaultGamemode,
    Difficulty difficulty
){}