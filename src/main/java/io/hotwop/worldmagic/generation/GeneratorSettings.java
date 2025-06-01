package io.hotwop.worldmagic.generation;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.util.dfu.ConfigurateOps;
import io.hotwop.worldmagic.util.serializer.EnumSwitchSerializer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public sealed interface GeneratorSettings permits GeneratorSettings.Vanilla,GeneratorSettings.Plugin{
    TypeSerializerCollection serializer=TypeSerializerCollection.builder()
        .register(Plugin.Serializer.instance)
        .register(Vanilla.class,Vanilla.Serializer.instance)

        .registerExact(GeneratorSettings.class,EnumSwitchSerializer.builder(GeneratorSettings.class, GenType.class,"source","settings")
            .define(GenType.vanilla, Vanilla.class)
            .define(GenType.plugin, Plugin.class)
            .build()
        )
        .build();

    enum GenType {
        vanilla,
        plugin
    }

    DedicatedServerProperties.WorldDimensionData create();

    record Vanilla(ChunkGenerator generator) implements GeneratorSettings {
        public DedicatedServerProperties.WorldDimensionData create(){
            if(generator instanceof FlatLevelSource fl){
                JsonObject obj=(JsonObject)FlatLevelGeneratorSettings.CODEC.encode(fl.settings(),WorldMagic.vanillaServer().registryAccess().createSerializationContext(JsonOps.INSTANCE),new JsonObject()).getOrThrow();
                return new DedicatedServerProperties.WorldDimensionData(obj,"flat");
            }
            return new DedicatedServerProperties.WorldDimensionData(new JsonObject(),"normal");
        }

        public static final class Serializer implements TypeSerializer<Vanilla> {
            public static final Serializer instance = new Serializer();
            private Serializer() {}

            public Vanilla deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
                ChunkGenerator gen = ChunkGenerator.CODEC.parse(WorldMagic.vanillaServer().registryAccess().createSerializationContext(ConfigurateOps.instance()), node)
                    .getOrThrow(err -> new SerializationException(node, GeneratorSettings.class, "Error to deserialize vanilla generator settings:\n     " + err.replace(";","\n    ")));
                return new Vanilla(gen);
            }

            public void serialize(@NotNull Type type, @Nullable Vanilla obj, @NotNull ConfigurationNode node) throws SerializationException {
                if (obj == null) return;

                ChunkGenerator.CODEC.encode(obj.generator,WorldMagic.vanillaServer().registryAccess().createSerializationContext(ConfigurateOps.instance()),node)
                    .getOrThrow(err -> new SerializationException(node, GeneratorSettings.class, "Error to serialize vanilla generator settings:\n     " + err.replace(";","\n    ")));
            }
        }
    }

    record Plugin(String generator) implements GeneratorSettings {
        public DedicatedServerProperties.WorldDimensionData create(){
            return new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse("{\"layers\":[],\"biome\":\"the_void\"}"),"flat");
        }

        public static final class Serializer extends ScalarSerializer<Plugin> {
            public static final Serializer instance = new Serializer();

            private Serializer() {
                super(Plugin.class);
            }

            public Plugin deserialize(@NotNull Type type, @NotNull Object obj) throws SerializationException {
                if (obj instanceof String str) return new Plugin(str);
                throw new SerializationException("Generator name expected as plugin generator");
            }

            protected @NotNull Object serialize(Plugin item, @NotNull Predicate<Class<?>> typeSupported) {
                return item.generator;
            }
        }
    }
}
