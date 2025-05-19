package io.hotwop.worldmagic.generation;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.file.WorldFile;
import io.hotwop.worldmagic.util.ImmutableLocation;
import io.hotwop.worldmagic.util.Util;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Main;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.*;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.hotwop.worldmagic.WorldMagic.*;

public final class CustomWorld {
    private static final ExecutorService threadManager=Executors.newCachedThreadPool();
    private static final Method vanillaSetSpawn;
    private static final Field ioExecutorField;

    private static final NamespacedKey overworld=NamespacedKey.minecraft("overworld");

    static{
        try {
            vanillaSetSpawn=MinecraftServer.class.getDeclaredMethod("setInitialSpawn", ServerLevel.class, ServerLevelData.class, boolean.class, boolean.class);
            ioExecutorField=DimensionDataStorage.class.getDeclaredField("ioExecutor");
        } catch (NoSuchMethodException|NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        vanillaSetSpawn.setAccessible(true);
        ioExecutorField.setAccessible(true);
    }

    private static boolean shutdown=false;
    public static void shutdownAsync(){
        if(shutdown)return;
        shutdown=true;

        threadManager.shutdown();

        try{
            threadManager.awaitTermination(5,TimeUnit.SECONDS);
        }catch(InterruptedException e){
            threadManager.shutdownNow();
        }
    }

    public final NamespacedKey id;
    public final String bukkitId;
    public final String folder;
    public final WorldProperties worldProperties;
    public final Dimension dimension;
    public final Loading loading;
    public final SpawnPosition spawnPosition;
    public final Location callbackLocation;
    public final AllowSettings allowSettings;
    public final GameRuleFactory gamerules;

    public record WorldProperties(
        long seed,
        boolean generateStructures,
        boolean bonusChest,
        GameType defaultGamemode,
        Difficulty difficulty,
        boolean hardcore
    ){
        public WorldProperties(WorldFile.WorldProperties file){
            this(file.seed,file.generateStructures,file.bonusChest,Util.mapGameMode(file.defaultGamemode),Util.mapDifficulty(file.difficulty),file.hardcore);
        }
    }

    public record Loading(
        boolean async,
        boolean startup,
        boolean override,
        boolean loadChunks,
        boolean save,
        boolean folderDeletion
    ){
        public Loading(WorldFile.Loading file){
            this(file.async,file.startup,file.override,file.loadChunks,file.save,file.folderDeletion);
        }
    }

    public record SpawnPosition(
        boolean override,
        int x,
        int y,
        int z,
        float yaw
    ){
        public SpawnPosition(WorldFile.SpawnPosition file){
            this(file.override,file.x,file.y,file.z,file.yaw);
        }
    }

    public record AllowSettings(
        boolean animals,
        boolean monsters
    ){
        public AllowSettings(WorldFile.AllowSettings file){
            this(file.animals,file.monsters);
        }
    }

    public final Path folderPath;
    public final ResourceLocation vanillaLocId;
    public final ResourceKey<LevelStem> vanillaId;
    public final ResourceKey<Level> vanillaLevelId;
    public final ResourceKey<LevelStem> dimensionId;
    public final LevelStem stem;

    private boolean loaded=false;
    public boolean loaded(){
        return loaded;
    }

    private World bukkitWorld=null;
    private ServerLevel level=null;

    public World world(){
        return bukkitWorld;
    }

    public CustomWorld(WorldFile file){
        id=file.id;

        vanillaLocId=new ResourceLocation(id.namespace(),id.value());
        vanillaId=ResourceKey.create(Registries.LEVEL_STEM,vanillaLocId);
        vanillaLevelId=ResourceKey.create(Registries.DIMENSION,vanillaLocId);

        bukkitId=file.bukkitId==null?(id.namespace().equals(NamespacedKey.MINECRAFT)?id.value():id.namespace()+"_"+id.value()):file.bukkitId;

        folder=file.folder;
        folderPath=Bukkit.getWorldContainer().toPath().resolve(folder==null?bukkitId:folder);

        worldProperties=new WorldProperties(file.worldProperties);
        dimension=file.dimension;
        loading=new Loading(file.loading);
        spawnPosition=file.spawnPosition==null?null:new SpawnPosition(file.spawnPosition);
        callbackLocation=file.callbackLocation==null?null:new ImmutableLocation(file.callbackLocation);
        allowSettings=new AllowSettings(file.allowSettings);
        gamerules=file.gamerules;

        if(dimension instanceof Dimension.Reference ref)dimensionId=ref.getKey();
        else dimensionId=vanillaId;

        stem=dimension.get();
    }

    public void load(){
        if(shutdown)return;

        if(loaded)throw new RuntimeException("World already loaded!");
        loaded=true;

        if(loading.async)threadManager.execute(this::loadProcess);
        else{
            if(vanillaServer().isSameThread())loadProcess();
            else scheduler().runTask(instance(),this::loadProcess);
        }
    }

    public void unload(){
        if(shutdown)return;
        if(!loaded)throw new RuntimeException("World already unloaded!");
        loaded=false;

        List<ServerPlayer> players=List.copyOf(level.players());

        if((loading.save&&Bukkit.isStopping())||players.isEmpty()){
            if(vanillaServer().isSameThread())unloadProcess();
            else scheduler().runTask(instance(),this::unloadProcess);
        }else{
            AtomicInteger task=new AtomicInteger(players.size());

            Location loc;
            if(callbackLocation==null)loc=Bukkit.getWorld(overworld).getSpawnLocation();
            else loc=callbackLocation;

            players.forEach(pl->pl.getBukkitEntity().teleportAsync(loc).thenAccept(done->{
                if(!done)pl.getBukkitEntity().teleport(loc);

                if(task.addAndGet(-1)==0)unloadProcess();
            }));
        }
    }

    @SuppressWarnings("deprecation")
    private void loadProcess(){
        if(checkDuplication()){
            logger().error("Error to load custom world {}, world with that vanilla id or bukkit id already loaded",id.asMinimalString());
            loaded=false;
            return;
        }

        if(folderPath.toFile().isFile()){
            logger().error("Error to load custom world {}, world \"folder\" is file",id.asMinimalString());
            loaded=false;
            return;
        }

        logger().info("Creating world {}",id.asMinimalString());

        LevelStorageSource.LevelStorageAccess levelStorage;

        Path container=folderPath.getParent();
        String folderName=container.relativize(folderPath).toString();

        try{
            levelStorage=LevelStorageSource.createDefault(container).validateAndCreateAccess(folderName,dimensionId);
        } catch (ContentValidationException | IOException e) {
            logger().error("Error to load custom world {}, folder creation error: {}",id.asMinimalString(),e.toString());
            loaded=false;
            return;
        }

        Dynamic<?> save;
        if (levelStorage.hasWorldData() && !loading.override) {
            LevelSummary worldinfo;

            try {
                save = levelStorage.getDataTag();
                worldinfo = levelStorage.getSummary(save);
            } catch (NbtException | ReportedNbtException | IOException ioexception) {
                LevelStorageSource.LevelDirectory directory = levelStorage.getLevelDirectory();

                MinecraftServer.LOGGER.warn("Failed to load world data from {}", directory.dataFile(), ioexception);
                MinecraftServer.LOGGER.info("Attempting to use fallback");

                try {
                    save = levelStorage.getDataTagFallback();
                    worldinfo = levelStorage.getSummary(save);
                } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                    MinecraftServer.LOGGER.error("Failed to load world data from {}", directory.oldDataFile(), ioexception1);
                    MinecraftServer.LOGGER.error("Failed to load world data from {} and {}. World files may be corrupted. Shutting down.", directory.dataFile(), directory.oldDataFile());
                    loaded=false;
                    return;
                }

                levelStorage.restoreLevelDataFromOld();
            }

            if (worldinfo.requiresManualConversion()) {
                MinecraftServer.LOGGER.info("This world must be opened in an older version (like 1.6.4) to be safely converted");
                loaded=false;
                return;
            }

            if (!worldinfo.isCompatible()) {
                MinecraftServer.LOGGER.info("This world was created by an incompatible version.");
                loaded=false;
                return;
            }
        } else {
            save = null;
        }

        GeneratorSettings generator;
        String pluginBiomeProvider;

        if(dimension instanceof Dimension.Reference){
            pluginBiomeProvider=null;
            generator=new GeneratorSettings.Vanilla(stem.generator());
        }else if(dimension instanceof Dimension.Inline(NamespacedKey typ,GeneratorSettings gen,String pluginBiomes)){
            generator=gen;
            pluginBiomeProvider=pluginBiomes;
        }else throw new RuntimeException();

        Registry<LevelStem> dimensionRegistry;
        PrimaryLevelData levelData;
        PrimaryLevelData.SpecialWorldProperty specialProperty;

        if(save!=null){
            LevelDataAndDimensions saveData=LevelStorageSource.getLevelDataAndDimensions(save,worldLoader().dataConfiguration(),WorldMagic.vanillaServer().registryAccess().registryOrThrow(Registries.LEVEL_STEM),worldLoader().datapackWorldgen());

            dimensionRegistry=saveData.dimensions().dimensions();
            levelData=(PrimaryLevelData)saveData.worldData();
            specialProperty=saveData.dimensions().specialWorldProperty();

            if(gamerules!=null&&gamerules.override)levelData.getGameRules().assignFrom(gamerules.gameRules,null);
        }else{
            WorldDimensions worldDimensions=generator.create().create(worldLoader().datapackWorldgen());

            WritableRegistry<LevelStem> writableDimensionRegistry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.experimental());
            worldDimensions.dimensions().forEach((key,dimension)->writableDimensionRegistry.register(key,dimension,new RegistrationInfo(
                Optional.empty(),
                Util.isStable(key,dimension)?Lifecycle.stable():Lifecycle.experimental()
            )));

            dimensionRegistry=writableDimensionRegistry.freeze();

            specialProperty=specialWorldProperty(generator);
            levelData=getPrimaryLevelData(dimensionRegistry,specialProperty);
        }

