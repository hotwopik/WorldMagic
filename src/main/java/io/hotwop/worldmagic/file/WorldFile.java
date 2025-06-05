package io.hotwop.worldmagic.file;

import io.hotwop.worldmagic.generation.Dimension;
import io.hotwop.worldmagic.generation.GameRuleFactory;
import io.hotwop.worldmagic.generation.GeneratorSettings;
import io.hotwop.worldmagic.util.serializer.*;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

@ConfigSerializable
public final class WorldFile{
    public static final ObjectMapper.Factory objectFactory=ObjectMapper.factoryBuilder()
        .addConstraint(ConfigRange.class, Number.class, ConfigRange.Factory.instance)
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

    @Required
    public NamespacedKey id;

    public String bukkitId;

    public String folder;

    public boolean prototype=false;

    public WorldProperties worldProperties;

    @ConfigSerializable
    public static final class WorldProperties{
        public boolean override=true;

        public Long seed;

        public boolean generateStructures=true;

        public boolean bonusChest=false;

        public GameMode defaultGamemode=GameMode.SURVIVAL;
        public boolean forceDefaultGamemode=false;

        public Difficulty difficulty=Difficulty.NORMAL;

        public String requiredPermission;

        @ConfigRange(min=0)
        public Integer enterPayment;
    }

    public Dimension dimension=new Dimension.Reference(NamespacedKey.minecraft("overworld"));

    public Loading loading;

    @ConfigSerializable
    public static final class Loading{
        public boolean async=false;
        public boolean startup=true;
        public boolean loadChunks=true;
        public boolean save=true;
        public boolean folderDeletion=false;
        public boolean loadControl=true;
    }

    public SpawnPosition spawnPosition=null;

    public Location callbackLocation=null;

    @ConfigSerializable
    public static final class SpawnPosition{
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

    public AllowSettings allowSettings;

    @ConfigSerializable
    public static final class AllowSettings{
        public boolean animals=true;
        public boolean monsters=true;
        public boolean pvp=true;
    }

    public GameRuleFactory gamerules;

    public WorldBorderSettings border;

    @ConfigSerializable
    public static final class WorldBorderSettings{
        public boolean override=false;

        public double size=29999984;
        public double safeZone=5;
        public double damagePerBlock=0.2;

        public Center center=new Center();

        @ConfigSerializable
        public static final class Center{
            public double x=0;
            public double z=0;
        }

        public Warning warning=new Warning();

        @ConfigSerializable
        public static final class Warning{
            public int distance=5;
            public int time=300;
        }
    }
}
