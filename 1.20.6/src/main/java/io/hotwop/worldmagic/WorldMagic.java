package io.hotwop.worldmagic;

import com.mojang.serialization.Lifecycle;
import io.hotwop.worldmagic.file.WorldFile;
import io.hotwop.worldmagic.generation.CustomWorld;
import io.hotwop.worldmagic.generation.Dimension;
import io.hotwop.worldmagic.util.Util;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.dimension.LevelStem;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class WorldMagic extends JavaPlugin {
    private static ComponentLogger logger;
    public static ComponentLogger logger(){
        return logger;
    }

    private static WorldMagic instance;

    private static Path worldsPath;

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
    public static WorldFile getWorldFile(NamespacedKey id){
        return worldFiles.get(id);
    }

    private static final List<CustomWorld> worlds=new ArrayList<>();

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

        worldsPath= dataFolderPath.resolve("worlds");
        worldsPath.toFile().mkdir();
        loadWorldFiles();
        worldFiles.forEach((id,file)->worlds.add(new CustomWorld(file)));
    }

    @Override
    public void onEnable(){
        worlds.forEach(wr->{
            wr.register();
            if(wr.loading.startup())wr.load();
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
            logger.error("Error to load world files: {}",e.toString());
            return;
        }

        worldFiles.clear();

        Map<WorldFile,Path> patches=new HashMap<>();
        Map<NamespacedKey, List<Path>> conflicts=new HashMap<>();
        loaders.forEach((path,loader)->{
            try{
                CommentedConfigurationNode node=loader.load();
                if(node.isNull()){
                    logger.warn("File {} is empty", path);
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
                logger.warn("Error to load item file {}: {}",path.toString(),ex.getMessage());
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
        List<ResourceKey<LevelStem>> dimensions=new ArrayList<>();

        worlds.forEach(wr->{
            if(wr.loaded())wr.unload();
            if(wr.dimension instanceof Dimension.Inline)dimensions.add(wr.vanillaId);
        });

        CustomWorld.shutdownAsync();
        Util.unregisterAll(Registries.LEVEL_STEM,vanillaServer.registryAccess(),dimensions);
    }


    public static final class EventListener implements Listener {
        private EventListener(){}

        private boolean saving=false;

        @EventHandler(priority=EventPriority.HIGHEST)
        public void worldSave(WorldSaveEvent e){
            if(!saving){
                saving=true;

                List<ResourceKey<LevelStem>> dimensions=new ArrayList<>();
                Map<ResourceKey<LevelStem>,LevelStem> map=new HashMap<>();

                worlds.forEach(wr->{
                    if(wr.registered()&&wr.dimension instanceof Dimension.Inline inl){
                        dimensions.add(wr.vanillaId);
                        map.put(wr.vanillaId,inl.get());
                    }
                });

                Util.unregisterAll(Registries.LEVEL_STEM,vanillaServer.registryAccess(),dimensions);
                scheduler.runTask(instance,()->{
                    Util.registerIgnoreFreezeAll(Registries.LEVEL_STEM,vanillaServer.registryAccess(),map,Lifecycle.experimental());
                    Util.boundRegistrations(Registries.LEVEL_STEM,vanillaServer.registryAccess(),map);
                    saving=false;
                });
            }
        }
    }
}
