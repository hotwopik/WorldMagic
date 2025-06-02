package io.hotwop.worldmagic.api.settings;

import org.bukkit.Location;

/**
 * Create spawn settings
 *
 * @param override Should plugin override spawn position on world load
 * @param x x spawn position
 * @param y y spawn position
 * @param z z spawn position
 * @param yaw player yaw rotation
 */
public record SpawnPosition(
    boolean override,
    int x,
    int y,
    int z,
    float yaw
){
    /**
     * Create spawn setting from location
     *
     * @param location location
     * @param override Should plugin override spawn position on world load
     */
    public SpawnPosition(Location location,boolean override){
        this(override,location.getBlockX(),location.getBlockY(),location.getBlockZ(),location.getYaw());
    }
}
