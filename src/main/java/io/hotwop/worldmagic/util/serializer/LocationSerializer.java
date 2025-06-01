package io.hotwop.worldmagic.util.serializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public final class LocationSerializer implements TypeSerializer<Location>{
    public static final LocationSerializer instance=new LocationSerializer();
    private LocationSerializer(){}

    private static final NamespacedKey overworld=NamespacedKey.minecraft("overworld");

    public Location deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
        ConfigurationNode worldNode=node.node("world");
        World world;

        if(!worldNode.virtual()){
            NamespacedKey worldKey=worldNode.require(NamespacedKey.class);
            world=Bukkit.getWorld(worldKey);
            if(world==null)throw new SerializationException(node,Location.class,"Unknown world: "+worldKey.asMinimalString());
        }else world=Bukkit.getWorld(overworld);

        ConfigurationNode xNode=node.node("x");
        if(xNode.virtual())throw new SerializationException(node,Location.class,"Location X not specified");
        double x=xNode.getDouble();

        ConfigurationNode yNode=node.node("y");
        if(yNode.virtual())throw new SerializationException(node,Location.class,"Location Y not specified");
        double y=yNode.getDouble();

        ConfigurationNode zNode=node.node("z");
        if(zNode.virtual())throw new SerializationException(node,Location.class,"Location Z not specified");
        double z=zNode.getDouble();

        float yaw=node.node("yaw").getFloat();
        float pitch=node.node("pitch").getFloat();

        return new Location(world,x,y,z,yaw,pitch);
    }

    public void serialize(@NotNull Type type, @Nullable Location obj, @NotNull ConfigurationNode node) throws SerializationException {
        if(obj==null)return;

        NamespacedKey world=obj.getWorld().getKey();
        if(!world.equals(overworld))node.node("world").set(world);

        node.node("x").set(obj.x());
        node.node("y").set(obj.y());
        node.node("z").set(obj.z());

        if(obj.getYaw()!=0)node.node("yaw").set(obj.getYaw());
        if(obj.getPitch()!=0)node.node("pitch").set(obj.getPitch());
    }
}
