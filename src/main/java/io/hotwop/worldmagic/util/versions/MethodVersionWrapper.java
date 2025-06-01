package io.hotwop.worldmagic.util.versions;

import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMaps;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

public final class MethodVersionWrapper<R>{
    private final Class<?> owner;
    private final Class<?>[] defaultParameters;
    private final String defaultName;
    private final Object defaultExecutor;

    private final Int2ObjectSortedMap<String> nameMap;
    private final Int2ObjectSortedMap<ResultMapping<R,?>> resultMap;
    private final Int2ObjectSortedMap<Function<Object[],Object[]>> allParametersMap;
    private final Int2ObjectSortedMap<Int2ObjectSortedMap<Function<Object,Object>>> oneParameterMap;
    private final Int2ObjectSortedMap<Class<?>[]> parameters;
    private final Int2ObjectSortedMap<Object> executorSwitch;

    private MethodVersionWrapper(
        Class<?> owner,
        Class<?>[] defaultParameters,
        String defaultName,
        Object defaultExecutor,
        Int2ObjectSortedMap<String> nameMap,
        Int2ObjectSortedMap<ResultMapping<R,?>> resultMap,
        Int2ObjectSortedMap<Function<Object[],Object[]>> allParametersMap,
        Int2ObjectSortedMap<Int2ObjectSortedMap<Function<Object,Object>>> oneParameterMap,
        Int2ObjectSortedMap<Class<?>[]> parameters,
        Int2ObjectSortedMap<Object> executorSwitch
    ){
        this.owner=owner;
        this.defaultParameters=defaultParameters;
        this.defaultName=defaultName;
        this.defaultExecutor=defaultExecutor;
        this.nameMap=nameMap;
        this.resultMap=resultMap;
        this.allParametersMap=allParametersMap;
        this.oneParameterMap=oneParameterMap;
        this.parameters=parameters;
        this.executorSwitch=executorSwitch;
    }

    public MethodMapping<R> createMapping(int version){
        String name=findUnderOrElse(nameMap,version+1,defaultName);
        ResultMapping<R,?> result=findUnderOrElse(resultMap,version+1,null);
        Class<?>[] parametersVer=findUnderOrElse(parameters,version+1,defaultParameters);

        Method method;
        Constructor<?> constructor;

        try{
            if(name.equals("<init>")){
                method=null;
                constructor=owner.getConstructor(parametersVer);
            }else{
                constructor=null;
                method=owner.getMethod(name,parametersVer);
            }
        }catch(NoSuchMethodException e){
            throw new RuntimeException(e);
        }

        Function<Object[],Object[]> allTransformer=findUnderOrElse(allParametersMap,version+1,null);
        Int2ObjectSortedMap<Function<Object,Object>> oneConverters=findConverters(version+1);

        Object executor=findUnderOrElse(executorSwitch,version+1,defaultExecutor);

        return new MethodMapping<>() {
            @Override
            public R invoke(Object... params) {
                return invokeWithExecutor(executor,params);
            }

            @Override
            @SuppressWarnings("unchecked")
            public R invokeWithExecutor(Object executor, Object... params){
                Object[] modify=allTransformer==null?Arrays.copyOf(params,params.length):allTransformer.apply(Arrays.copyOf(params,params.length));

                for(int i=0;i<modify.length;++i){
                    if(oneConverters.containsKey(i)){
                        modify[i]=oneConverters.get(i).apply(modify[i]);
                    }
                }

                Object val;
                try{
                    if(name.equals("<init>"))val=constructor.newInstance(modify);
                    else val=method.invoke(executor,modify);
                }catch(IllegalAccessException|InvocationTargetException|InstantiationException e){
                    throw new RuntimeException(e);
                }

                return result==null?(R)val:result.convert(val);
            }
        };
    }

    private static <T> T findUnderOrElse(Int2ObjectSortedMap<T> map, int index, T def){
        Map.Entry<Integer,T> entry=map.headMap(index).lastEntry();
        if(entry==null)return def;
        return entry.getValue();
    }

    private Int2ObjectSortedMap<Function<Object,Object>> findConverters(int version){
        Int2ObjectSortedMap<Function<Object,Object>> out=new Int2ObjectRBTreeMap<>();

        oneParameterMap.headMap(version).reversed().forEach((vers,converters)->
            converters.forEach((index, conv)->{
                if(!out.containsKey(index.intValue()))out.put(index.intValue(),conv);
            })
        );

        return out;
    }

