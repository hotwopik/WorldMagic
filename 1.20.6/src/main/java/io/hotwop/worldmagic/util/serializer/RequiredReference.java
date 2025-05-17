package io.hotwop.worldmagic.util.serializer;

import io.hotwop.worldmagic.util.Reference;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.meta.Constraint;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RequiredReference {
    final class Factory implements Constraint.Factory<@NonNull RequiredReference,Reference>{
        public static final Factory instance=new Factory();
        private Factory(){}

        @NotNull
        public Constraint<Reference> make(RequiredReference data,@NotNull Type type) {
            return value->{
                if(value!=null){
                    value.check();
                    if(!value.active())throw new SerializationException("Passed identifier not reference to some value: "+value.identifier().toString());
                }
            };
        }
    }
}
