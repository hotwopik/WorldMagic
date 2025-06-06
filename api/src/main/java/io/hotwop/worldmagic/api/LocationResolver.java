package io.hotwop.worldmagic.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/**
 * Interface to perform location requests
 */
public sealed interface LocationResolver permits LocationResolver.RuntimeResolver, LocationResolver.AlreadyResolver{
    /**
     * Location request
     *
     * @return location
     * @throws ResolveException if can't resolve
     */
    Location resolve() throws ResolveException;

    /**
     * Make resolver from world id, x, y and z
     *
     * @param world world id
     * @param x x position
     * @param y y position
     * @param z z position
     * @return resolver
     */
    static LocationResolver resolver(NamespacedKey world,double x,double y,double z){
        return new RuntimeResolver(world,x,y,z,0,0);
    }

    /**
     * Make resolver from world id, x, y, z, yaw and pitch
     *
     * @param world world id
     * @param x x position
     * @param y y position
     * @param z z position
     * @param yaw position yaw
     * @param pitch position pitch
     * @return resolver
     */
    static LocationResolver resolver(NamespacedKey world,double x,double y,double z,float yaw,float pitch){
        return new RuntimeResolver(world,x,y,z,yaw,pitch);
    }

    /**
     * Make resolver from location
     *
     * @param location location
     * @return resolver
     */
    static LocationResolver resolver(Location location){
        return new AlreadyResolver(location);
    }

    /**
     * Class that resolves location for request
     */
    final class RuntimeResolver implements LocationResolver{
        /**
         * World id
         */
        public final NamespacedKey world;
        /**
         * Position x
         */
        public final double x;
        /**
         * Position y
         */
        public final double y;
        /**
         * Position z
         */
        public final double z;
        /**
         * Position yaw
         */
        public final float yaw;
        /**
         * Position pitch
         */
        public final float pitch;

        private RuntimeResolver(NamespacedKey world, double x, double y, double z, float yaw, float pitch){
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public Location resolve() throws ResolveException{
            World wd=Bukkit.getWorld(world);
            if(wd==null)throw new ResolveException();

            return new Location(wd,x,y,z,yaw,pitch);
        }
    }

    /**
     * Class that resolves already existed location
     */
    final class AlreadyResolver implements LocationResolver{
        /**
         * Existed location
         */
        public final Location location;

        private AlreadyResolver(Location location){
            this.location=location.clone();
        }

        @Override
        public Location resolve() {
            return location;
        }
    }

    /**
     * Location resolve exception
     */
    final class ResolveException extends Exception{
        /**
         * Location resolve exception
         */
        public ResolveException(){
            super("Error to resolve location");
        }
    }
}
