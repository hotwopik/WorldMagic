package io.hotwop.worldmagic.api;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Dimension settings
 */
public interface DimensionLike{
    /**
     * Creates dimension settings from dimension type and generator
     *
     * @param dimensionType dimension type id
     * @param generator generator settings
     * @return new DimensionLike
     */
    @Contract("_, _ -> new")
    static DimensionLike createInline(@NotNull NamespacedKey dimensionType,@NotNull GeneratorLike generator){throw new IncorrectImplementationException();}

    /**
     * Creates dimension settings from dimension type, generator and plugin biome source
     *
     * @param dimensionType dimension type id
     * @param generator generator settings
     * @param pluginBiomeSource plugin biome source id
     * @return new DimensionLike
     */
    @Contract("_, _, _ -> new")
    static DimensionLike createInline(@NotNull NamespacedKey dimensionType,@NotNull GeneratorLike generator,@NotNull String pluginBiomeSource){throw new IncorrectImplementationException();}

    /**
     * Creates dimension settings from id
     *
     * @param id reference to other dimension
     * @return new DimensionLike
     */
    @Contract("_ -> new")
    static DimensionLike createFromReference(@NotNull NamespacedKey id){throw new IncorrectImplementationException();}
}
