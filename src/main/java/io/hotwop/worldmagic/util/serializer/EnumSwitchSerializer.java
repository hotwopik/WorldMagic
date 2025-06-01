package io.hotwop.worldmagic.util.serializer;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.ObjectMapper;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import oshi.util.tuples.Pair;

import javax.annotation.Nullable;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class EnumSwitchSerializer<T,E extends Enum<?>> implements TypeSerializer<T> {
    private final Class<T> clazz;
    private final Class<E> enm;
    private final Object typeNode;
    private final Object valueNode;
    private final boolean parent;

    private final Map<E,Class<? extends T>> toClass;
    private final Map<Class<? extends T>,E> toEnum;

    private final Map<E,NotNodeData<? extends T>> toNotNode;
    private final Map<Class<? extends T>,E> fromNotNode;

    private EnumSwitchSerializer(Class<T> clazz,Class<E> enm,Object typeNode,Object valueNode,boolean parent,Map<E,Class<? extends T>> toClass,Map<Class<? extends T>,E> toEnum,Map<E,NotNodeData<? extends T>> toNotNode,Map<Class<? extends T>,E> fromNotNode){
        this.clazz=clazz;
        this.enm=enm;
        this.typeNode=typeNode;
        this.valueNode=valueNode;
        this.parent=parent;

        this.toClass=toClass;
        this.toEnum=toEnum;

        this.toNotNode=toNotNode;
        this.fromNotNode=fromNotNode;
    }

    public static <T,E extends Enum<?>> Builder<T,E> builder(Class<T> clazz,Class<E> enm,Object typeNode){
        Objects.requireNonNull(typeNode);
        if(clazz.accessFlags().contains(AccessFlag.FINAL))throw new RuntimeException("Error to build EnumSwitchSerializer: switchable class can't be final");

        return new Builder<>(clazz,enm,typeNode,null,true);
    }
    public static <T,E extends Enum<?>> Builder<T,E> builder(Class<T> clazz,Class<E> enm,Object typeNode,Object valueNode){
        Objects.requireNonNull(typeNode);
        Objects.requireNonNull(valueNode);
        if(clazz.accessFlags().contains(AccessFlag.FINAL))throw new RuntimeException("Error to build EnumSwitchSerializer: switchable class can't be final");

        return new Builder<>(clazz,enm,typeNode,valueNode,false);
    }

    public T deserialize(@NotNull Type typ, @NotNull ConfigurationNode node) throws SerializationException {
        if(parent){
            ConfigurationNode parentNode=node.parent();
            if(parentNode==null)throw new SerializationException(node,clazz,"Parented enum switch can't be a root");

            ConfigurationNode typNode=parentNode.node(typeNode);
            if(typNode.virtual())return null;

            return deserialize(typ,typNode,node,node);
        }else{
            ConfigurationNode typNode=node.node(typeNode);
            if(typNode.virtual())return null;

            ConfigurationNode valNode=node.node(valueNode);

            return deserialize(typ,typNode,valNode,node);
        }
    }
    private T deserialize(@NotNull Type typ,@NotNull ConfigurationNode typNode,@NotNull ConfigurationNode valNode,@NotNull ConfigurationNode origin) throws SerializationException{
        E type=typNode.require(enm);

        if(toClass.containsKey(type)){
            if(valNode.virtual())return null;

            Class<? extends T> subClass=toClass.get(type);

            TypeSerializer<? extends T> serializer=origin.options().serializers().get(subClass);
            if(serializer==null||serializer.equals(this)){
                if(subClass.isAnnotationPresent(ConfigSerializable.class)){
                    ObjectMapper<? extends T> mapper=ObjectMapper.factory().get(subClass);
                    return mapper.load(valNode);
                }else throw new SerializationException(origin,clazz,"Enum switch: Class "+subClass.getName()+" hasn't own serializer");
            }else return serializer.deserialize(typ,valNode);
        }else if(toNotNode.containsKey(type)){
            NotNodeData<? extends T> data=toNotNode.get(type);

            return data.supply.get();
        }else throw new SerializationException(origin,clazz,"Enum switch hasn't definition for enum "+type.name());
    }

    @SuppressWarnings("unchecked")
    public void serialize(@NotNull Type typ, @Nullable T obj, @NotNull ConfigurationNode node) throws SerializationException {
        if(obj==null)return;
        Class<? extends T> subClass=obj.getClass().asSubclass(clazz);

        E type;
        if(toEnum.containsKey(subClass)){
            type=toEnum.get(subClass);
            TypeSerializer<T> serializer=(TypeSerializer<T>)node.options().serializers().get(subClass);

            ConfigurationNode valNode=parent?node:node.node(valueNode);

            if(serializer==null||serializer.equals(this)){
                if(subClass.isAnnotationPresent(ConfigSerializable.class)){
                    ObjectMapper<T> mapper=(ObjectMapper<T>)ObjectMapper.factory().get(subClass);

                    mapper.save(obj,valNode);
                }else throw new SerializationException(node,clazz,"Enum switch: Class "+subClass.getName()+" hasn't own serializer");
            }else serializer.serialize(typ,obj,valNode);
        }else if(fromNotNode.containsKey(subClass))type=fromNotNode.get(subClass);
        else throw new SerializationException(node,clazz,"Class "+subClass.getName()+" wasn't defined in enum switch");

        ConfigurationNode typNode;
        if(parent){
            ConfigurationNode parentNode=node.parent();
            if(parentNode==null)throw new SerializationException(node,clazz,"Parented enum switch can't be a root");

            typNode=parentNode.node(typeNode);
            if(!typNode.virtual())throw new SerializationException(node,clazz,"Parented enum switch: Type node already defined");
        }else typNode=node.node(typeNode);

        typNode.set(type);
    }

    public static final class Builder<T,E extends Enum<?>>{
        private final Class<T> clazz;
        private final Class<E> enm;
        private final Object typeNode;
        private final Object valueNode;
        private final boolean parent;

        private final Map<E,Class<? extends T>> toClass=new HashMap<>();
        private final Map<Class<? extends T>,E> toEnum=new HashMap<>();

        private final Map<E,NotNodeData<? extends T>> toNotNode=new HashMap<>();
        private final Map<Class<? extends T>,E> fromNotNode=new HashMap<>();

        private Builder(Class<T> clazz,Class<E> enm,Object typeNode,Object valueNode,boolean parent){
            this.clazz=clazz;
            this.enm=enm;
            this.typeNode=typeNode;
            this.valueNode=valueNode;
            this.parent=parent;
        }

        public Builder<T,E> define(E enm,Class<? extends T> clazz){
            if(toClass.containsKey(enm)||toNotNode.containsKey(enm))throw new RuntimeException("Error to build EnumSwitchSerializer: Enum "+enm.name()+" already defined");
            if(toEnum.containsKey(clazz)||fromNotNode.containsKey(clazz))throw new RuntimeException("Error to build EnumSwitchSerializer: Class "+clazz.getName()+" already used");

            toClass.put(enm,clazz);
            toEnum.put(clazz,enm);

            return this;
        }
        public <V extends T> Builder<T,E> defineNotNode(E enm,Class<V> clazz,Supplier<V> supply){
            if(toClass.containsKey(enm)||toNotNode.containsKey(enm))throw new RuntimeException("Error to build EnumSwitchSerializer: Enum "+enm.name()+" already defined");
            if(toEnum.containsKey(clazz)||fromNotNode.containsKey(clazz))throw new RuntimeException("Error to build EnumSwitchSerializer: Class "+clazz.getName()+" already used");

            toNotNode.put(enm,new NotNodeData<>(clazz,supply));
            fromNotNode.put(clazz,enm);

            return this;
        }

        public EnumSwitchSerializer<T,E> build(){
            if(toClass.isEmpty()&&fromNotNode.isEmpty())throw new RuntimeException("Error to build EnumSwitchSerializer: No one case is defined");

            return new EnumSwitchSerializer<>(clazz,enm,typeNode,valueNode,parent,Map.copyOf(toClass),Map.copyOf(toEnum),Map.copyOf(toNotNode),Map.copyOf(fromNotNode));
        }
    }

    private record NotNodeData<V>(
        Class<V> clazz,
        Supplier<V> supply
    ){}
}
