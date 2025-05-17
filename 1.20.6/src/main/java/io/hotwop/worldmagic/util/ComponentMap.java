package io.hotwop.worldmagic.util;

import com.mojang.serialization.JavaOps;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.Map;

public record ComponentMap(DataComponentMap map){
    public void apply(ItemStack item) {
        CraftItemStack.asNMSCopy(item).applyComponents(map);
    }

    public <T> T get(DataComponentType<T> component){
        return map.get(component);
    }

    public static ComponentMap fromItem(ItemStack item){
        if(item.isEmpty())return null;

        net.minecraft.world.item.ItemStack stack=CraftItemStack.asNMSCopy(item);

        DataComponentMap.Builder builder=DataComponentMap.builder();
        DataComponentMap map=stack.getComponents();
        builder.addAll(map);

        stack.getPrototype().forEach(cp->{
            DataComponentType<?> type=cp.type();
            Object obj=map.get(type);
            if(obj!=null&&obj.equals(cp.value()))builder.set(type,null);
        });

        return new ComponentMap(builder.build());
    }

    public static final class Serializer implements TypeSerializer<ComponentMap> {
        public static final Serializer instance = new Serializer();

        private Serializer() {
        }

        @SuppressWarnings("unchecked")
        public ComponentMap deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
            if (node.isNull()) return null;
            if (!node.isMap()) throw new SerializationException(node, ComponentMap.class, "Map expected");
            Map<Object, Object> map = (Map<Object, Object>) node.raw();

            DataComponentMap data = DataComponentMap.CODEC
                .decode(JavaOps.INSTANCE, map)
                .getOrThrow(mess -> new SerializationException(node, ComponentMap.class, mess))
                .getFirst();

            return new ComponentMap(data);
        }

        @SuppressWarnings("unchecked")
        public void serialize(@NotNull Type type, @Nullable ComponentMap obj, @NotNull ConfigurationNode node) throws SerializationException {
            if (obj == null) return;
            Map<Object, Object> map = (Map<Object, Object>) DataComponentMap.CODEC
                .encodeStart(JavaOps.INSTANCE, obj.map)
                .getOrThrow(mess -> new SerializationException(node, ComponentMap.class, mess));

            Util.fromMap(node, map);
        }
    }
}
