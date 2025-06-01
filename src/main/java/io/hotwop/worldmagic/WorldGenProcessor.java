package io.hotwop.worldmagic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import io.hotwop.worldmagic.util.Util;
import io.hotwop.worldmagic.util.dfu.ConfigurateOps;
import io.hotwop.worldmagic.util.serializer.NamespacedKeySerializer;
import io.leangen.geantyref.TypeToken;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static io.hotwop.worldmagic.WorldMagic.*;

public final class WorldGenProcessor{
    private WorldGenProcessor(){}

    private static final Map<NamespacedKey, DimensionType> dimensionTypes=new HashMap<>();
    public static DimensionType getPluginDimensionType(NamespacedKey id){
        return dimensionTypes.get(id);
    }
    public static Map<NamespacedKey,DimensionType> getPluginDimensionTypes(){
        return Map.copyOf(dimensionTypes);
    }

    private static final List<WorldGenStatement<?>> worldGen=List.of(
        // Generators
        new WorldGenStatement<>(
            "noise",
            new TypeToken<>(){},
            NormalNoise.NoiseParameters.DIRECT_CODEC,
            new HashMap<>(),
            Registries.NOISE
        ),
        new WorldGenStatement<>(
            "density_function",
            new TypeToken<>(){},
            DensityFunction.DIRECT_CODEC,
            new HashMap<>(),
            Registries.DENSITY_FUNCTION
        ),
        new WorldGenStatement<>(
            "noise_settings",
            new TypeToken<>(){},
            NoiseGeneratorSettings.DIRECT_CODEC,
            new HashMap<>(),
            Registries.NOISE_SETTINGS
        ),

        // Biome
        new WorldGenStatement<>(
            "configured_carver",
            new TypeToken<>(){},
            ConfiguredWorldCarver.DIRECT_CODEC,
            new HashMap<>(),
            Registries.CONFIGURED_CARVER
        ),
        new WorldGenStatement<>(
            "configured_feature",
            new TypeToken<>(){},
            ConfiguredFeature.DIRECT_CODEC,
            new HashMap<>(),
            Registries.CONFIGURED_FEATURE
        ),
        new WorldGenStatement<>(
            "placed_feature",
            new TypeToken<>(){},
            PlacedFeature.DIRECT_CODEC,
            new HashMap<>(),
            Registries.PLACED_FEATURE
        ),

        new WorldGenStatement<>(
            "biome",
            new TypeToken<>(){},
            Biome.DIRECT_CODEC,
            new HashMap<>(),
            Registries.BIOME
        ),

        new WorldGenStatement<>(
            "processor_list",
            new TypeToken<>(){},
            StructureProcessorType.DIRECT_CODEC,
            new HashMap<>(),
            Registries.PROCESSOR_LIST
        ),
        new WorldGenStatement<>(
            "template_pool",
            new TypeToken<>(){},
            StructureTemplatePool.DIRECT_CODEC,
            new HashMap<>(),
            Registries.TEMPLATE_POOL
        ),
        new WorldGenStatement<>(
            "structure",
            new TypeToken<>(){},
            Structure.DIRECT_CODEC,
            new HashMap<>(),
            Registries.STRUCTURE
        ),
        new WorldGenStatement<>(
            "structure_set",
            new TypeToken<>(){},
            StructureSet.DIRECT_CODEC,
            new HashMap<>(),
            Registries.STRUCTURE_SET
        )
    );

    private record WorldGenStatement<T>(
        String name,
        TypeToken<T> clazz,
        Codec<T> codec,
        Map<NamespacedKey,T> map,
        ResourceKey<Registry<T>> registry
    ){
        public void load(){
            Path directoryPath=worldGenPath().resolve(name);

            File directory=directoryPath.toFile();
            if(!directory.exists()||!directory.isDirectory())return;

            Map<Path, YamlConfigurationLoader> loaders=new HashMap<>();

            try(Stream<Path> stream=Files.walk(directoryPath)){
                stream.filter(pt->Files.isRegularFile(pt)&&pt.toFile().getName().endsWith(".yml")).forEach(pt->loaders.computeIfAbsent(pt,WorldGenProcessor::createWorldGenLoader));
            }catch(IOException e){
                logger().error("Error to load {} files: {}",name,e.toString());
                return;
            }

            map.clear();

            Map<T,Path> patches=new HashMap<>();
            Map<NamespacedKey,List<Path>> conflicts=new HashMap<>();

            RegistryAccess.Frozen access=vanillaServer().registryAccess();
            RegistryOps<ConfigurationNode> ops=access.createSerializationContext(ConfigurateOps.instance());

            loaders.forEach((path,loader)->{
                try{
                    CommentedConfigurationNode node=loader.load();
                    if(node.isNull()){
                        logger().warn("File {} is empty, error to load {}",path,name);
                        return;
                    }

                    CommentedConfigurationNode idNode=node.node("id");
                    if(idNode.virtual())throw new SerializationException(node,clazz.getType(),"id node is required");
                    NamespacedKey id=idNode.require(NamespacedKey.class);

                    if(conflicts.containsKey(id)){
                        conflicts.get(id).add(path);
                        return;
                    }
                    if(map.containsKey(id)){
                        T conflict=map.get(id);
                        Path conflictPath=patches.get(conflict);

                        map.remove(id);
                        patches.remove(conflict);

                        List<Path> conflictLs=new ArrayList<>();
                        conflictLs.add(path);
                        conflictLs.add(conflictPath);
                        conflicts.put(id,conflictLs);

                        return;
                    }

                    T file=codec.parse(ops,node)
                        .getOrThrow(err->new SerializationException(node,clazz.getType(),"Error to deserialize "+name+":\n     "+err.replace(";","\n    ")));

                    map.put(id,file);
                    patches.put(file,path);
                }catch(ConfigurateException ex){
                    logger().warn("Error to load {} file {}: {}",name,path.toString(),ex.getMessage());
                }
            });

            Map<ResourceKey<T>,T> vanillaMap=new HashMap<>();
            map.forEach((id,val)->vanillaMap
                .put(ResourceKey.create(registry, Util.createResourceLocation(id)),val)
            );

            Util.registerIgnoreFreezeAll(registry,access,vanillaMap,Lifecycle.experimental());
            Util.bindRegistrations(registry,access,vanillaMap);
        }
    }

