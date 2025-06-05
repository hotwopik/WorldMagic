package io.hotwop.worldmagic.api;

/**
 * Exception that appears if world already unloaded
 */
public final class WorldAlreadyUnloadedException extends RuntimeException{
    /**
     * Exception that appears if world already unloaded
     */
    public WorldAlreadyUnloadedException() {
        super("World already unloaded");
    }
}
