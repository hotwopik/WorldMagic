package io.hotwop.worldmagic.api;

public final class WorldAlreadyUnloadedException extends RuntimeException{
    public WorldAlreadyUnloadedException() {
        super("World already unloaded");
    }
}
