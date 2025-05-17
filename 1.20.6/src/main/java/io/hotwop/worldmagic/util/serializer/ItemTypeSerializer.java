package io.hotwop.worldmagic.util.serializer;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class ItemTypeSerializer extends ScalarSerializer<ItemType> {
    public static final ItemTypeSerializer instance=new ItemTypeSerializer();
    private ItemTypeSerializer(){
        super(ItemType.class);
    }

    public ItemType deserialize(@NotNull Type type, @NotNull Object obj) throws SerializationException {
        NamespacedKey id=NamespacedKeySerializer.instance.deserialize(type,obj);
        ItemType item= Registry.ITEM.get(id);
        if(item==null)throw new SerializationException("Unknown item type: "+obj);
        return item;
    }
    protected @NotNull Object serialize(ItemType item, @NotNull Predicate<Class<?>> typeSupported) {
        NamespacedKey key=Registry.ITEM.getKey(item);
        if(key==null)return "";
        return NamespacedKeySerializer.instance.serialize(key,typeSupported);
    }
}
