package io.hotwop.worldmagic.api;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Dimension generator settings
 */
public interface GeneratorLike{
    /**
     * Create vanilla generator settings by deserializing string
     *
     * @param settings Stringified json vanilla generator settings
     * @return new GeneratorLike
     */
    @Contract("_ -> new")
    static GeneratorLike createVanillaGeneratorFromSettings(@NotNull String settings){throw new IncorrectImplementationException();}

    /**
     * Create vanilla generator settings from NMS ChunkGenerator
     *
     * @param handle NMS ChunkGenerator
     * @return new GeneratorLike
     */
    @Contract("_ -> new")
    static GeneratorLike createVanillaGeneratorFromHandle(@NotNull Object handle){throw new IncorrectImplementationException();}

    /**
     * Create plugin generator settings from id
     *
     * @param id reference to plugin generator
     * @return new GeneratorLike
     */
    @Contract("_ -> new")
    static GeneratorLike createPluginGenerator(@NotNull String id){throw new IncorrectImplementationException();}
}
