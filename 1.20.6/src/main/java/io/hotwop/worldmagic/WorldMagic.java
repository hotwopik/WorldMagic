package io.hotwop.worldmagic;

import io.hotwop.worldmagic.file.WorldFile;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
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

    private static final Map<NamespacedKey,WorldFile> worldFiles=new HashMap<>();
    public static @Nullable WorldFile getWorldFile(NamespacedKey id){
        return worldFiles.get(id);
    }
    public static Set<NamespacedKey> getWorldFileIds(){
        return Set.copyOf(worldFiles.keySet());
    }

    private static final List<CustomWorld> worlds=new ArrayList<>();
    public static @Nullable CustomWorld getPluginWorld(NamespacedKey id){
        return worlds.stream().filter(wr->wr.id.equals(id)).findAny().orElse(null);
    }
    public static List<CustomWorld> getPluginWorlds(){
        return List.copyOf(worlds);
    }

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

        worldsPath=dataFolderPath.resolve("worlds");
        worldsPath.toFile().mkdir();

        dimensionTypesPath=dataFolderPath.resolve("dimension-types");
        dimensionTypesPath.toFile().mkdir();

        worldGenPath=dataFolderPath.resolve("worldgen");
        worldGenPath.toFile().mkdir();

        WorldGenProcessor.loadWorldGen();
        WorldGenProcessor.loadDimensionTypes();
        loadWorldFiles();

        logger.info("Building worlds...");
        worldFiles.forEach((id,file)->{
            if(file.prototype)return;

            try{
                worlds.add(new CustomWorld(file));
            }catch(RuntimeException e){
                logger().info("Error to build world: {}",e.toString());
            }
        });
    }

    @Override
    public void onEnable(){
        logger.info("Loading worlds...");
        worlds.forEach(wr->{
            if(wr.loading.startup()){
                try{
                    wr.load();
                }catch(RuntimeException e){
                    World bukkit=wr.world();
                    if(bukkit!=null){
                        Bukkit.unloadWorld(bukkit,false);
                    }

                    logger().info("Error to load world: {}",e.toString());
                }
            }
        });

        loaded=true;

        pluginManager.registerEvents(new EventListener(),this);
    }

    public void loadWorldFiles(){
        logger.info("Loading world files...");
        Map<Path,YamlConfigurationLoader> loaders=new HashMap<>();

        try(Stream<Path> stream=Files.walk(worldsPath)){
            stream.filter(pt->Files.isRegularFile(pt)&&pt.toFile().getName().endsWith(".yml")).forEach(pt->loaders.computeIfAbsent(pt,WorldFile::createLoader));
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
                logger.warn("Error to load world file {}:\n  {}",path.toString(),ex.getMessage());
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

    @Override
    public void onDisable() {
        worlds.forEach(wr->{
            if(wr.loaded())wr.unload();
        });

        CustomWorld.shutdownAsync();
    }

    public static void createWorldFromFile(NamespacedKey id,WorldFile file) throws WorldCreationException{
        createWorldFromFile(id,null,null,file);
    }

    public static void createWorldFromFile(NamespacedKey id,@Nullable String bukkitId,WorldFile file) throws WorldCreationException{
        createWorldFromFile(id,bukkitId,null,file);
    }

    public static void createWorldFromFile(NamespacedKey id,@Nullable String bukkitId,@Nullable String folder,WorldFile file) throws WorldCreationException{
        if(!loaded)throw new RuntimeException("External loads not accepted in plugin load phase");

        if(Bukkit.getWorld(id)!=null)throw new WorldCreationException("check phase error: World with vanilla id "+id.asString()+" already exist");
        if(bukkitId!=null&&Bukkit.getWorld(bukkitId)!=null)throw new WorldCreationException("check phase error: World with bukkit id "+bukkitId+" already exist");

        if(worlds.stream()
            .anyMatch(cw->cw.id.equals(id)))throw new WorldCreationException("check phase error: already exist unloaded world with vanilla id "+id.asString());
        if(worlds.stream()
            .anyMatch(cw->cw.bukkitId.equals(bukkitId)))throw new WorldCreationException("check phase error: already exist unloaded world with bukkit id "+bukkitId);
        if(worlds.stream()
            .anyMatch(cw->cw.folder.equals(folder)))throw new WorldCreationException("check phase error: already exist unloaded world with folder "+folder);

        if(folder!=null){
            Path folderPath;
            try{
                folderPath=Bukkit.getWorldContainer().toPath().resolve(folder);
            }catch(InvalidPathException e){
                throw new WorldCreationException("check phase error: invalid path "+e.getMessage());
            }

            if(folderPath.toFile().isFile())throw new WorldCreationException("check phase error: Folder "+folder+" already exist as file");
        }

        CustomWorld world;
        try{
            world=new CustomWorld(id,bukkitId,folder,file);
        }catch(RuntimeException e){
            throw new WorldCreationException("build phase error: "+e.getMessage());
        }

        try{
            world.load();
        }catch(RuntimeException e){
            World bukkit=world.world();
            if(bukkit!=null){
                Bukkit.unloadWorld(bukkit,false);
            }

            throw new WorldCreationException("load phase error: "+e.getMessage());
        }

        worlds.add(world);
    }

    public static final class WorldCreationException extends Exception{
        public WorldCreationException(String message){
            super(message);
        }
    }

    public static final class EventListener implements Listener {
        private EventListener(){}

        @EventHandler(priority=EventPriority.HIGHEST)
        public void worldUnload(WorldUnloadEvent e){
            World world=e.getWorld();

            for(CustomWorld wr:worlds){
                if(wr.loaded()&&wr.world().equals(world)){
                    logger.info("Redirecting unload to WorldMagic...");
                    e.setCancelled(true);
                    wr.unload();
                    break;
                }
            }
        }
    }
}