        levelData.customDimensions=dimensionRegistry;
        levelData.checkName(bukkitId);
        levelData.setModdedInfo(vanillaServer().getServerModName(),vanillaServer().getModdedStatus().shouldReportAsModified());

        if(vanillaServer().options.has("forceUpgrade")){
            Main.forceUpgrade(levelStorage, DataFixers.getDataFixer(), vanillaServer().options.has("eraseCache"), () -> true, vanillaServer().registryAccess(), vanillaServer().options.has("recreateRegionFiles"));
        }

        int loadRadius=levelData.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).get();
        ChunkProgressListener loadListener=vanillaServer().progressListenerFactory.create(loadRadius);

        level=new ServerLevel(
            vanillaServer(),
            vanillaServer().executor,
            levelStorage,
            levelData,
            vanillaLevelId,
            stem,
            loadListener,
            specialProperty==PrimaryLevelData.SpecialWorldProperty.DEBUG,
            worldProperties.seed,
            List.of(new PhantomSpawner(),new PatrolSpawner(),new CatSpawner(),new VillageSiege(),new WanderingTraderSpawner(levelData)),
            true,
            vanillaServer().overworld().getRandomSequences(),
            Util.mapEnvironment(stem.type()),
            (generator instanceof GeneratorSettings.Plugin(String pl))? WorldCreator.getGeneratorForName(bukkitId,pl,Bukkit.getConsoleSender()):null,
            (pluginBiomeProvider==null)?null:WorldCreator.getBiomeProviderForName(bukkitId,pluginBiomeProvider,Bukkit.getConsoleSender())
        );
        level.noSave=!loading.save;

        vanillaServer().addLevel(level);

        bukkitWorld=level.getWorld();
        if(level.generator!=null)bukkitWorld.getPopulators().addAll(level.generator.getDefaultPopulators(bukkitWorld));

        if(!loading.async){
            WorldBorder worldBorder=level.getWorldBorder();
            worldBorder.applySettings(levelData.getWorldBorder());

            pluginManager().callEvent(new WorldInitEvent(bukkitWorld));
        }

        if(spawnPosition!=null&&spawnPosition.override)setSpawn(level,levelData);

        if(!levelData.isInitialized()){
            if(spawnPosition!=null){
                if(!spawnPosition.override)setSpawn(level,levelData);

                if (worldProperties.bonusChest){
                    level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((registry)->registry.getHolder(MiscOverworldFeatures.BONUS_CHEST))
                        .ifPresent((holder)->((ConfiguredFeature<?, ?>)holder.value())
                            .place(level, level.chunkSource.getGenerator(), level.random, levelData.getSpawnPos())
                        );
                }
            }else{
                try{
                    vanillaSetSpawn.invoke(null,level,levelData,worldProperties.bonusChest,level.isDebug());
                } catch (IllegalAccessException|InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }

            levelData.setInitialized(true);
        }

        level.setSpawnSettings(allowSettings.monsters,allowSettings.animals);

        BlockPos spawnPos=level.getSharedSpawnPos();

        loadListener.updateSpawnPos(new ChunkPos(spawnPos));
        level.setDefaultSpawnPos(spawnPos,level.getSharedSpawnAngle());

        logger().info("World {} loading done!",id.asMinimalString());
        if(!loading.async)postLoadProcess(loadRadius);
        else scheduler().runTask(instance(),()->{
            pluginManager().callEvent(new WorldInitEvent(bukkitWorld));
            postLoadProcess(loadRadius);
        });
    }

    private void postLoadProcess(int loadRadius){
        ServerChunkCache chunkCache=level.getChunkSource();

        if(loading.loadChunks){
            if(loadRadius>0){
                int load=Mth.square(ChunkProgressListener.calculateDiameter(loadRadius));
                while(chunkCache.getTickingGenerated()<load){
                    try{chunkCache.pollTask();}
                    catch(Throwable ignore){}
                }
            }
            logger().info("{} chunks loaded.",chunkCache.getTickingGenerated());
        }

        ForcedChunksSavedData forcedChunks=level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if(forcedChunks!=null){
            LongIterator chunksIterator = forcedChunks.getChunks().iterator();

            while(chunksIterator.hasNext())chunkCache.updateChunkForced(new ChunkPos(chunksIterator.nextLong()),true);
        }

        pluginManager().callEvent(new WorldLoadEvent(bukkitWorld));
    }

    private void unloadProcess(){
        if(!loading.save){
            DimensionDataStorage dataSt=level.getDataStorage();
            try{
                ((ExecutorService)ioExecutorField.get(dataSt)).shutdown();
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }
            dataSt.cache.forEach((id,dat)->{
                if(dat!=null)dat.setDirty(false);
            });
        }
        Bukkit.unloadWorld(bukkitWorld,loading.save);

        if(loading.folderDeletion)threadManager.execute(this::folderDeletion);
    }

    private void folderDeletion(){
        int i = 1;

        while (i <= 5) {
            try {
                Files.walkFileTree(folderPath, new SimpleFileVisitor<>() {
                    @NotNull
                    public FileVisitResult visitFile(Path path, @NotNull BasicFileAttributes basicfileattributes) throws IOException {
                        Files.delete(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @NotNull
                    public FileVisitResult postVisitDirectory(Path path, @Nullable IOException ioexception) throws IOException {
                        if (ioexception != null) {
                            throw ioexception;
                        } else {
                            Files.deleteIfExists(path);
                            return FileVisitResult.CONTINUE;
                        }
                    }
                });
                break;
            } catch (IOException ioexception) {
                if (i == 5) break;
                try {
                    Thread.sleep(500L);
                }catch(InterruptedException ignore){}
                ++i;
            }
        }
    }

    private void setSpawn(ServerLevel level,PrimaryLevelData levelData){
        BlockPos spawnPos=new BlockPos(spawnPosition.x,spawnPosition.y,spawnPosition.z);
        levelData.setSpawn(spawnPos,spawnPosition.yaw);
    }

    @SuppressWarnings("deprecation")
    private @NotNull PrimaryLevelData getPrimaryLevelData(Registry<LevelStem> dimensionRegistry, PrimaryLevelData.SpecialWorldProperty specialProperty) {
        WorldDimensions.Complete dimensionsOp=new WorldDimensions.Complete(dimensionRegistry,specialProperty);

        WorldOptions worldOptions=new WorldOptions(worldProperties.seed,worldProperties.generateStructures,worldProperties.bonusChest);
        LevelSettings levelSettings=new LevelSettings(bukkitId,worldProperties.defaultGamemode,worldProperties.hardcore,worldProperties.difficulty,false,gamerules==null?new GameRules():gamerules.gameRules,worldLoader().dataConfiguration());

        return new PrimaryLevelData(levelSettings,worldOptions,dimensionsOp.specialWorldProperty(),Lifecycle.experimental());
    }

    @SuppressWarnings("deprecation")
    private static PrimaryLevelData.SpecialWorldProperty specialWorldProperty(GeneratorSettings generator) {
        if(generator instanceof GeneratorSettings.Plugin)return PrimaryLevelData.SpecialWorldProperty.FLAT;

        if(generator instanceof GeneratorSettings.Vanilla(ChunkGenerator generatorVan)){
            if(generatorVan instanceof DebugLevelSource)return PrimaryLevelData.SpecialWorldProperty.DEBUG;
            else if(generatorVan instanceof FlatLevelSource)return PrimaryLevelData.SpecialWorldProperty.FLAT;
            else return PrimaryLevelData.SpecialWorldProperty.NONE;
        }

        throw new RuntimeException();
    }

    private boolean checkDuplication(){
        return Bukkit.getWorld(bukkitId)!=null || Bukkit.getWorld(id)!=null;
    }
}