    public <T> @Nullable T getPluginWorldGenElement(Class<T> elementClass,NamespacedKey id){
        return findStatement(elementClass).map(st->st.map.get(id)).orElse(null);
    }

    public <T> @Nullable Map<NamespacedKey,T> getPluginWorldGenElements(Class<T> elementClass){
        return findStatement(elementClass).map(st->Map.copyOf(st.map)).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<WorldGenStatement<T>> findStatement(Class<T> elementClass){
        return worldGen.stream()
            .filter(st->{
                Type inner=st.clazz.getType();
                if(inner.equals(elementClass))return true;

                if(inner instanceof ParameterizedType param&&param.getRawType().equals(elementClass))return true;
                return false;
            })
            .map(st->(WorldGenStatement<T>)st)
            .findAny();
    }

    public static void loadDimensionTypes(){
        logger().info("Loading dimension types...");
        Map<Path, YamlConfigurationLoader> loaders=new HashMap<>();

        try(Stream<Path> stream=Files.walk(dimensionTypesPath())){
            stream.filter(pt->Files.isRegularFile(pt)&&pt.toFile().getName().endsWith(".yml")).forEach(pt->loaders.computeIfAbsent(pt,WorldGenProcessor::createWorldGenLoader));
        }catch(IOException e){
            logger().error("Error to load dimension type files: {}",e.toString());
            return;
        }

        dimensionTypes.clear();

        Map<DimensionType,Path> patches=new HashMap<>();
        Map<NamespacedKey,List<Path>> conflicts=new HashMap<>();

        RegistryAccess.Frozen access=vanillaServer().registryAccess();
        RegistryOps<ConfigurationNode> ops=access.createSerializationContext(ConfigurateOps.instance());

        loaders.forEach((path,loader)->{
            try{
                CommentedConfigurationNode node=loader.load();
                if(node.isNull()){
                    logger().warn("File {} is empty, error to load dimension type", path);
                    return;
                }

                CommentedConfigurationNode idNode=node.node("id");
                if(idNode.virtual())throw new SerializationException(node,DimensionType.class,"id node is required");
                NamespacedKey id=idNode.require(NamespacedKey.class);

                if(conflicts.containsKey(id)){
                    conflicts.get(id).add(path);
                    return;
                }
                if(dimensionTypes.containsKey(id)){
                    DimensionType conflict=dimensionTypes.get(id);
                    Path conflictPath=patches.get(conflict);

                    dimensionTypes.remove(id);
                    patches.remove(conflict);

                    List<Path> conflictLs=new ArrayList<>();
                    conflictLs.add(path);
                    conflictLs.add(conflictPath);
                    conflicts.put(id,conflictLs);

                    return;
                }

                DimensionType file=DimensionType.DIRECT_CODEC.parse(ops,node)
                    .getOrThrow(err->new SerializationException(node,DimensionType.class,"Error to deserialize dimension type:\n     "+err.replace(";","\n    ")));

                dimensionTypes.put(id,file);
                patches.put(file,path);
            }catch(ConfigurateException ex){
                logger().warn("Error to load dimension type file {}: {}",path.toString(),ex.getMessage());
            }
        });

        conflicts.forEach((id,files)->{
            StringBuilder builder=new StringBuilder();
            boolean separator=false;
            for(Path path:files){
                if(separator)builder.append(", ");
                builder.append(path.toString());
                if(!separator)separator=true;
            }

            logger().warn("Error to load dimension type files due ID duplication: {}",builder);
        });

        Map<ResourceKey<DimensionType>,DimensionType> vanillaDimensionTypes=new HashMap<>();
        dimensionTypes.forEach((id,type)->vanillaDimensionTypes
            .put(ResourceKey.create(Registries.DIMENSION_TYPE, Util.createResourceLocation(id)),type)
        );

        Util.registerIgnoreFreezeAll(Registries.DIMENSION_TYPE,access,vanillaDimensionTypes, Lifecycle.experimental());
        Util.bindRegistrations(Registries.DIMENSION_TYPE,access,vanillaDimensionTypes);

        logger().info("Dimension types loaded");
    }

    public static void loadWorldGen(){
        logger().info("Loading worldgen...");

        worldGen.forEach(WorldGenStatement::load);

        logger().info("Worldgen loaded");
    }

    private static YamlConfigurationLoader createWorldGenLoader(Path path){
        return YamlConfigurationLoader.builder()
            .path(path)
            .indent(2)
            .defaultOptions(opts->opts
                .serializers(ser->ser

                    .register(NamespacedKeySerializer.instance)
                )
            )
            .build();
    }
}
