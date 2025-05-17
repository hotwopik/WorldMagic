package io.hotwop.worldmagic.util.serializer;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.objectmapping.meta.Constraint;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

public @interface NotEmpty{
    final class Factory{
        public static Constraint.Factory<@NonNull NotEmpty,List> factory(){
            return (data,type)->(val)->{
                if(val!=null&&val.isEmpty())throw new SerializationException("List is empty");
            };
        }
    }
}
