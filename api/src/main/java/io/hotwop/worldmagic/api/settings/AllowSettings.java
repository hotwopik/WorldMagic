package io.hotwop.worldmagic.api.settings;

/**
 * Setting up what allowed in world
 *
 * @param animals allow animal mobs
 * @param monsters allow monster mobs
 * @param pvp allow player vs player
 */
public record AllowSettings(
    boolean animals,
    boolean monsters,
    boolean pvp
){}
