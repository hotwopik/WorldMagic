package io.hotwop.worldmagic.version;

import java.util.function.Function;

public record ResultMapping<R,T>(
    Class<T> clazz,
    Function<T,R> transform
){
    public R convert(Object value){
        return transform.apply(clazz.cast(value));
    }
}
