package io.hotwop.worldmagic.util;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.WorldMagicBootstrap;
import io.hotwop.worldmagic.version.MethodMapping;
import io.hotwop.worldmagic.version.MethodVersionWrapper;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Main;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.*;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class VersionUtil {
    private VersionUtil(){}

    public static final int dataVersion= WorldMagicBootstrap.getDataVersion();

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
        .allParameterMapping(4082,old->new Object[]{WorldMagic.worldLoader().dataConfiguration().enabledFeatures()},net.minecraft.world.flag.FeatureFlagSet.class)
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

    static{
        ClassLoader loader= VersionUtil.class.getClassLoader();

        try {
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
        } catch (NoSuchMethodException|ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
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

    public static <T> Holder.Reference<T> getHolderOrThrow(Registry<T> registry, ResourceKey<T> id, Supplier<RuntimeException> err){
        return (Holder.Reference<T>)registryGetHolderMapping.invokeWithExecutor(registry, id).orElseThrow(err);
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

    public static GameRules createGameRulesFromContext(CommandBuildContext ctx){
        if(dataVersion<4082)return createGameRules();
        return new GameRules(ctx.enabledFeatures());
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
