package io.hotwop.worldmagic;

import io.hotwop.worldmagic.api.IncorrectImplementationException;
import io.hotwop.worldmagic.api.MagicWorld;
import io.hotwop.worldmagic.api.settings.CustomWorldSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class WorldMagic extends JavaPlugin{
    /**
     * Get plugin instance
     */
    @Contract(pure=true)
    public static WorldMagic instance(){throw new IncorrectImplementationException();}

    /**
     * Whether plugin finish enabling state or not
     */
    @Contract(pure=true)
    public static boolean loaded(){throw new IncorrectImplementationException();}

    /**
     * Try to find plugin world by it vanilla id
     *
     * @param id world vanilla id
     * @return WorldMagic world instance
     */
    @Contract
    public static @Nullable MagicWorld getPluginWorld(@NotNull NamespacedKey id){throw new IncorrectImplementationException();}

    /**
     * Get full list of plugin worlds
     *
     * @return Plugin worlds instance list
     */
    @Contract(pure=true)
    public static @NotNull @Unmodifiable List<MagicWorld> getPluginWorlds(){throw new IncorrectImplementationException();}

    /**
     * Try to find plugin world instance by bukkit world instance
     *
     * @param world bukkit world instance
     * @return WorldMagic world instance
     */
    @Contract
    public static @Nullable MagicWorld isPluginWorld(@NotNull World world){throw new IncorrectImplementationException();}

    /**
     * Try to create plugin world via CustomWorldSettings
     *
     * @param settings CustomWorldSettings
     * @return plugin world instance
     * @throws WorldCreationException when catch an error on some of the phases
     */
    @Contract
    public static MagicWorld createWorldFromSettings(@NotNull CustomWorldSettings settings) throws WorldCreationException{throw new IncorrectImplementationException();}

    /**
     * Try to delete(not unload) plugin world instance
     *
     * @param id vanilla world id
     * @throws WorldDeletionException when can't find world
     */
    @Contract
    public static void deleteWorld(@NotNull NamespacedKey id) throws WorldDeletionException{throw new IncorrectImplementationException();}
}
