package io.hotwop.worldmagic.util.serializer;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class BlockTypeSerializer extends ScalarSerializer<BlockType> {
    public static final BlockTypeSerializer instance=new BlockTypeSerializer();
    private BlockTypeSerializer(){
        super(BlockType.class);
    }

    public BlockType deserialize(@NotNull Type type, @NotNull Object obj) throws SerializationException {
        NamespacedKey id=NamespacedKeySerializer.instance.deserialize(type,obj);
        BlockType block=Registry.BLOCK.get(id);
        if(block==null)throw new SerializationException("Unknown block type: "+obj);
        return block;
    }
    protected @NotNull Object serialize(BlockType item, @NotNull Predicate<Class<?>> typeSupported) {
        NamespacedKey key=Registry.BLOCK.getKey(item);
        if(key==null)return "";
        return NamespacedKeySerializer.instance.serialize(key,typeSupported);
    }
}
