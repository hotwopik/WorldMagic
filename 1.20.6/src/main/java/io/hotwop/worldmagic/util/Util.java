package io.hotwop.worldmagic.util;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Util{
    private Util(){}

    private static final Field frozenField;
    static{
        try {
            frozenField=MappedRegistry.class.getDeclaredField("frozen");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        frozenField.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    public static void fromMap(ConfigurationNode node, Object value) throws SerializationException {
        if(value instanceof Map){
            for(Map.Entry<Object,Object> entry:((Map<Object,Object>)value).entrySet()){
                fromMap(node.node(entry.getKey()),entry.getValue());
            }
        }
        else if(value instanceof List){
            for(Object val:(List<Object>)value){
                fromMap(node.appendListNode(),val);
            }
        }
        else node.set(value);
    }

    public static <T> @Nullable T registryGet(ResourceKey<Registry<T>> registry, RegistryAccess access, NamespacedKey id){
        Registry<T> reg=access.registryOrThrow(registry);
        return reg.get(new ResourceLocation(id.namespace(),id.value()));
    }

    public static <T> void registerIgnoreFreeze(ResourceKey<Registry<T>> registry, RegistryAccess access,ResourceKey<T> id,T value,Lifecycle lifecycle){
        Registry<T> reg=access.registryOrThrow(registry);

        if(reg instanceof WritableRegistry<T> wr){
            if(wr instanceof MappedRegistry<T> map){
                boolean frozen;
                try{
                    frozen=frozenField.getBoolean(map);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                if(frozen){
                    try {
                        frozenField.setBoolean(map, false);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                map.register(id,value,new RegistrationInfo(Optional.empty(),lifecycle));

                if(frozen){
                    try {
                        frozenField.setBoolean(map, true);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else wr.register(id,value,new RegistrationInfo(Optional.empty(),lifecycle));
        }else throw new RuntimeException("Registry isn't writable at all");
    }

    public static net.minecraft.world.Difficulty mapDifficulty(Difficulty difficulty){
        switch(difficulty){
            case PEACEFUL->{return net.minecraft.world.Difficulty.PEACEFUL;}
            case EASY->{return net.minecraft.world.Difficulty.EASY;}
            case NORMAL->{return net.minecraft.world.Difficulty.NORMAL;}
            case HARD->{return net.minecraft.world.Difficulty.HARD;}
            default->throw new RuntimeException();
        }
    }
    public static GameType mapGameMode(GameMode gamemode){
        switch(gamemode){
            case SURVIVAL->{return GameType.SURVIVAL;}
            case CREATIVE->{return GameType.CREATIVE;}
            case ADVENTURE->{return GameType.ADVENTURE;}
            case SPECTATOR->{return GameType.SPECTATOR;}
            default->throw new RuntimeException();
        }
    }

    public static boolean isStable(ResourceKey<LevelStem> key, LevelStem data){
        return (key==LevelStem.OVERWORLD&&isStableOverworld(data))||(key==LevelStem.NETHER&&isStableNether(data))||(key==LevelStem.END&&isStableEnd(data));
    }

    public static boolean isStableOverworld(LevelStem dimensionOptions) {
        Holder<DimensionType> holder = dimensionOptions.type();
        return (
            holder.is(BuiltinDimensionTypes.OVERWORLD)||
                holder.is(BuiltinDimensionTypes.OVERWORLD_CAVES)
        )&&(
            !(dimensionOptions.generator().getBiomeSource() instanceof MultiNoiseBiomeSource multiNoiseBiomeSource)||
                multiNoiseBiomeSource.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
        );
    }

    public static boolean isStableNether(LevelStem dimensionOptions) {
        return dimensionOptions.type().is(BuiltinDimensionTypes.NETHER)
            && dimensionOptions.generator() instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator
            && noiseBasedChunkGenerator.stable(NoiseGeneratorSettings.NETHER)
            && noiseBasedChunkGenerator.getBiomeSource() instanceof MultiNoiseBiomeSource multiNoiseBiomeSource
            && multiNoiseBiomeSource.stable(MultiNoiseBiomeSourceParameterLists.NETHER);
    }

    public static boolean isStableEnd(LevelStem dimensionOptions) {
        return dimensionOptions.type().is(BuiltinDimensionTypes.END)
            && dimensionOptions.generator() instanceof NoiseBasedChunkGenerator noiseBasedChunkGenerator
            && noiseBasedChunkGenerator.stable(NoiseGeneratorSettings.END)
            && noiseBasedChunkGenerator.getBiomeSource() instanceof TheEndBiomeSource;
    }

    public static World.Environment mapEnvironment(Holder<DimensionType> dimensionType){
        if(dimensionType.is(BuiltinDimensionTypes.OVERWORLD)||dimensionType.is(BuiltinDimensionTypes.OVERWORLD_CAVES))return World.Environment.NORMAL;
        if(dimensionType.is(BuiltinDimensionTypes.NETHER))return World.Environment.NETHER;
        if(dimensionType.is(BuiltinDimensionTypes.END))return World.Environment.THE_END;
        return World.Environment.CUSTOM;
    }
}
