package io.hotwop.worldmagic.api;

/**
 * Exception that appears if world can't be unloaded
 */
public class WorldUnloadException extends RuntimeException{
    /**
     * Exception that appears if world can't be unloaded
     */
    public WorldUnloadException(String message) {
        super(message);
    }
}
