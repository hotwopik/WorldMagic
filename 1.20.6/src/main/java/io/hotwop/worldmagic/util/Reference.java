package io.hotwop.worldmagic.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public interface Reference<T,V>{
    T identifier();
    V get();
    default boolean active(){
        return true;
    }
    default void check(){}

    final class Now<T,V> implements Reference<T,V>{
        public final V value;
        public final T identifier;

        public Now(T identifier,V value){
            this.value=value;
            this.identifier=identifier;
        }

        public V get(){
            return value;
        }
        public T identifier() {
            return identifier;
        }
    }

    final class Future<T,V> implements Reference<T,V>{
        private boolean active=false;
        private V value=null;
        public final T identifier;
        public final SupplyReferer<V> referer;

        public Future(T identifier,SupplyReferer<V> referer){
            this.referer=referer;
            this.identifier=identifier;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void check() {
            try{
                value=referer.refer();
                active=true;
            }catch(RefererException e){
                active=false;
            }
        }

        public T identifier() {
            return identifier;
        }
        public V get() {
            return value;
        }
    }

    final class Empty<T,V> implements Reference<T,V>{
        public Empty(){}

        public boolean active(){
            return false;
        }
        public V get() {
            return null;
        }
        public T identifier() {
            return null;
        }
    }

    final class Serializer<T,V> implements TypeSerializer<Reference<T,V>>{
        public final TypeSerializer<T> storeOrigin;
        public final FunctionalReferer<T,V> referer;

        public Serializer(TypeSerializer<T> storeOrigin,FunctionalReferer<T,V> referer){
            this.storeOrigin=storeOrigin;
            this.referer=referer;
        }

        public Reference<T,V> deserialize(@NotNull Type type,@NotNull ConfigurationNode node) throws SerializationException{
            T str=storeOrigin.deserialize(((ParameterizedType)type).getActualTypeArguments()[0],node);
            return formReferer(str);
        }
        public void serialize(@NotNull Type type,@Nullable Reference<T,V> obj,@NotNull ConfigurationNode node) throws SerializationException {
            if(obj==null||obj instanceof Empty)return;
            storeOrigin.serialize(((ParameterizedType)type).getActualTypeArguments()[0],obj.identifier(),node);
        }

        @Override
        public @Nullable Reference<T,V> emptyValue(Type specificType, ConfigurationOptions options) {
            T str=storeOrigin.emptyValue(((ParameterizedType)specificType).getActualTypeArguments()[0],options);
            if(str==null)return new Empty<>();
            return formReferer(str);
        }

        private Reference<T,V> formReferer(T str){
            V value;
            try{
                value=referer.refer(str);
            }catch(RefererException e){
                return new Future<>(str,()->referer.refer(str));
            }

            return new Now<>(str,value);
        }
    }

    final class RefererException extends Exception{}

    @FunctionalInterface
    interface SupplyReferer<V>{
        V refer() throws RefererException;
    }
    @FunctionalInterface
    interface FunctionalReferer<T,V>{
        V refer(T value) throws RefererException;
    }
}
