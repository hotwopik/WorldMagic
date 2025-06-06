package io.hotwop.worldmagic.util.serializer;

import io.hotwop.worldmagic.api.LocationResolver;
import org.bukkit.NamespacedKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public final class LocationResolverSerializer implements TypeSerializer<LocationResolver>{
    public static final LocationResolverSerializer instance=new LocationResolverSerializer();
    private LocationResolverSerializer(){}

    @Override
    public LocationResolver deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
        ConfigurationNode worldNode=node.node("world");
        ConfigurationNode xNode=node.node("x");
        ConfigurationNode yNode=node.node("y");
        ConfigurationNode zNode=node.node("z");
        ConfigurationNode yawNode=node.node("yaw");
        ConfigurationNode pitchNode=node.node("pitch");

        if(worldNode.virtual())throw new SerializationException(node,LocationResolver.class,"world node missing");
        if(xNode.virtual())throw new SerializationException(node,LocationResolver.class,"x node missing");
        if(yNode.virtual())throw new SerializationException(node,LocationResolver.class,"y node missing");
        if(zNode.virtual())throw new SerializationException(node,LocationResolver.class,"z node missing");

        NamespacedKey world=worldNode.require(NamespacedKey.class);
        double x=xNode.getDouble();
        double y=yNode.getDouble();
        double z=zNode.getDouble();

        float yaw;
        float pitch;

        if(yawNode.virtual())yaw=0;
        else yaw=yawNode.getFloat();

        if(pitchNode.virtual())pitch=0;
        else pitch=pitchNode.getFloat();

        return LocationResolver.resolver(world,x,y,z,yaw,pitch);
    }
    @Override
    public void serialize(@NotNull Type type, @Nullable LocationResolver obj,@NotNull ConfigurationNode node) throws SerializationException {
        if(obj==null)return;

        NamespacedKey world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;

        if(obj instanceof LocationResolver.RuntimeResolver run){
            world=run.world;
            x=run.x;
            y=run.y;
            z=run.z;
            yaw=run.yaw;
            pitch=run.pitch;
        }else if(obj instanceof LocationResolver.AlreadyResolver al){
            world=al.location.getWorld().getKey();
            x=al.location.getX();
            y=al.location.getY();
            z=al.location.getZ();
            yaw=al.location.getYaw();
            pitch=al.location.getPitch();
        }else throw new RuntimeException();

        node.node("world").set(world);
        node.node("x").set(x);
        node.node("y").set(y);
        node.node("z").set(z);
        node.node("yaw").set(yaw);
        node.node("pitch").set(pitch);
    }
}