    public static final class Builder<R>{
        private final Class<?> owner;
        private final Class<?>[] defaultParameters;
        private final String defaultName;
        private final Object defaultExecutor;

        private final Int2ObjectSortedMap<String> nameMap=new Int2ObjectRBTreeMap<>();
        private final Int2ObjectSortedMap<ResultMapping<R,?>> resultMap=new Int2ObjectRBTreeMap<>();
        private final Int2ObjectSortedMap<Function<Object[],Object[]>> allParametersMap=new Int2ObjectRBTreeMap<>();
        private final Int2ObjectSortedMap<Int2ObjectSortedMap<Function<Object,Object>>> oneParameterMap=new Int2ObjectRBTreeMap<>();
        private final Int2ObjectSortedMap<Class<?>[]> parameters=new Int2ObjectRBTreeMap<>();
        private final Int2ObjectSortedMap<Object> executorSwitch=new Int2ObjectRBTreeMap<>();

        public Builder(Class<?> owner,String defaultName,Object defaultExecutor,Class<?>... defaultParameters){
            this.owner=owner;
            this.defaultParameters=defaultParameters;
            this.defaultName=defaultName;
            this.defaultExecutor=defaultExecutor;
        }

        public Builder<R> nameMapping(int version,String name){
            nameMap.put(version,name);
            return this;
        }

        public <T> Builder<R> resultMapping(int version,Class<T> versionResult,Function<T,R> conversion){
            resultMap.put(version,new ResultMapping<>(versionResult,conversion));
            return this;
        }

        public Builder<R> oneParameterMapping(int version,int order,Class<?> parameterClass,Function<Object,Object> conversion){
            parameters.compute(version,(vers,params)->addTo(params==null?findUnderOrDefaultParameters(version):params,order,parameterClass));
            oneParameterMap.computeIfAbsent(version,v->new Int2ObjectRBTreeMap<>()).put(order,conversion);

            return this;
        }

        public Builder<R> allParameterMapping(int version,Function<Object[],Object[]> conversion,Class<?>... parameterClasses){
            parameters.put(version,parameterClasses);
            allParametersMap.put(version,conversion);

            return this;
        }

        public Builder<R> executorSwitch(int version,Object executor){
            executorSwitch.put(version,executor);

            return this;
        }

        public MethodVersionWrapper<R> build(){
            return new MethodVersionWrapper<>(
                owner,
                defaultParameters,
                defaultName,
                defaultExecutor,
                Int2ObjectSortedMaps.unmodifiable(nameMap),
                Int2ObjectSortedMaps.unmodifiable(resultMap),
                Int2ObjectSortedMaps.unmodifiable(allParametersMap),
                Int2ObjectSortedMaps.unmodifiable(freezeAll(oneParameterMap)),
                Int2ObjectSortedMaps.unmodifiable(parameters),
                Int2ObjectSortedMaps.unmodifiable(executorSwitch)
            );
        }

        private Class<?>[] findUnderOrDefaultParameters(int version){
            Map.Entry<Integer,Class<?>[]> entry=parameters.headMap(version).lastEntry();
            if(entry!=null)return entry.getValue();
            return defaultParameters;
        }
    }

    private record ResultMapping<R,T>(
        Class<T> clazz,
        Function<T,R> conversion
    ){
        public R convert(Object obj){
            return conversion.apply(clazz.cast(obj));
        }
    }

    private static Class<?>[] addTo(Class<?>[] input,int index,Class<?> obj){
        Class<?>[] out=Arrays.copyOf(input,Math.max(index+1,input.length));
        out[index]=obj;
        return out;
    }

    private static <T> Int2ObjectSortedMap<Int2ObjectSortedMap<T>> freezeAll(Int2ObjectSortedMap<Int2ObjectSortedMap<T>> map){
        Int2ObjectSortedMap<Int2ObjectSortedMap<T>> out=new Int2ObjectRBTreeMap<>();

        map.forEach((id,val)->out.put(id.intValue(),Int2ObjectSortedMaps.unmodifiable(val)));

        return out;
    }
}
