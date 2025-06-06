package io.hotwop.worldmagic;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import io.hotwop.worldmagic.api.*;
import io.hotwop.worldmagic.api.settings.*;
import io.hotwop.worldmagic.file.WorldFile;
import io.hotwop.worldmagic.generation.Dimension;
import io.hotwop.worldmagic.generation.GameRuleFactory;
import io.hotwop.worldmagic.generation.GeneratorSettings;
import io.hotwop.worldmagic.file.FileUtil;
import io.hotwop.worldmagic.util.ImmutableLocation;
import io.hotwop.worldmagic.util.VersionUtil;
import io.papermc.paper.FeatureHooks;
import net.minecraft.core.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
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
import java.util.function.BooleanSupplier;

import static io.hotwop.worldmagic.WorldMagic.*;

public final class CustomWorld implements MagicWorld {
    private static final ExecutorService threadManager=Executors.newCachedThreadPool();
    private static final Method vanillaSetSpawn;
    private static final Field ioExecutorField;

    private static final NamespacedKey overworld=NamespacedKey.minecraft("overworld");

    static{
        try {
            vanillaSetSpawn=MinecraftServer.class.getDeclaredMethod("setInitialSpawn", ServerLevel.class, ServerLevelData.class, boolean.class, boolean.class);

            if(VersionUtil.dataVersion<4082){
                ioExecutorField=DimensionDataStorage.class.getDeclaredField("ioExecutor");
                ioExecutorField.setAccessible(true);
            }else ioExecutorField=null;

        } catch (NoSuchMethodException|NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        vanillaSetSpawn.setAccessible(true);
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
    public final LocationResolver callbackLocation;
    public final AllowSettings allowSettings;
    public final GameRuleFactory gamerules;
    public final WorldBorderSettings border;

    public final Path folderPath;
    public final ResourceLocation vanillaLocId;
    public final ResourceKey<LevelStem> vanillaId;
    public final ResourceKey<Level> vanillaLevelId;
    public final ResourceKey<LevelStem> dimensionId;
    public final LevelStem stem;

    private boolean loaded=false;

    public NamespacedKey id(){
        return id;
    }
    public String bukkitId(){
        return bukkitId;
    }
    public String folder(){
        return folder;
    }
    public Path folderPath(){
        return folderPath;
    }
    public DimensionLike dimension(){
        return dimension;
    }
    public WorldProperties worldProperties(){
        return worldProperties;
    }
    public AllowSettings allowSettings(){
        return allowSettings;
    }

    public CustomWorldSettings createSettings(@NotNull NamespacedKey id){
        Objects.requireNonNull(id,"id");
        return createSettings(id,id.namespace().equals(NamespacedKey.MINECRAFT)?id.value():id.namespace()+"_"+id.value());
    }

    public CustomWorldSettings createSettings(@NotNull NamespacedKey id, @NotNull String bukkitId){
        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(bukkitId,"bukkitId");
        return createSettings(id,bukkitId,bukkitId);
    }

    public CustomWorldSettings createSettings(@NotNull NamespacedKey id, @NotNull String bukkitId, @NotNull String folder){
        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(bukkitId,"bukkitId");
        Objects.requireNonNull(folder,"folder");

        CustomWorldSettings out=new CustomWorldSettings(id,bukkitId,folder);

        out.setWorldProperties(worldProperties);
        out.setDimension(dimension);
        out.setLoadingSettings(loading);
        out.setAllowSettings(allowSettings);
        out.setWorldBorderSettings(border);

        if(spawnPosition!=null)out.setSpawn(spawnPosition);
        if(callbackLocation!=null)out.setCallbackLocation(callbackLocation);

        FileUtil.fromFactory(out.gameRuleSet(),gamerules);
        out.setGameRuleOverride(gamerules.override);

        return out;
    }

    public boolean loaded(){
        return loaded;
    }

    public World world(){
        if(!loaded)return null;
        return bukkitWorld;
    }
    public ServerLevel level(){
        if(!loaded)return null;
        return level;
    }

    public Location callbackLocation(){
        if(callbackLocation==null)return Bukkit.getWorld(overworld).getSpawnLocation();
        try{
            return callbackLocation.resolve();
        }catch(LocationResolver.ResolveException e){
            return Bukkit.getWorld(overworld).getSpawnLocation();
        }
    }

    public boolean isForDeletion(){
        return forDeletion;
    }

    private boolean forDeletion=false;

    private World bukkitWorld=null;
    private ServerLevel level=null;

    protected CustomWorld(WorldFile file){
        this(file.id,file.bukkitId,file.folder,file);
    }

    protected CustomWorld(NamespacedKey id, String bukkitId, String folder, WorldFile file){
        this.id=id;

        vanillaLocId= VersionUtil.createResourceLocation(id);
        vanillaId=ResourceKey.create(Registries.LEVEL_STEM,vanillaLocId);
        vanillaLevelId=ResourceKey.create(Registries.DIMENSION,vanillaLocId);

        this.bukkitId=bukkitId==null?(id.namespace().equals(NamespacedKey.MINECRAFT)?id.value():id.namespace()+"_"+id.value()):bukkitId;

        this.folder=folder==null?this.bukkitId:folder;
        folderPath=Bukkit.getWorldContainer().toPath().resolve(this.folder);

        worldProperties= FileUtil.fromFile(file.worldProperties);
        dimension=file.dimension;
        loading= FileUtil.fromFile(file.loading);
        spawnPosition=file.spawnPosition==null?null:FileUtil.fromFile(file.spawnPosition);
        callbackLocation=file.callbackLocation;
        allowSettings= FileUtil.fromFile(file.allowSettings);
        gamerules=file.gamerules;
        border=FileUtil.fromFile(file.border);

        if(dimension instanceof Dimension.Reference ref)dimensionId=ref.getKey();
        else dimensionId=vanillaId;

        stem=dimension.get();
    }

    protected CustomWorld(CustomWorldSettings settings){
        id=settings.id;
        bukkitId=settings.bukkitId;
        folder=settings.folder;

        vanillaLocId= VersionUtil.createResourceLocation(id);
        vanillaId=ResourceKey.create(Registries.LEVEL_STEM,vanillaLocId);
        vanillaLevelId=ResourceKey.create(Registries.DIMENSION,vanillaLocId);

        folderPath=Bukkit.getWorldContainer().toPath().resolve(this.folder);

        worldProperties=settings.worldProperties()==null?new WorldProperties(
            true,
            null,
            true,
            false,
            null,
            Difficulty.NORMAL,
            null,
            null
        ):settings.worldProperties();

        if(!(settings.dimension() instanceof Dimension dim))throw new RuntimeException("Don't try to spoof DimensionLike!");
        dimension=dim;

        loading=settings.loadingSettings()==null?new Loading(
            false,
            true,
            true,
            false,
            true
        ):settings.loadingSettings();

        spawnPosition=settings.spawn();
        callbackLocation=settings.callbackLocation();
        allowSettings=settings.allowSettings()==null?new AllowSettings(
            true,
            true,
            true
        ):settings.allowSettings();

        gamerules=FileUtil.toFactory(settings.gameRuleSet(),settings.isGameRuleOverride());
        border=settings.worldBorderSettings()==null?new WorldBorderSettings(
            false,
            29999984,
            5,
            0.2,
            0,
            0,
            5,
            300
        ):settings.worldBorderSettings();

        if(dimension instanceof Dimension.Reference ref)dimensionId=ref.getKey();
        else dimensionId=vanillaId;

        stem=dimension.get();
    }

    public void load(){
        if(shutdown)return;

        if(loaded)throw new WorldAlreadyLoadedException();
        if(forDeletion)throw new RuntimeException("World for deletion!");

        loaded=true;

        if(loading.async())threadManager.execute(this::loadProcess);
        else{
            if(vanillaServer().isSameThread())loadProcess();
            else scheduler().runTask(instance(),this::loadProcess);
        }
    }

    public void unload(){
        if(shutdown)return;
        if(!loaded)throw new WorldAlreadyUnloadedException();
        loaded=false;

        List<ServerPlayer> players=List.copyOf(level.players());

        if((loading.save()&&Bukkit.isStopping())||players.isEmpty()){
            if(vanillaServer().isSameThread())unloadProcess();
            else scheduler().runTask(instance(),this::unloadProcess);
        }else{
            AtomicInteger task=new AtomicInteger(players.size());

            Location loc=callbackLocation();

            players.forEach(pl->pl.getBukkitEntity().teleportAsync(loc).thenAccept(done->{
                if(!done)pl.getBukkitEntity().teleport(loc);

                if(task.addAndGet(-1)==0)unloadProcess();
            }));
        }
    }

    protected void forDeletion(){
        forDeletion=true;
    }

    @SuppressWarnings("deprecation")
    private void loadProcess(){
        if(checkDuplication()){
            logger().error("Error to load custom world {}, world with that vanilla id or bukkit id already loaded",id.asString());
            loaded=false;
            return;
        }

        if(folderPath.toFile().isFile()){
            logger().error("Error to load custom world {}, world \"folder\" is file",id.asString());
            loaded=false;
            return;
        }

        logger().info("Creating world {}",id.asString());

        LevelStorageSource.LevelStorageAccess levelStorage;

        Path container=folderPath.getParent();
        String folderName=container.relativize(folderPath).toString();

        try{
            levelStorage=LevelStorageSource.createDefault(container).validateAndCreateAccess(folderName,dimensionId);
        } catch (ContentValidationException | IOException e) {
            logger().error("Error to load custom world {}, folder creation error: {}",id.asString(),e.toString());
            loaded=false;
            return;
        }

        Dynamic<?> save;
        if (levelStorage.hasWorldData()) {
            LevelSummary worldinfo;

            try {
                save = levelStorage.getDataTag();
                worldinfo = levelStorage.getSummary(save);
            } catch (NbtException | ReportedNbtException | IOException ioexception) {
                LevelStorageSource.LevelDirectory directory = levelStorage.getLevelDirectory();

                logger().warn("Failed to load world {} data from {}", id.asString(), directory.dataFile(), ioexception);
                logger().info("Attempting to use fallback");

                try {
                    save = levelStorage.getDataTagFallback();
                    worldinfo = levelStorage.getSummary(save);
                } catch (NbtException | ReportedNbtException | IOException ioexception1) {
                    logger().error("Failed to load world {} data from {}", id.asString(), directory.oldDataFile(), ioexception1);
                    loaded=false;
                    return;
                }

                levelStorage.restoreLevelDataFromOld();
            }

            if (worldinfo.requiresManualConversion()) {
                logger().warn("World {} must be opened in an older version (like 1.6.4) to be safely converted",id.asString());
                loaded=false;
                return;
            }

            if (!worldinfo.isCompatible()) {
                logger().info("World {} was created by an incompatible version.",id.asString());
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

        if(save==null||worldProperties.override()){
            WorldDimensions worldDimensions= VersionUtil.createWorldDimensions(generator.create());

            WritableRegistry<LevelStem> writableDimensionRegistry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.experimental());
            worldDimensions.dimensions().forEach((key,dimension)->writableDimensionRegistry.register(key,dimension,new RegistrationInfo(
                Optional.empty(),
                isStable(key,dimension)?Lifecycle.stable():Lifecycle.experimental()
            )));

            dimensionRegistry=writableDimensionRegistry.freeze();

            specialProperty=specialWorldProperty(generator);
            levelData=getPrimaryLevelData(dimensionRegistry,specialProperty);

            if(save==null||border.override())levelData.setWorldBorder(FileUtil.buildWorldBorder(border));

            if(save!=null){
                LevelDataAndDimensions saveData=VersionUtil.getLevelDataAndDimension(save,worldLoader().dataConfiguration(), VersionUtil.getRegistry(Registries.LEVEL_STEM));
                PrimaryLevelData oldLevelData=(PrimaryLevelData)saveData.worldData();

                levelData.setDayTime(oldLevelData.getDayTime());

                levelData.setClearWeatherTime(oldLevelData.getClearWeatherTime());
                levelData.setRainTime(oldLevelData.getRainTime());
                levelData.setThunderTime(oldLevelData.getThunderTime());
                levelData.setGameTime(oldLevelData.getGameTime());

                levelData.setRaining(oldLevelData.isRaining());
                levelData.setThundering(oldLevelData.isThundering());
                levelData.setInitialized(oldLevelData.isInitialized());

                if(gamerules==null||!gamerules.override)levelData.getGameRules().assignFrom(oldLevelData.getGameRules(),null);
                if(!border.override())levelData.setWorldBorder(oldLevelData.getWorldBorder());
            }
        }else{
            LevelDataAndDimensions saveData=VersionUtil.getLevelDataAndDimension(save,worldLoader().dataConfiguration(), VersionUtil.getRegistry(Registries.LEVEL_STEM));

            dimensionRegistry=saveData.dimensions().dimensions();
            levelData=(PrimaryLevelData)saveData.worldData();
            specialProperty=saveData.dimensions().specialWorldProperty();

            if(gamerules!=null&&gamerules.override)levelData.getGameRules().assignFrom(gamerules.gameRules,null);
            if(border.override())levelData.setWorldBorder(FileUtil.buildWorldBorder(border));
        }

        levelData.customDimensions=dimensionRegistry;
        levelData.checkName(bukkitId);
        levelData.setModdedInfo(vanillaServer().getServerModName(),vanillaServer().getModdedStatus().shouldReportAsModified());

        if(vanillaServer().options.has("forceUpgrade")){
            VersionUtil.forceUpgrade.invoke(levelStorage,levelData,DataFixers.getDataFixer(), vanillaServer().options.has("eraseCache"), (BooleanSupplier)()->true, vanillaServer().registryAccess(), vanillaServer().options.has("recreateRegionFiles"));
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
            BiomeManager.obfuscateSeed(worldProperties.override()&&worldProperties.seed()!=null?worldProperties.seed():levelData.worldGenOptions().seed()),
            List.of(new PhantomSpawner(),new PatrolSpawner(),new CatSpawner(),new VillageSiege(),new WanderingTraderSpawner(levelData)),
            true,
            vanillaServer().overworld().getRandomSequences(),
            World.Environment.CUSTOM,
            (generator instanceof GeneratorSettings.Plugin(String pl))? WorldCreator.getGeneratorForName(bukkitId,pl,Bukkit.getConsoleSender()):null,
            (pluginBiomeProvider==null)?null:WorldCreator.getBiomeProviderForName(bukkitId,pluginBiomeProvider,Bukkit.getConsoleSender())
        );
        level.noSave=!loading.save();
        level.pvpMode=allowSettings.pvp();

        vanillaServer().addLevel(level);

        bukkitWorld=level.getWorld();
        boolean init;

        try{
            if(level.generator!=null)bukkitWorld.getPopulators().addAll(level.generator.getDefaultPopulators(bukkitWorld));

            if(!loading.async()){
                WorldBorder worldBorder=level.getWorldBorder();
                worldBorder.applySettings(levelData.getWorldBorder());

                pluginManager().callEvent(new WorldInitEvent(bukkitWorld));
            }

            if(spawnPosition!=null&&spawnPosition.override())setSpawn(levelData);

            if(!levelData.isInitialized()){
                init=true;

                if(spawnPosition!=null){
                    if(!spawnPosition.override())setSpawn(levelData);
                }else{
                    try{
                        vanillaSetSpawn.invoke(null,level,levelData,false,level.isDebug());
                    } catch (IllegalAccessException|InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }

                if(!loading.async()&&worldProperties.bonusChest()){
                    VersionUtil.getHolder(VersionUtil.getRegistry(level.registryAccess(),Registries.CONFIGURED_FEATURE),MiscOverworldFeatures.BONUS_CHEST)
                        .value().place(level, level.chunkSource.getGenerator(), level.random, levelData.getSpawnPos());
                }

                levelData.setInitialized(true);
            }else init=false;

            VersionUtil.setSpawnSettings(level,allowSettings.monsters(),allowSettings.animals());

            BlockPos spawnPos=level.getSharedSpawnPos();

            loadListener.updateSpawnPos(new ChunkPos(spawnPos));
            level.setDefaultSpawnPos(spawnPos,level.getSharedSpawnAngle());

            logger().info("World {} initialization done!",id.asString());
        }catch(RuntimeException e){
            loaded=false;
            Bukkit.unloadWorld(bukkitWorld,false);

            throw e;
        }

        if(!loading.async())postLoadProcess(loadRadius,loadListener);
        else scheduler().runTask(instance(),()->{
            try{
                WorldBorder worldBorder=level.getWorldBorder();
                worldBorder.applySettings(levelData.getWorldBorder());

                pluginManager().callEvent(new WorldInitEvent(bukkitWorld));

                if(init&&worldProperties.bonusChest()){
                    VersionUtil.getHolder(VersionUtil.getRegistry(level.registryAccess(),Registries.CONFIGURED_FEATURE),MiscOverworldFeatures.BONUS_CHEST)
                        .value().place(level, level.chunkSource.getGenerator(), level.random, levelData.getSpawnPos());
                }
            }catch(RuntimeException e){
                loaded=false;
                Bukkit.unloadWorld(bukkitWorld,false);

                throw e;
            }
            postLoadProcess(loadRadius,loadListener);
        });
    }

    private void postLoadProcess(int loadRadius,ChunkProgressListener loadListener){
        try{
            ServerChunkCache chunkCache=level.getChunkSource();

            if(loading.loadChunks()){
                vanillaServer().forceTicks=true;
                if(loadRadius>0){
                    int load=Mth.square(ChunkProgressListener.calculateDiameter(loadRadius));
                    while(chunkCache.getTickingGenerated()<load){
                        try{chunkCache.pollTask();}
                        catch(Throwable ignore){}
                    }
                }
                vanillaServer().forceTicks=false;
                logger().info("{} {} chunks loaded.",chunkCache.getTickingGenerated(),id.asString());
            }

            VersionUtil.computeForcedChunks(level.getDataStorage(),chunkCache);

            loadListener.stop();
            if(VersionUtil.dataVersion>4082)FeatureHooks.tickEntityManager(level);

            pluginManager().callEvent(new WorldLoadEvent(bukkitWorld));
        }catch(RuntimeException e){
            loaded=false;
            Bukkit.unloadWorld(bukkitWorld,false);

            throw e;
        }
    }

    private void unloadProcess(){
        if(!loading.save()){
            DimensionDataStorage dataSt=level.getDataStorage();

            if(VersionUtil.dataVersion<4082){
                try{
                    ((ExecutorService)ioExecutorField.get(dataSt)).shutdown();
                }catch(IllegalAccessException e){
                    throw new RuntimeException(e);
                }
            }

            VersionUtil.undirtData(dataSt);
        }
        Bukkit.unloadWorld(bukkitWorld,loading.save());

        if(loading.folderDeletion())threadManager.execute(this::folderDeletion);
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

    private void setSpawn(PrimaryLevelData levelData){
        BlockPos spawnPos=new BlockPos(spawnPosition.x(),spawnPosition.y(),spawnPosition.z());
        levelData.setSpawn(spawnPos,spawnPosition.yaw());
    }

    private boolean checkDuplication(){
        return Bukkit.getWorld(bukkitId)!=null || Bukkit.getWorld(id)!=null;
    }

    @SuppressWarnings("deprecation")
    private @NotNull PrimaryLevelData getPrimaryLevelData(Registry<LevelStem> dimensionRegistry, PrimaryLevelData.SpecialWorldProperty specialProperty) {
        WorldDimensions.Complete dimensionsOp=new WorldDimensions.Complete(dimensionRegistry,specialProperty);

        WorldOptions worldOptions=new WorldOptions(worldProperties.seed()==null?WorldOptions.randomSeed():worldProperties.seed(),worldProperties.generateStructures(),worldProperties.bonusChest());
        LevelSettings levelSettings=new LevelSettings(bukkitId,vanillaServer().getDefaultGameType(),Bukkit.isHardcore(), FileUtil.mapDifficulty(worldProperties.difficulty()),false,gamerules==null? VersionUtil.createGameRules():gamerules.gameRules,worldLoader().dataConfiguration());

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
}
