package io.hotwop.worldmagic.util;


import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RegistrationUtil{
    static final Field frozenField;
    static final Field byIdField;
    static final Field toIdField;
    static final Field byLocationField;
    static final Field byKeyField;
    static final Field byValueField;
    static final Field registrationInfosField;
    static final Field valueHolderField;

    static{
        try{
            frozenField= MappedRegistry.class.getDeclaredField("frozen");
            byIdField=MappedRegistry.class.getDeclaredField("byId");
            toIdField=MappedRegistry.class.getDeclaredField("toId");
            byLocationField=MappedRegistry.class.getDeclaredField("byLocation");
            byKeyField=MappedRegistry.class.getDeclaredField("byKey");
            byValueField=MappedRegistry.class.getDeclaredField("byValue");
            registrationInfosField=MappedRegistry.class.getDeclaredField("registrationInfos");

            valueHolderField= Holder.Reference.class.getDeclaredField("value");
        }catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }

        frozenField.setAccessible(true);
        byIdField.setAccessible(true);
        toIdField.setAccessible(true);
        byLocationField.setAccessible(true);
        byKeyField.setAccessible(true);
        byValueField.setAccessible(true);
        registrationInfosField.setAccessible(true);

        valueHolderField.setAccessible(true);
    }

    private RegistrationUtil(){}

    public static <T> void registerIgnoreFreeze(ResourceKey<Registry<T>> registry, RegistryAccess access, ResourceKey<T> id, T value, Lifecycle lifecycle){
        Registry<T> reg= VersionUtil.getRegistry(access,registry);

        if(reg instanceof WritableRegistry<T> wr){
            if(wr instanceof MappedRegistry<T> map){
                boolean frozen;
                try{
                    frozen=frozenField.getBoolean(map);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                if(frozen){
                    try {
                        frozenField.setBoolean(map, false);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                map.register(id,value,new RegistrationInfo(Optional.empty(),lifecycle));

                if(frozen){
                    try {
                        frozenField.setBoolean(map, true);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else wr.register(id,value,new RegistrationInfo(Optional.empty(),lifecycle));
        }else throw new RuntimeException("Registry isn't writable at all");
    }

    public static <T> void bindRegistration(ResourceKey<Registry<T>> registry, RegistryAccess access, ResourceKey<T> id, T value){
        Registry<T> reg= VersionUtil.getRegistry(access,registry);

        Holder.Reference<T> ref= VersionUtil.getHolder(reg,id);
        if(ref!=null){
            try{
                valueHolderField.set(ref,value);
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void unregisterAll(ResourceKey<Registry<T>> registry, RegistryAccess access, List<ResourceKey<T>> ids){
        Registry<T> reg= VersionUtil.getRegistry(access,registry);

        if(reg instanceof MappedRegistry<T>){
            ObjectList<Holder.Reference<T>> byId;
            Reference2IntMap<T> toId;
            Map<ResourceLocation, Holder.Reference<T>> byLocation;
            Map<ResourceKey<T>, Holder.Reference<T>> byKey;
            Map<T, Holder.Reference<T>> byValue;
            Map<ResourceKey<T>, RegistrationInfo> registrationInfos;

            try{
                byId=(ObjectList<Holder.Reference<T>>)byIdField.get(reg);
                toId=(Reference2IntMap<T>)toIdField.get(reg);
                byLocation=(Map<ResourceLocation, Holder.Reference<T>>)byLocationField.get(reg);
                byKey=(Map<ResourceKey<T>, Holder.Reference<T>>)byKeyField.get(reg);
                byValue=(Map<T, Holder.Reference<T>>)byValueField.get(reg);
                registrationInfos=(Map<ResourceKey<T>, RegistrationInfo>)registrationInfosField.get(reg);
            }catch(IllegalAccessException e){
                throw new RuntimeException(e);
            }

            for(ResourceKey<T> id:ids){
                T value= VersionUtil.registryGet(reg,id.location());
                Holder.Reference<T> ref= VersionUtil.getHolderOrThrow(reg,id);
                ResourceLocation loc=id.location();

                int index=byId.indexOf(ref);
                byId.remove(ref);

                toId.remove(value,index);
                byLocation.remove(loc);
                byKey.remove(id);
                byValue.remove(value);
                registrationInfos.remove(id);
            }
        }else throw new RuntimeException("Can't uregister not mapped registry");
    }

    public static <T> void registerIgnoreFreezeAll(ResourceKey<Registry<T>> registry, RegistryAccess access, Map<ResourceKey<T>,T> entries, Lifecycle lifecycle){
        Registry<T> reg= VersionUtil.getRegistry(access,registry);

        if(reg instanceof WritableRegistry<T> wr){
            if(wr instanceof MappedRegistry<T> map){
                boolean frozen;
                try{
                    frozen=frozenField.getBoolean(map);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                if(frozen){
                    try {
                        frozenField.setBoolean(map, false);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                registerIgnoreFreezeAllOp(wr,entries,lifecycle);

                if(frozen){
                    try {
                        frozenField.setBoolean(map, true);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else registerIgnoreFreezeAllOp(wr,entries,lifecycle);
        }else throw new RuntimeException("Registry isn't writable at all");
    }

    private static <T> void registerIgnoreFreezeAllOp(WritableRegistry<T> registry, Map<ResourceKey<T>,T> entries, Lifecycle lifecycle){
        RegistrationInfo info=new RegistrationInfo(Optional.empty(),lifecycle);

        for(Map.Entry<ResourceKey<T>,T> entry:entries.entrySet()){
            registry.register(entry.getKey(),entry.getValue(),info);
        }
    }

    public static <T> void bindRegistrations(ResourceKey<Registry<T>> registry, RegistryAccess access, Map<ResourceKey<T>,T> entries){
        Registry<T> reg= VersionUtil.getRegistry(access,registry);

        for(Map.Entry<ResourceKey<T>,T> entry:entries.entrySet()){
            Holder.Reference<T> ref= VersionUtil.getHolder(reg,entry.getKey());
            if(ref!=null){
                try{
                    valueHolderField.set(ref,entry.getValue());
                }catch(IllegalAccessException e){
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
