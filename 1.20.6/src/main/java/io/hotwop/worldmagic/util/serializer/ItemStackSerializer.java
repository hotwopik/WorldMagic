package io.hotwop.worldmagic.util.serializer;

import io.hotwop.worldmagic.util.ComponentMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public final class ItemStackSerializer implements TypeSerializer<ItemStack> {
    public static final ItemStackSerializer instance=new ItemStackSerializer();
    private ItemStackSerializer(){}

    public ItemStack deserialize(@NotNull Type typ, @NotNull ConfigurationNode node) throws SerializationException{
        ConfigurationNode idNode=node.node("id");
        ConfigurationNode countNode=node.node("count");

        int count=countNode.virtual()?1:countNode.getInt();
        if(count<1)throw new SerializationException(countNode,Integer.class,"Item count must be positive");

        if(!idNode.virtual()){
            ItemStack out=idNode.require(ItemType.class).createItemStack(count);

            ConfigurationNode componentsNode=node.node("components");
            if(!componentsNode.virtual()){
                ComponentMap components=componentsNode.require(ComponentMap.class);
                components.apply(out);
            }

            return out;
        }else throw new SerializationException(node,ItemStack.class,"Item should contain \"id\" node");
    }
    public void serialize(@NotNull Type typ, @Nullable ItemStack obj, @NotNull ConfigurationNode node) throws SerializationException {
        if(obj==null||obj.isEmpty())return;

        int count=obj.getAmount();
        if(count!=1)node.node("count").set(count);

        node.node("id").set(obj.getType().asItemType());

        ComponentMap components=ComponentMap.fromItem(obj);
        if(!components.map().isEmpty())node.node("components").set(components);
    }
}
