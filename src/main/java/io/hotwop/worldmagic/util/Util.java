package io.hotwop.worldmagic.util;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.util.versions.MethodMapping;
import io.hotwop.worldmagic.util.versions.MethodVersionWrapper;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class Util{
    private Util(){}

    public static final int dataVersion=SharedConstants.getCurrentVersion().getDataVersion().getVersion();

    private static final MethodMapping<Registry<?>> registryAccessorMapping=new MethodVersionWrapper
        .Builder<Registry<?>>(RegistryAccess.class,"registryOrThrow",null,ResourceKey.class)
        .nameMapping(4082,"lookupOrThrow")
        .build().createMapping(dataVersion);

    private static final MethodMapping<ResourceLocation> resourceLocationCreatorMapping=new MethodVersionWrapper
        .Builder<ResourceLocation>(ResourceLocation.class,"<init>",null,String.class,String.class)
        .nameMapping(3840,"fromNamespaceAndPath")
        .build().createMapping(dataVersion);

    private static final MethodMapping<Optional<Holder.Reference<?>>> registryGetHolderMapping=new MethodVersionWrapper
        .Builder<Optional<Holder.Reference<?>>>(Registry.class,"getHolder",null,ResourceKey.class)
        .nameMapping(4082,"get")
        .build().createMapping(dataVersion);

    private static final MethodMapping<?> registryGetValueMapping=new MethodVersionWrapper
        .Builder<>(Registry.class,"get",null,ResourceLocation.class)
        .nameMapping(4082,"getValue")
        .build().createMapping(dataVersion);

    public static final MethodMapping<GameRules> createGameRulesMapping=new MethodVersionWrapper
        .Builder<GameRules>(GameRules.class,"<init>",null)
        .allParameterMapping(4082,old->new Object[]{net.minecraft.world.flag.FeatureFlagSet.of()},net.minecraft.world.flag.FeatureFlagSet.class)
        .build().createMapping(dataVersion);

    public static final MethodMapping<LevelDataAndDimensions> getLevelDataAndDimensions=new MethodVersionWrapper
        .Builder<LevelDataAndDimensions>(LevelStorageSource.class,"getLevelDataAndDimensions",null,Dynamic.class,WorldDataConfiguration.class,Registry.class,RegistryAccess.Frozen.class)
        .oneParameterMapping(4082,3,net.minecraft.core.HolderLookup.Provider.class,obj->obj)
        .build().createMapping(dataVersion);

    public static final MethodMapping<WorldDimensions> createWorldDimensionsData=new MethodVersionWrapper
        .Builder<WorldDimensions>(DedicatedServerProperties.WorldDimensionData.class,"create",null,RegistryAccess.class)
        .oneParameterMapping(4082,0,net.minecraft.core.HolderLookup.Provider.class,obj->obj)
        .build().createMapping(dataVersion);

    public static final MethodMapping<Void> forceUpgrade=new MethodVersionWrapper
        .Builder<Void>(Main.class,"forceUpgrade",null,LevelStorageSource.LevelStorageAccess.class,WorldData.class,DataFixer.class,boolean.class,BooleanSupplier.class,RegistryAccess.class,boolean.class)
        .allParameterMapping(3839,old->new Object[]{old[0],old[2],old[3],old[4],old[5],old[6]},LevelStorageSource.LevelStorageAccess.class,DataFixer.class,boolean.class,BooleanSupplier.class,RegistryAccess.class,boolean.class)
        .allParameterMapping(4325,old->old,LevelStorageSource.LevelStorageAccess.class,WorldData.class,DataFixer.class,boolean.class,BooleanSupplier.class,RegistryAccess.class,boolean.class)
        .build().createMapping(dataVersion);

    private static final Method getHolderOwner;
    private static final Method setSpawnSettings;
    private static final Method gameRuleVisitor;

    private static final Method datapackWorldGen;

    private static final Method forcedChunksFactory;
    private static final Method forcedChunks;
    private static final Method dimensionDataStorageGet;

    private static final Field frozenField;
    private static final Field byIdField;
    private static final Field toIdField;
    private static final Field byLocationField;
    private static final Field byKeyField;
    private static final Field byValueField;
    private static final Field registrationInfosField;

    private static final Field valueHolderField;

    static{
        ClassLoader loader=Util.class.getClassLoader();

        try {
            frozenField=MappedRegistry.class.getDeclaredField("frozen");
            byIdField=MappedRegistry.class.getDeclaredField("byId");
            toIdField=MappedRegistry.class.getDeclaredField("toId");
            byLocationField=MappedRegistry.class.getDeclaredField("byLocation");
            byKeyField=MappedRegistry.class.getDeclaredField("byKey");
            byValueField=MappedRegistry.class.getDeclaredField("byValue");
            registrationInfosField=MappedRegistry.class.getDeclaredField("registrationInfos");

            valueHolderField=Holder.Reference.class.getDeclaredField("value");
            gameRuleVisitor=GameRules.class.getMethod("visitGameRuleTypes",GameRules.GameRuleTypeVisitor.class);
            datapackWorldGen=WorldLoader.DataLoadContext.class.getMethod("datapackWorldgen");


            if(dataVersion<4082){
                getHolderOwner=Registry.class.getMethod("holderOwner");
                setSpawnSettings=Level.class.getMethod("setSpawnSettings",boolean.class,boolean.class);
            }else{
                getHolderOwner=null;
                setSpawnSettings=null;
            }

            if(dataVersion<4325){
                Class<?> forcedChunksClass=loader.loadClass("net.minecraft.world.level.ForcedChunksSavedData");
                Class<?> savedDataFactoryClass=loader.loadClass("net.minecraft.world.level.saveddata.SavedData$Factory");

                forcedChunksFactory=forcedChunksClass.getMethod("factory");
                forcedChunks=forcedChunksClass.getMethod("getChunks");

                dimensionDataStorageGet=DimensionDataStorage.class.getMethod("get",savedDataFactoryClass,String.class);
            }else{
                forcedChunksFactory=null;
                forcedChunks=null;
                dimensionDataStorageGet=null;
            }
        } catch (NoSuchFieldException|NoSuchMethodException|ClassNotFoundException e) {
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
        Registry<T> reg=getRegistry(access,registry);
        return registryGet(reg,id);
    }

    public static <T> @Nullable T registryGet(Registry<T> registry,NamespacedKey id){
        ResourceLocation rc=createResourceLocation(id);
        return registryGet(registry,rc);
    }

    public static <T> @Nullable T registryGet(Registry<T> registry,ResourceLocation id){
        return (T)registryGetValueMapping.invokeWithExecutor(registry,id);
    }

    @SuppressWarnings("unchecked")
    public static <T> HolderOwner<T> getHolderOwner(Registry<T> registry){
        if(dataVersion<4082){
            try{
                return (HolderOwner<T>)getHolderOwner.invoke(registry);
            }catch(IllegalAccessException|InvocationTargetException e){
                throw new RuntimeException(e);
            }
        }else{
            return registry;
        }
    }

    public static <T> void registerIgnoreFreeze(ResourceKey<Registry<T>> registry, RegistryAccess access,ResourceKey<T> id,T value,Lifecycle lifecycle){
        Registry<T> reg=getRegistry(access,registry);

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
        Registry<T> reg=getRegistry(access,registry);

        Holder.Reference<T> ref=getHolder(reg,id);
        if(ref!=null){
            try{
                valueHolderField.set(ref,value);
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void unregisterAll(ResourceKey<Registry<T>> registry,RegistryAccess access,List<ResourceKey<T>> ids){
        Registry<T> reg=getRegistry(access,registry);

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
                T value=registryGet(reg,id.location());
                Holder.Reference<T> ref=getHolderOrThrow(reg,id);
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
        Registry<T> reg=getRegistry(access,registry);

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
        Registry<T> reg=getRegistry(access,registry);

        for(Map.Entry<ResourceKey<T>,T> entry:entries.entrySet()){
            Holder.Reference<T> ref=getHolder(reg,entry.getKey());
            if(ref!=null){
                try{
                    valueHolderField.set(ref,entry.getValue());
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

    public static boolean hasOneOfPermission(CommandSender sender, String[] permissions){
        for(String permission:permissions){
            if(sender.hasPermission(permission))return true;
        }
        return false;
    }

    public static ResourceLocation createResourceLocation(NamespacedKey id){
        return createResourceLocation(id.namespace(),id.value());
    }

    public static ResourceLocation createResourceLocation(String namespace, String id){
        return resourceLocationCreatorMapping.invoke(namespace,id);
    }

    public static <T> Registry<T> getRegistry(ResourceKey<Registry<T>> id){
        return getRegistry(WorldMagic.vanillaServer().registryAccess(),id);
    }

    public static <T> Registry<T> getRegistry(RegistryAccess access,ResourceKey<Registry<T>> id){
        return (Registry<T>)registryAccessorMapping.invokeWithExecutor(access,id);
    }

    public static <T> Holder.@Nullable Reference<T> getHolder(Registry<T> registry,ResourceKey<T> id){
        return (Holder.Reference<T>)registryGetHolderMapping.invokeWithExecutor(registry, id).orElse(null);
    }

    public static <T> Holder.Reference<T> getHolderOrThrow(Registry<T> registry, ResourceKey<T> id){
        return (Holder.Reference<T>)registryGetHolderMapping.invokeWithExecutor(registry, id).orElseThrow();
    }

    public static void undirtData(DimensionDataStorage st){
        if(dataVersion<4082){
            st.cache.forEach((id, dat)->{
                if(dat!=null)((SavedData)((Object)dat)).setDirty(false);
            });
        }else{
            st.cache.forEach((id, dat)->dat.ifPresent(savedData->savedData.setDirty(false)));
        }
    }

    public static void setSpawnSettings(ServerLevel level,boolean monsters,boolean animals){
        if(dataVersion<4082){
            try{
                setSpawnSettings.invoke(level,monsters,animals);
            }catch(IllegalAccessException|InvocationTargetException e){
                throw new RuntimeException(e);
            }
        }else{
            level.setSpawnSettings(monsters);
            level.tickCustomSpawners(monsters,animals);
        }
    }

    public static GameRules createGameRules(){
        return createGameRulesMapping.invoke();
    }

    public static void visitGameRules(GameRules rules,GameRules.GameRuleTypeVisitor visitor){
        Object executor;

        if(dataVersion<4082)executor=null;
        else executor=rules;

        try{
            gameRuleVisitor.invoke(executor,visitor);
        }catch(IllegalAccessException|InvocationTargetException e){
            throw new RuntimeException(e);
        }
    }

    public static void visitGameRules(GameRules.GameRuleTypeVisitor visitor){
        Object executor;

        if(dataVersion<4082)executor=null;
        else executor=new GameRules(FeatureFlagSet.of());

        try{
            gameRuleVisitor.invoke(executor,visitor);
        }catch(IllegalAccessException|InvocationTargetException e){
            throw new RuntimeException(e);
        }
    }

    public static LevelDataAndDimensions getLevelDataAndDimension(Dynamic<?> dynamic,WorldDataConfiguration dataConfiguration,Registry<LevelStem> dimensionsRegistry){
        try{
            return getLevelDataAndDimensions.invoke(dynamic,dataConfiguration,dimensionsRegistry,datapackWorldGen.invoke(WorldMagic.worldLoader()));
        }catch(IllegalAccessException|InvocationTargetException e){
            throw new RuntimeException(e);
        }
    }

    public static WorldDimensions createWorldDimensions(DedicatedServerProperties.WorldDimensionData data){
        try{
            return createWorldDimensionsData.invokeWithExecutor(data,datapackWorldGen.invoke(WorldMagic.worldLoader()));
        }catch(IllegalAccessException|InvocationTargetException e){
            throw new RuntimeException(e);
        }
    }

    public static void computeForcedChunks(DimensionDataStorage storage,ServerChunkCache chunkCache){
        if(dataVersion<4325){
            try{
                Object factory=forcedChunksFactory.invoke(null);
                Object forcedData=dimensionDataStorageGet.invoke(storage,factory,"chunks");

                if(forcedData!=null){
                    LongList chunks=(LongList)forcedChunks.invoke(forcedData);
                    LongIterator chunksIterator=chunks.iterator();

                    while(chunksIterator.hasNext())chunkCache.updateChunkForced(new ChunkPos(chunksIterator.nextLong()),true);
                }
            }catch(InvocationTargetException|IllegalAccessException e){
                throw new RuntimeException(e);
            }
        }else{
            TicketStorage st=storage.get(TicketStorage.TYPE);
            if(st!=null)st.activateAllDeactivatedTickets();
        }
    }
}
