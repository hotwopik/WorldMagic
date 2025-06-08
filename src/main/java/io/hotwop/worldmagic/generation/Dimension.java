package io.hotwop.worldmagic.generation;

import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.api.DimensionLike;
import io.hotwop.worldmagic.util.VersionUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.bukkit.NamespacedKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

public sealed interface Dimension extends DimensionLike permits Dimension.Reference,Dimension.Inline{
    LevelStem get();
    ResourceKey<LevelStem> getKey();

    record Reference(
        NamespacedKey id
    ) implements Dimension{
        public ResourceKey<LevelStem> getKey(){
            return ResourceKey.create(Registries.LEVEL_STEM, VersionUtil.createResourceLocation(id));
        }
        public LevelStem get() {
            return VersionUtil.registryGet(Registries.LEVEL_STEM,WorldMagic.vanillaServer().registryAccess(),id);
        }
    }

    record Inline(
        NamespacedKey dimensionType,
        GeneratorSettings generator,
        @Nullable String pluginBiomes
    ) implements Dimension{
        public static final FlatLevelSource emptyGenerator;

        static{
            Registry<Biome> biomeRegistry= VersionUtil.getRegistry(Registries.BIOME);

            emptyGenerator=new FlatLevelSource(new FlatLevelGeneratorSettings(
                Optional.empty(),
                Holder.Reference.createStandAlone(VersionUtil.getHolderOwner(biomeRegistry),Biomes.THE_VOID),
                List.of()
            ));
        }

        public LevelStem get(){
            Registry<DimensionType> type= VersionUtil.getRegistry(Registries.DIMENSION_TYPE);
            return new LevelStem(
                VersionUtil.getHolderOrThrow(type,ResourceKey.create(
                    Registries.DIMENSION_TYPE,
                    VersionUtil.createResourceLocation(dimensionType)),
                    ()->new RuntimeException("Error to get dimension type: "+dimensionType.asString())
                ),
                (generator instanceof GeneratorSettings.Vanilla(ChunkGenerator generatorVan))?generatorVan:emptyGenerator
            );
        }
        public ResourceKey<LevelStem> getKey() {
            Registry<LevelStem> reg= VersionUtil.getRegistry(Registries.LEVEL_STEM);
            ResourceLocation loc=reg.getKey(get());
            if(loc==null)return null;
            return ResourceKey.create(Registries.LEVEL_STEM,loc);
        }
    }

    final class Serializer implements TypeSerializer<Dimension>{
        public static final Serializer instance=new Serializer();
        private Serializer(){}

        public Dimension deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
            if(node.isMap()){
                ConfigurationNode dimensionNode=node.node("dimension-type");
                ConfigurationNode generatorNode=node.node("generator");

                if(dimensionNode.virtual())throw new SerializationException(node,Dimension.class,"Missing dimension-type node");
                if(generatorNode.virtual())throw new SerializationException(node,Dimension.class,"Missing generator node");

                ConfigurationNode pluginBiomeNode=node.node("plugin-biomes");

                NamespacedKey dimension=dimensionNode.require(NamespacedKey.class);
                GeneratorSettings settings=generatorNode.require(GeneratorSettings.class);
                String pluginBiome;

                if(pluginBiomeNode.virtual())pluginBiome=null;
                else pluginBiome=pluginBiomeNode.getString();

                return new Inline(dimension,settings,pluginBiome);
            }else{
                NamespacedKey dimension=node.require(NamespacedKey.class);
                return new Reference(dimension);
            }
        }
        public void serialize(@NotNull Type type, @Nullable Dimension obj, @NotNull ConfigurationNode node) throws SerializationException {
            switch (obj) {
                case null -> {
                }
                case Reference(NamespacedKey id) -> node.set(id);
                case Inline(NamespacedKey dimensionType, GeneratorSettings generator, String pluginGenerator) -> {
                    node.node("dimension-type").set(dimensionType);
                    node.node("generator").set(generator);
                    if(pluginGenerator!=null)node.node("plugin-biomes").set(pluginGenerator);
                }
                default -> throw new SerializationException();
            }

        }
    }
}
