package io.hotwop.worldmagic.api.settings;

/**
 * Setting up loading settings
 *
 * @param async Enable async loading mode
 * @param loadChunks Should plugin load spawn chunks immediately(Chunks still loads in some ticks after world load)
 * @param save Should plugin save world
 * @param folderDeletion Should plugin delete world folder after unload
 * @param loadControl Should players able to unload world via command
 */
public record Loading(
    boolean async,
    boolean loadChunks,
    boolean save,
    boolean folderDeletion,
    boolean loadControl
){}
