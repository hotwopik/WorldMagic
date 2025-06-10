package io.hotwop.worldmagic;

import io.hotwop.worldmagic.api.MagicWorld;
import io.hotwop.worldmagic.api.settings.CustomWorldSettings;
import io.hotwop.worldmagic.file.WorldFile;
import io.hotwop.worldmagic.integration.VaultIntegration;
import io.hotwop.worldmagic.integration.papi.Placeholders;
import io.hotwop.worldmagic.util.serializer.ComponentSerializer;
import io.hotwop.worldmagic.util.serializer.NamespacedKeySerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class WorldMagic extends JavaPlugin {
    private static ComponentLogger logger;
    public static ComponentLogger logger(){
        return logger;
    }

    private static WorldMagic instance;

    private static Path worldsPath;
    private static Path configPath;
    private static Path dimensionTypesPath;
    private static Path worldGenPath;

    public static Path dimensionTypesPath(){
        return dimensionTypesPath;
    }
    public static Path worldGenPath(){
        return worldGenPath;
    }

    private static WorldLoader.DataLoadContext worldLoader;
    private static DedicatedServer vanillaServer;
    private static PluginManager pluginManager;
    private static BukkitScheduler scheduler;

    private static Config config;
    private static boolean vaultEnabled=false;

    private static boolean loaded=false;

    public static WorldLoader.DataLoadContext worldLoader(){
        return worldLoader;
    }
    public static DedicatedServer vanillaServer(){
        return vanillaServer;
    }
    public static PluginManager pluginManager(){
        return pluginManager;
    }
    public static BukkitScheduler scheduler(){
        return scheduler;
    }
    public static WorldMagic instance(){
        return instance;
    }
    public static boolean loaded(){
        return loaded;
    }

    private static final Map<NamespacedKey, WorldFile> worldFiles=new HashMap<>();
    public static @Nullable WorldFile getWorldFile(NamespacedKey id){
        Objects.requireNonNull(id,"id");

        return worldFiles.get(id);
    }
    public static Set<NamespacedKey> getWorldFileIds(){
        return Set.copyOf(worldFiles.keySet());
    }

    private static final List<CustomWorld> worlds=new ArrayList<>();
    public static @Nullable CustomWorld getPluginWorld(NamespacedKey id){
        Objects.requireNonNull(id,"id");

        return worlds.stream().filter(wr->wr.id.equals(id)).findAny().orElse(null);
    }
    public static List<CustomWorld> getPluginWorlds(){
        return List.copyOf(worlds);
    }

    public static @Nullable CustomWorld isPluginWorld(World world){
        Objects.requireNonNull(world,"world");

        NamespacedKey id=world.getKey();
        return worlds.stream().filter(wr->wr.loaded()&&wr.id.equals(id)).findAny().orElse(null);
    }

    private static final List<CustomWorld> startups=new ArrayList<>();

    @Override
    public void onLoad(){
        instance=this;
        logger=getComponentLogger();

        vanillaServer=MinecraftServer.getServer().server.getServer();
        worldLoader=vanillaServer.worldLoader;

        Server server=getServer();
        pluginManager=server.getPluginManager();
        scheduler=server.getScheduler();

        File dataFolder=getDataFolder();
        dataFolder.mkdirs();
        Path dataFolderPath=dataFolder.toPath();

        configPath=dataFolderPath.resolve("config.yml");

        worldsPath=dataFolderPath.resolve("worlds");
        worldsPath.toFile().mkdir();

        dimensionTypesPath=dataFolderPath.resolve("dimension-types");
        dimensionTypesPath.toFile().mkdir();

        worldGenPath=dataFolderPath.resolve("worldgen");
        worldGenPath.toFile().mkdir();

        try{
            loadConfig();
        }catch(RuntimeException e){
            logger.error(e.toString());
            pluginManager.disablePlugin(this);
            return;
        }

        WorldGenProcessor.loadWorldGen();
        WorldGenProcessor.loadDimensionTypes();
        loadWorldFiles();

        logger.info("Building worlds...");
        worldFiles.forEach((id,file)->{
            if(file.prototype)return;

            CustomWorld cw;
            try{
                cw=new CustomWorld(file);
            }catch(RuntimeException e){
                logger().info("Error to build world {}: {}",file.id.asString(),e.getMessage());
                return;
            }

            worlds.add(cw);
            if(file.loading.startup)startups.add(cw);
        });
    }

    @Override
    public void onEnable(){
        if(pluginManager.isPluginEnabled("PlaceholderAPI"))new Placeholders().register();
        if(pluginManager.isPluginEnabled("Vault"))loadVault();

        logger.info("Loading worlds...");
        startups.forEach(wr->{
            try{
                wr.load();
            }catch(RuntimeException e){
                logger().error("Error to load world {}: {}",wr.id.asString(),e.getMessage());
            }
        });
        startups.clear();

        loaded=true;

        pluginManager.registerEvents(new EventListener(),this);
    }

    public void loadWorldFiles(){
        logger.info("Loading world files...");
        Map<Path,YamlConfigurationLoader> loaders=new HashMap<>();

        try(Stream<Path> stream=Files.walk(worldsPath)){
            stream.filter(pt->Files.isRegularFile(pt)&&pt.toFile().getName().endsWith(".yml")).forEach(pt->loaders.computeIfAbsent(pt, WorldFile::createLoader));
        }catch(IOException e){
            logger.error("Error to load world files:\n {}",e.toString());
            return;
        }

        worldFiles.clear();

        Map<WorldFile,Path> patches=new HashMap<>();
        Map<NamespacedKey, List<Path>> conflicts=new HashMap<>();
        loaders.forEach((path,loader)->{
            try{
                CommentedConfigurationNode node=loader.load();
                if(node.isNull()){
                    logger.warn("File {} is empty, error to load world file", path);
                    return;
                }
                WorldFile file=node.require(WorldFile.class);

                if(conflicts.containsKey(file.id)){
                    conflicts.get(file.id).add(path);
                    return;
                }
                if(worldFiles.containsKey(file.id)){
                    WorldFile conflict=worldFiles.get(file.id);
                    Path conflictPath=patches.get(conflict);

                    worldFiles.remove(file.id);
                    patches.remove(conflict);

                    List<Path> conflictLs=new ArrayList<>();
                    conflictLs.add(path);
                    conflictLs.add(conflictPath);
                    conflicts.put(file.id,conflictLs);

                    return;
                }

                worldFiles.put(file.id,file);
                patches.put(file,path);
            }catch(ConfigurateException ex){
                logger.warn("Error to load world file {}:\n  {}",path.toString(),ex.toString());
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

            logger.warn("Error to load world files due ID duplication: {}",builder);
        });
        logger.info("World files loaded");
    }

    public void loadVault(){
        VaultIntegration.loadEconomy();
        vaultEnabled=VaultIntegration.economy()!=null;
    }

    public void loadConfig(){
        logger.info("Loading config...");

        YamlConfigurationLoader loader=createConfigLoader(configPath);
        File configFile=configPath.toFile();

        if(configFile.isDirectory())throw new RuntimeException("Config file is directory");

        if(configFile.exists()){
            try{
                CommentedConfigurationNode node=loader.load();
                config=node.get(Config.class);
            }catch(ConfigurateException e){
                throw new RuntimeException("Config loading error "+e);
            }
        }else{
            config=new Config();
            CommentedConfigurationNode node=loader.createNode();

            try{
                node.set(config);
                loader.save(node);
            }catch(ConfigurateException e){
                throw new RuntimeException("Config default file creation error "+e);
            }
        }

        logger.info("Config loaded");
    }

    @Override
    public void onDisable() {
        worlds.forEach(wr->{
            if(wr.loaded())wr.unload();
        });

        CustomWorld.shutdownAsync();
    }

    public static void createWorldFromFile(NamespacedKey id, WorldFile file) throws WorldCreationException{
        createWorldFromFile(id,null,null,file);
    }

    public static void createWorldFromFile(NamespacedKey id, @Nullable String bukkitId, WorldFile file) throws WorldCreationException{
        createWorldFromFile(id,bukkitId,null,file);
    }

    public static void createWorldFromFile(NamespacedKey id, @Nullable String bukkitId, @Nullable String folder, WorldFile file) throws WorldCreationException{
        if(!loaded)throw new RuntimeException("External loads not accepted in plugin load phase");

        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(file,"file");

        worldDataCheck(id,bukkitId,folder);

        CustomWorld world;
        try{
            world=new CustomWorld(id,bukkitId,folder,file);
        }catch(RuntimeException e){
            throw new WorldCreationException(e.toString(),WorldCreationException.Phase.build);
        }

        try{
            world.load();
        }catch(RuntimeException e){
            throw new WorldCreationException(e.toString(),WorldCreationException.Phase.load);
        }

        worlds.add(world);
    }

    public static MagicWorld createWorldFromSettings(CustomWorldSettings settings) throws WorldCreationException{
        if(!loaded)throw new RuntimeException("External loads not accepted in plugin load phase");
        Objects.requireNonNull(settings,"settings");

        worldDataCheck(settings.id,settings.bukkitId,settings.folder);

        CustomWorld world;
        try{
            world=new CustomWorld(settings);
        }catch(RuntimeException e){
            throw new WorldCreationException(e.toString(),WorldCreationException.Phase.build);
        }

        try{
            world.load();
        }catch(RuntimeException e){
            throw new WorldCreationException(e.toString(),WorldCreationException.Phase.load);
        }

        worlds.add(world);
        return world;
    }

    private static void worldDataCheck(NamespacedKey id,@Nullable String bukkitId,@Nullable String folder) throws WorldCreationException{
        if(Bukkit.getWorld(id)!=null)throw new WorldCreationException("world with vanilla id "+id.asString()+" already exist",WorldCreationException.Phase.check);
        if(bukkitId!=null&&Bukkit.getWorld(bukkitId)!=null)throw new WorldCreationException("world with bukkit id "+bukkitId+" already exist",WorldCreationException.Phase.check);

        if(worlds.stream()
            .anyMatch(cw->cw.id.equals(id)))throw new WorldCreationException("already exist unloaded world with vanilla id "+id.asString(),WorldCreationException.Phase.check);
        if(worlds.stream()
            .anyMatch(cw->cw.bukkitId.equals(bukkitId)))throw new WorldCreationException("already exist unloaded world with bukkit id "+bukkitId,WorldCreationException.Phase.check);
        if(worlds.stream()
            .anyMatch(cw->cw.folder.equals(folder)))throw new WorldCreationException("already exist unloaded world with folder path "+folder,WorldCreationException.Phase.check);

        if(folder!=null){
            Path folderPath;
            try{
                folderPath=Bukkit.getWorldContainer().toPath().resolve(folder);
            }catch(InvalidPathException e){
                throw new WorldCreationException("invalid path "+e,WorldCreationException.Phase.check);
            }

            if(folderPath.toFile().isFile())throw new WorldCreationException("folder "+folder+" already exist as file",WorldCreationException.Phase.check);
        }
    }

    public static void deleteWorld(NamespacedKey id) throws WorldDeletionException{
        Objects.requireNonNull(id,"id");

        CustomWorld world=worlds.stream()
            .filter(cw->cw.id.equals(id)).findAny().orElse(null);
        if(world==null)throw new WorldDeletionException("Unknown world: "+id.asString());

        logger.info("Deleting world {}",id.asString());
        world.forDeletion();

        if(world.loaded())world.unload();
        else worlds.remove(world);
    }

    @ConfigSerializable
    public static final class Config{
        public NamespacedKey spawnWorld=null;
        public Component noPermissionMessage=Component.text("You haven't permissions to get in this world",NamedTextColor.RED);
        public Component haventToPayMessage=Component.text("You haven't to pay for entrance in this world",NamedTextColor.RED);
        public String worldWithdrawMessage="<yellow><cost><green> payed for world entrance.";
    }

    public static YamlConfigurationLoader createConfigLoader(Path path){
        return YamlConfigurationLoader.builder()
            .path(path)
            .indent(2)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions(opts->opts
                .serializers(ser->ser
                    .register(NamespacedKeySerializer.instance)
                    .register(ComponentSerializer.instance)
                )
            )
            .build();
    }

    public static final class EventListener implements Listener {
        private EventListener(){}

        @EventHandler(priority=EventPriority.HIGHEST)
        public void worldUnload(WorldUnloadEvent e){
            World world=e.getWorld();

            worlds.stream()
                .filter(cw->cw.isForDeletion()&&cw.id.equals(world.getKey())).findAny()
                .ifPresent(worlds::remove);

            worlds.stream()
                .filter(cw->cw.loaded()&&cw.id.equals(world.getKey())).findAny()
                .ifPresent(cw->{
                    logger.info("Redirecting {} unload to WorldMagic...",world.getKey().asString());
                    e.setCancelled(true);

                    cw.unload();
                });
        }

        @EventHandler
        public void worldChange(PlayerChangedWorldEvent e){
            Player pl=e.getPlayer();
            World world=pl.getWorld();

            CustomWorld cw=isPluginWorld(world);
            if(cw!=null){
                if(
                    cw.worldProperties.requiredPermission()!=null&&
                    !pl.hasPermission("worldmagic.bypass.permissions")&&
                    !pl.hasPermission(cw.worldProperties.requiredPermission())
                ){
                    pl.teleport(cw.callbackLocation());
                    if(config.noPermissionMessage!=null)pl.sendMessage(config.noPermissionMessage);
                    return;
                }

                if(
                    vaultEnabled&&
                    cw.worldProperties.enterPayment()!=null&&
                    cw.worldProperties.enterPayment()>0&&
                    !pl.hasPermission("worldmagic.bypass.payment")
                ){
                    if(VaultIntegration.economy().has(pl,cw.worldProperties.enterPayment())){
                        VaultIntegration.economy().withdrawPlayer(pl,cw.worldProperties.enterPayment());

                        if(config.worldWithdrawMessage!=null){
                            MiniMessage compiler=MiniMessage.builder()
                                .editTags(tags->tags.tag("cost",Tag.inserting(Component.text(cw.worldProperties.enterPayment()))))
                                .build();

                            pl.sendMessage(compiler.deserialize(config.worldWithdrawMessage));
                        }
                    }else{
                        pl.teleport(cw.callbackLocation());
                        if(config.haventToPayMessage!=null)pl.sendMessage(config.haventToPayMessage);
                        return;
                    }
                }

                if(
                    cw.worldProperties.forceGamemode()!=null&&
                    !pl.hasPermission("worldmagic.bypass.forcegm")
                ){
                    pl.setGameMode(cw.worldProperties.forceGamemode());
                }
            }
        }

        @EventHandler
        public void playerSpawn(PlayerSpawnLocationEvent e){
            Player pl=e.getPlayer();

            if(!pl.hasPlayedBefore()&&config.spawnWorld!=null){
                World world=Bukkit.getWorld(config.spawnWorld);
                if(world==null){
                    logger.error("Error to setup player spawn, unknown world: {}", config.spawnWorld.asString());
                    return;
                }

                e.setSpawnLocation(world.getSpawnLocation());
            }
        }
    }
}
