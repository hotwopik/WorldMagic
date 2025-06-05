package io.hotwop.worldmagic.api;

/**
 * Exception that appears if world already loaded
 */
public final class WorldAlreadyLoadedException extends RuntimeException{
    /**
     * Exception that appears if world already loaded
     */
    public WorldAlreadyLoadedException() {
        super("World already loaded");
    }
}
