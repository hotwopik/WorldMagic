package io.hotwop.worldmagic.api;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.hotwop.worldmagic.generation.GeneratorSettings;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Objects;

public interface GeneratorLike{
    static GeneratorLike createVanillaGeneratorFromSettings(String settings){
        Objects.requireNonNull(settings,"settings");

        JsonObject json=GsonHelper.parse(settings);
        ChunkGenerator generator=ChunkGenerator.CODEC.parse(JsonOps.COMPRESSED,json)
            .getOrThrow(err->new RuntimeException("Error to parse ChunkGenerator: "+err));

        return new GeneratorSettings.Vanilla(generator);
    }
    static GeneratorLike createVanillaGeneratorFromHandle(Object handle){
        Objects.requireNonNull(handle,"handle");

        if(handle instanceof ChunkGenerator ch){
            return new GeneratorSettings.Vanilla(ch);
        }else throw new RuntimeException("Provided object isn't NMS ChunkGenerator");
    }
    static GeneratorLike createPluginGenerator(String id){
        Objects.requireNonNull(id,"id");

        return new GeneratorSettings.Plugin(id);
    }
}
