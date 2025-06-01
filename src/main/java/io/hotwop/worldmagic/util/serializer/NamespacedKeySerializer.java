package io.hotwop.worldmagic.util.serializer;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class NamespacedKeySerializer extends ScalarSerializer<NamespacedKey> {
    public static final NamespacedKeySerializer instance=new NamespacedKeySerializer();
    private NamespacedKeySerializer(){
        super(NamespacedKey.class);
    }

    public NamespacedKey deserialize(@NotNull Type type, @NotNull Object obj) throws SerializationException {
        if(!(obj instanceof String))throw new SerializationException("String expected");
        NamespacedKey name=NamespacedKey.fromString((String)obj);
        if(name==null)throw new SerializationException("NamespacedKey deserialize error");
        return name;
    }

    @NotNull
    public Object serialize(NamespacedKey item, @NotNull Predicate<Class<?>> typeSupported){
        return item.asMinimalString();
    }
}