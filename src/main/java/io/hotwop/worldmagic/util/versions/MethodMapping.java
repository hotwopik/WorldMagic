package io.hotwop.worldmagic.util.versions;

public interface MethodMapping<R> {
    R invoke(Object... params);

    R invokeWithExecutor(Object executor, Object... params);
}
