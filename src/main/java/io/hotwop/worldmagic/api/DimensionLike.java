package io.hotwop.worldmagic.api;

import io.hotwop.worldmagic.generation.Dimension;
import io.hotwop.worldmagic.generation.GeneratorSettings;
import org.bukkit.NamespacedKey;

import java.util.Objects;

public interface DimensionLike{
    static DimensionLike createInline(NamespacedKey dimensionType,GeneratorLike generator){
        Objects.requireNonNull(dimensionType,"dimensionType");
        Objects.requireNonNull(generator,"generator");

        if(!(generator instanceof GeneratorSettings gen))throw new RuntimeException("Don't try to spoof GeneratorLike!");

        return new Dimension.Inline(dimensionType,gen,null);
    }
    static DimensionLike createInline(NamespacedKey dimensionType,GeneratorLike generator,String pluginBiomeSource){
        Objects.requireNonNull(dimensionType,"dimensionType");
        Objects.requireNonNull(generator,"generator");
        Objects.requireNonNull(pluginBiomeSource,"pluginBiomeSource");

        if(!(generator instanceof GeneratorSettings gen))throw new RuntimeException("Don't try to spoof GeneratorLike!");

        return new Dimension.Inline(dimensionType,gen,pluginBiomeSource);
    }
    static DimensionLike createFromReference(NamespacedKey id){
        Objects.requireNonNull(id,"id");

        return new Dimension.Reference(id);
    }
}
