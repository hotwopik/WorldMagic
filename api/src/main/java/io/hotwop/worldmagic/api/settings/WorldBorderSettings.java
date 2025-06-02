package io.hotwop.worldmagic.api.settings;

/**
 * Create world border settings
 *
 * @param override Should plugin override world border on load
 * @param size Border radius
 * @param safeZone Safe distance from border without damage
 * @param damagePerBlock Damage per block out of safe zone
 * @param centerX Border center x
 * @param centerZ Border center z
 * @param warningDistance Distance from border where vignette displays
 * @param warningTime Warning vignette appearing time
 */
public record WorldBorderSettings(
    boolean override,
    double size,
    double safeZone,
    double damagePerBlock,
    double centerX,
    double centerZ,
    int warningDistance,
    int warningTime
){}
