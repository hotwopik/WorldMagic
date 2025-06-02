package io.hotwop.worldmagic;

public class WorldCreationException extends Exception {
    public final Phase phase;

    public WorldCreationException(String message,Phase phase) {
        super(message);
        this.phase=phase;
    }

    public String getMessage(){
        return phase.name()+" phase error: "+super.getMessage();
    }

    public enum Phase{
        check,
        build,
        load
    }
}
