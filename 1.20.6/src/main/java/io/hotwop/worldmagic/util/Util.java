package io.hotwop.worldmagic.util;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.*;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Util{
    private Util(){}

    private static final Field frozenField;
    private static final Field byIdField;
    private static final Field toIdField;
    private static final Field byLocationField;
    private static final Field byKeyField;
    private static final Field byValueField;
    private static final Field registrationInfosField;

    private static final Field valueHolderField;
    static{
        try {
            frozenField=MappedRegistry.class.getDeclaredField("frozen");
            byIdField=MappedRegistry.class.getDeclaredField("byId");
            toIdField=MappedRegistry.class.getDeclaredField("toId");
            byLocationField=MappedRegistry.class.getDeclaredField("byLocation");
            byKeyField=MappedRegistry.class.getDeclaredField("byKey");
            byValueField=MappedRegistry.class.getDeclaredField("byValue");
            registrationInfosField=MappedRegistry.class.getDeclaredField("registrationInfos");

            valueHolderField=Holder.Reference.class.getDeclaredField("value");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        frozenField.setAccessible(true);
        byIdField.setAccessible(true);
        toIdField.setAccessible(true);
        byLocationField.setAccessible(true);
        byKeyField.setAccessible(true);
        byValueField.setAccessible(true);
        registrationInfosField.setAccessible(true);

        valueHolderField.setAccessible(true);
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
    public static <T> void bindRegistration(ResourceKey<Registry<T>> registry, RegistryAccess access, ResourceKey<T> id, T value){
        Registry<T> reg=access.registryOrThrow(registry);

        Optional<Holder.Reference<T>> ref=reg.getHolder(id);
        if(ref.isPresent()){
            Holder.Reference<T> hold=ref.get();

            try{
                valueHolderField.set(hold,value);
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void unregisterAll(ResourceKey<Registry<T>> registry,RegistryAccess access,List<ResourceKey<T>> ids){
        Registry<T> reg=access.registryOrThrow(registry);

        if(reg instanceof MappedRegistry<T>){
            ObjectList<Holder.Reference<T>> byId;
            Reference2IntMap<T> toId;
            Map<ResourceLocation, Holder.Reference<T>> byLocation;
            Map<ResourceKey<T>, Holder.Reference<T>> byKey;
            Map<T, Holder.Reference<T>> byValue;
            Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

            try{
                byId=(ObjectList<Holder.Reference<T>>)byIdField.get(reg);
                toId=(Reference2IntMap<T>)toIdField.get(reg);
                byLocation=(Map<ResourceLocation, Holder.Reference<T>>)byLocationField.get(reg);
                byKey=(Map<ResourceKey<T>, Holder.Reference<T>>)byKeyField.get(reg);
                byValue=(Map<T, Holder.Reference<T>>)byValueField.get(reg);
                registrationInfos=(Map<ResourceKey<T>, RegistrationInfo>)registrationInfosField.get(reg);
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }

            for(ResourceKey<T> id:ids){
                T value=reg.getOrThrow(id);
                Holder.Reference<T> ref=reg.getHolderOrThrow(id);
                ResourceLocation loc=id.location();

                int index=byId.indexOf(ref);
                byId.remove(ref);

                toId.remove(value,index);
                byLocation.remove(loc);
                byKey.remove(id);
                byValue.remove(value);
                registrationInfos.remove(id);
            }
        }else throw new RuntimeException("Can't uregister not mapped registry");
    }

    public static <T> void registerIgnoreFreezeAll(ResourceKey<Registry<T>> registry,RegistryAccess access,Map<ResourceKey<T>,T> entries,Lifecycle lifecycle){
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

                registerIgnoreFreezeAllOp(wr,entries,lifecycle);

                if(frozen){
                    try {
                        frozenField.setBoolean(map, true);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else registerIgnoreFreezeAllOp(wr,entries,lifecycle);
        }else throw new RuntimeException("Registry isn't writable at all");
    }

    private static <T> void registerIgnoreFreezeAllOp(WritableRegistry<T> registry,Map<ResourceKey<T>,T> entries,Lifecycle lifecycle){
        RegistrationInfo info=new RegistrationInfo(Optional.empty(),lifecycle);

        for(Map.Entry<ResourceKey<T>,T> entry:entries.entrySet()){
            registry.register(entry.getKey(),entry.getValue(),info);
        }
    }

    public static <T> void bindRegistrations(ResourceKey<Registry<T>> registry,RegistryAccess access,Map<ResourceKey<T>,T> entries){
        Registry<T> reg=access.registryOrThrow(registry);

        for(Map.Entry<ResourceKey<T>,T> entry:entries.entrySet()){
            Optional<Holder.Reference<T>> ref=reg.getHolder(entry.getKey());
            if(ref.isPresent()){
                Holder.Reference<T> hold=ref.get();

                try{
                    valueHolderField.set(hold,entry.getValue());
                }catch(IllegalAccessException e){
                    throw new RuntimeException(e);
                }
            }
        }
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
