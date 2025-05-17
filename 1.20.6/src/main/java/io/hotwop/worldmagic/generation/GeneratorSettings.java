package io.hotwop.worldmagic.generation;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.util.serializer.EnumSwitchSerializer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.extra.dfu.v4.ConfigurateOps;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public sealed interface GeneratorSettings permits GeneratorSettings.Vanilla,GeneratorSettings.Plugin{
    TypeSerializerCollection serializer=TypeSerializerCollection.builder()
        .register(GeneratorSettings.class,EnumSwitchSerializer.builder(GeneratorSettings.class, GenType.class,"type","settings")
            .define(GenType.vanilla, Vanilla.class)
            .define(GenType.plugin, Plugin.class)
            .build()
        )
        .register(Plugin.Serializer.instance)
        .register(Vanilla.class, Vanilla.Serializer.instance)
        .build();

    enum GenType {
        vanilla,
        plugin
    }

    DedicatedServerProperties.WorldDimensionData create();

    record Vanilla(ChunkGenerator generator) implements GeneratorSettings {
        public DedicatedServerProperties.WorldDimensionData create(){
            JsonObject obj=(JsonObject) ChunkGenerator.CODEC.encode(generator,WorldMagic.vanillaServer().registryAccess().createSerializationContext(JsonOps.INSTANCE),new JsonObject()).getOrThrow();
            return new DedicatedServerProperties.WorldDimensionData(obj.getAsJsonObject("settings"),obj.getAsJsonPrimitive("type").getAsString());
        }

        public static final class Serializer implements TypeSerializer<Vanilla> {
            public static final Serializer instance = new Serializer();

            private Serializer() {}

            public Vanilla deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
                ChunkGenerator gen = ChunkGenerator.CODEC.parse(WorldMagic.vanillaServer().registryAccess().createSerializationContext(ConfigurateOps.instance(true)), node)
                    .getOrThrow(err -> new SerializationException(node, GeneratorSettings.class, "Error to deserialize vanilla generator settings: " + err));
                return new Vanilla(gen);
            }

            public void serialize(@NotNull Type type, @Nullable Vanilla obj, @NotNull ConfigurationNode node) throws SerializationException {
                if (obj == null) return;

                ChunkGenerator.CODEC.encode(obj.generator,WorldMagic.vanillaServer().registryAccess().createSerializationContext(ConfigurateOps.instance(true)),node)
                    .getOrThrow(err -> new SerializationException(node, GeneratorSettings.class, "Error to serialize vanilla generator settings: " + err));
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
