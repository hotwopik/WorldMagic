package io.hotwop.worldmagic.util.serializer;

import org.bukkit.Location;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public final class LocationSerializer implements TypeSerializer<Location>{
    public static final LocationSerializer instance=new LocationSerializer();
    private LocationSerializer(){}

    public Location deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
        ConfigurationNode worldNode=node.node("world");
        if(!)
    }

    public void serialize(@NotNull Type type, @Nullable Location obj, @NotNull ConfigurationNode node) throws SerializationException {

    }
}
