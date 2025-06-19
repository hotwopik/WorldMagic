package io.hotwop.worldmagic.api;

/**
 * Exception that appears if world can't be loaded
 */
public class WorldLoadException extends RuntimeException{
    /**
     * Exception that appears if world can't be loaded
     * @param message exception message
     */
    public WorldLoadException(String message) {
        super(message);
    }
}
