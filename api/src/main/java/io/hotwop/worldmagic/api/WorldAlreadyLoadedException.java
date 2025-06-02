package io.hotwop.worldmagic.api;

public final class WorldAlreadyLoadedException extends RuntimeException{
    public WorldAlreadyLoadedException() {
        super("World already loaded");
    }
}
