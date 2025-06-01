package io.hotwop.worldmagic.util.serializer;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.meta.Constraint;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigRange{
    int min() default Integer.MIN_VALUE;
    int max() default Integer.MAX_VALUE;

    final class Factory implements Constraint.Factory<@NonNull ConfigRange,Number>{
        public static final Factory instance=new Factory();
        private Factory(){
        }

        @NotNull
        public Constraint<Number> make(ConfigRange data,@NotNull Type type) {
            return value->{
                if(value!=null&&(value.intValue()<data.min()||value.intValue()>data.max())){
                    throw new SerializationException("Value not in range "+data.min()+" - "+data.max());
                }
            };
        }
    }
}