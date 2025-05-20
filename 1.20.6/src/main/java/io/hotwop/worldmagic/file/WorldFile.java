package io.hotwop.worldmagic.file;

import io.hotwop.worldmagic.generation.Dimension;
import io.hotwop.worldmagic.generation.GameRuleFactory;
import io.hotwop.worldmagic.generation.GeneratorSettings;
import io.hotwop.worldmagic.util.Reference;
import io.hotwop.worldmagic.util.serializer.*;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.List;

@ConfigSerializable
public final class WorldFile{
    public static final ObjectMapper.Factory objectFactory=ObjectMapper.factoryBuilder()
        .addConstraint(ConfigRange.class, Number.class, ConfigRange.Factory.instance)
        .addConstraint(NotEmpty.class, List.class,NotEmpty.Factory.factory())
        .addConstraint(RequiredReference.class,Reference.class,RequiredReference.Factory.instance)
        .build();

    public static final TypeSerializerCollection serializers=TypeSerializerCollection.builder()
        .register(NamespacedKeySerializer.instance)
        .register(Dimension.class,Dimension.Serializer.instance)
        .register(Location.class,LocationSerializer.instance)
        .register(GameRuleFactory.class,GameRuleFactory.Serializer.instance)
        .registerAll(GeneratorSettings.serializer)
        .registerAnnotatedObjects(objectFactory)
        .build();

    public static YamlConfigurationLoader createLoader(Path path){
        return YamlConfigurationLoader.builder()
            .path(path)
            .indent(2)
            .defaultOptions(bl->bl
                .serializers(sr->sr.registerAll(serializers))
            )
            .build();
    }

    @Comment("Vanilla world id, namespaced id, required")
    @Required
    public NamespacedKey id;

    @Comment("Bukkit world id, string, default to <vanilla id namespace>_<vanilla id name>")
    public String bukkitId;

    @Comment("Folder where world will be stored relative to default world container, string, default to world bukkit id")
    public String folder;

    public WorldProperties worldProperties;

    @ConfigSerializable
    public static final class WorldProperties{
        @Comment("World seed, long, default to 0")
        public long seed=0;

        @Comment("Should structure generator be enabled in the world, bool, default to true")
        public boolean generateStructures=true;

        @Comment("Should bonus chest be created on world first startup, bool, default to false")
        public boolean bonusChest=false;

        @Comment("Default game mode in the world, can be CREATIVE, SURVIVAL, ADVENTURE or SPECTATOR, default to SURVIVAL")
        public GameMode defaultGamemode=GameMode.SURVIVAL;

        @Comment("Default difficulty in the world, can be PEACEFUL, EASY, NORMAL or HARD, default to NORMAL")
        public Difficulty difficulty=Difficulty.NORMAL;
    }

    @Comment("Vanilla world configuration, namespaced id reference or inline, default to overworld")
    public Dimension dimension=new Dimension.Reference(NamespacedKey.minecraft("overworld"));

    @Comment("World load configuration")
    public Loading loading;

    @ConfigSerializable
    public static final class Loading{
        @Comment("Make plugin load world asynchronously, may cause conflicts with other plugins, boolean, default to false")
        public boolean async=false;

        @Comment("Should world loads at server startup, boolean, default to true")
        public boolean startup=true;

        @Comment("Should plugin override previously saved world settings, boolean, default to true")
        public boolean override=true;

        @Comment("Should plugin load chunks immediately on world load (if radius not equals 0 and load-chunks disabled, chunks still be loaded in some server ticks), boolean, default to true")
        public boolean loadChunks=true;

        @Comment("Should plugin save world on unload, boolean, default to true")
        public boolean save=true;

        @Comment("Should plugin delete world folder after world unload, boolean, default to false")
        public boolean folderDeletion=false;
    }

    @Comment("World spawn position, by default set by minecraft")
    public SpawnPosition spawnPosition=null;

    @Comment("Position to which players sends on world unload, default to overworld spawn(Vanilla world ids should be used)")
    public Location callbackLocation=null;

    @ConfigSerializable
    public static final class SpawnPosition{
        @Comment("Should spawn position be overwritten at every world load,boolean, default to false")
        public boolean override=false;

        @Required
        public int x;

        @Required
        public int y;

        @Required
        public int z;

        @ConfigRange(min=-180,max=180)
        public float yaw=0;
    }

    @Comment("Configure what allowed in world")
    public AllowSettings allowSettings;

    @ConfigSerializable
    public static final class AllowSettings{
        public boolean animals=true;
        public boolean monsters=true;
        public boolean pvp=true;
    }

    @Comment("Configure gamerules in the world, set override to true to make it reset on every load")
    public GameRuleFactory gamerules;
}
