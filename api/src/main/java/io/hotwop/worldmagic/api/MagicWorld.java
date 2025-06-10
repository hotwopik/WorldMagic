package io.hotwop.worldmagic.api;

import io.hotwop.worldmagic.api.settings.AllowSettings;
import io.hotwop.worldmagic.api.settings.CustomWorldSettings;
import io.hotwop.worldmagic.api.settings.WorldProperties;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.file.Path;

/**
 * WorldMagic world instance
 */
@ThreadSafe
public interface MagicWorld{
    /**
     * Get world id
     * @return vanilla world id
     */
    NamespacedKey id();
    /**
     * Get world bukkit id
     * @return bukkit world id
     */
    String bukkitId();
    /**
     * Get world path
     * @return relative folder path
     */
    String folder();

    /**
     * Get world path object
     * @return folder path object
     */
    Path folderPath();
    /**
     * Get world load state
     * @return whether world loaded or not
     */
    boolean loaded();
    /**
     * Get bukkit world instance or null if world not loaded
     * @return bukkit world instance
     */
    World world();
    /**
     * Returns true if world in deletion process or already deleted
     * @return whether world deleted or not
     */
    boolean isForDeletion();

    /**
     * Get world dimension
     * @return World dimension settings
     */
    DimensionLike dimension();
    /**
     * Get world properties
     * @return world properties
     */
    WorldProperties worldProperties();
    /**
     * Get allow settings
     * @return world allow settings
     */
    AllowSettings allowSettings();

    /**
     * Get callback location
     * @return current callback location
     */
    Location callbackLocation();

    /**
     * Create CustomWorldSettings based on this world
     *
     * @param id vanilla world id
     * @return settings
     */
    @Contract("_ -> new")
    CustomWorldSettings createSettings(@NotNull NamespacedKey id);

    /**
     * Create CustomWorldSettings based on this world
     *
     * @param id vanilla world id
     * @param bukkitId bukkit world id
     * @return settings
     */
    @Contract("_, _ -> new")
    CustomWorldSettings createSettings(@NotNull NamespacedKey id,@NotNull String bukkitId);

    /**
     * Create CustomWorldSettings based on this world
     *
     * @param id vanilla world id
     * @param bukkitId bukkit world id
     * @param folder world folder path
     * @return settings
     */
    @Contract("_, _, _ -> new")
    CustomWorldSettings createSettings(@NotNull NamespacedKey id,@NotNull String bukkitId,@NotNull String folder);


    /**
     * Run world load process
     * @throws WorldAlreadyLoadedException if world already loaded
     * @throws WorldLoadException if can't load
     */
    void load();

    /**
     * Run world unload process
     * @throws WorldAlreadyUnloadedException if world already unloaded
     * @throws WorldUnloadException if can't unload
     */
    void unload();
}
