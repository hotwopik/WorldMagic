package io.hotwop.worldmagic;

/**
 * Exception in world creation process
 */
public class WorldCreationException extends Exception {
    /**
     * Phase in which happen exception
     */
    public final Phase phase;

    /**
     * Exception in world creation process
     * @param message exception message
     * @param phase in which phase its happen
     */
    public WorldCreationException(String message,Phase phase) {
        super(message);
        this.phase=phase;
    }

    public String getMessage(){
        return phase.name()+" phase error: "+super.getMessage();
    }

    /**
     * World creation phase
     */
    public enum Phase{
        /**
         * Phase where plugin check that all right
         */
        check,
        /**
         * Phase where plugin prepare world loading
         */
        build,
        /**
         * Phase where plugin load world
         */
        load
    }
}
