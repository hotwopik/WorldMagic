package io.hotwop.worldmagic.version;

public interface MethodMapping<R> {
    R invoke(Object... params);

    R invokeWithExecutor(Object executor, Object... params);
}
