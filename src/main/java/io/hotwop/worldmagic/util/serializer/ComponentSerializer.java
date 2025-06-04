package io.hotwop.worldmagic.util.serializer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.ScalarSerializer;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;
import java.util.function.Predicate;

public final class ComponentSerializer extends ScalarSerializer<Component>{
    public static final ComponentSerializer instance=new ComponentSerializer();
    private ComponentSerializer(){
        super(Component.class);
    }

    public Component deserialize(@NotNull Type type, @NotNull Object obj) throws SerializationException {
        if(!(obj instanceof String))throw new SerializationException("String expected");
        return MiniMessage.miniMessage().deserialize((String)obj);
    }

    @NotNull
    public Object serialize(Component item, @NotNull Predicate<Class<?>> typeSupported){
        return MiniMessage.miniMessage().serialize(item);
    }
}
