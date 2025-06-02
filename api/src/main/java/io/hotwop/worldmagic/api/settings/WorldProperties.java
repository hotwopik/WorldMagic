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
 * @param forceDefaultGamemode Should plugin set default gememode to all players entering the world
 * @param difficulty World difficulty
 * @param requiredPermission Permission required to enter the world, null to none
 */
public record WorldProperties(
    @Nullable Long seed,
    boolean generateStructures,
    boolean bonusChest,
    GameMode defaultGamemode,
    boolean forceDefaultGamemode,
    Difficulty difficulty,
    @Nullable String requiredPermission
){}