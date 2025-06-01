package io.hotwop.worldmagic.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public class ImmutableLocation extends Location {
    public ImmutableLocation(World world, double x, double y, double z) {
        super(world,x,y,z);
    }
    public ImmutableLocation(World world,double x,double y,double z,float yaw,float pitch) {
        super(world,x,y,z,yaw,pitch);
    }
    public ImmutableLocation(Location location) {
        super(location.getWorld(),location.getX(),location.getY(),location.getZ(),location.getYaw(),location.getPitch());
    }

    public void setWorld(World world){
        throw new UnsupportedOperationException("Location is immutable");
    }
    public void setX(double x){
        throw new UnsupportedOperationException("Location is immutable");
    }
    public void setY(double y){
        throw new UnsupportedOperationException("Location is immutable");
    }
    public void setZ(double z){
        throw new UnsupportedOperationException("Location is immutable");
    }
    public void setYaw(float yaw){
        throw new UnsupportedOperationException("Location is immutable");
    }
    public void setPitch(float pitch){
        throw new UnsupportedOperationException("Location is immutable");
    }

    @Override
    public boolean equals(Object obj) {
        if(obj==null)return false;
        if(obj==this)return true;
        if(!(obj instanceof Location other))return false;

        return Objects.equals(getWorld(),other.getWorld())
            &&Double.doubleToLongBits(getX()) == Double.doubleToLongBits(other.getX())
            &&Double.doubleToLongBits(getY()) == Double.doubleToLongBits(other.getY())
            &&Double.doubleToLongBits(getZ()) == Double.doubleToLongBits(other.getZ())
            &&Float.floatToIntBits(getPitch()) == Float.floatToIntBits(other.getPitch())
            &&Float.floatToIntBits(getYaw()) == Float.floatToIntBits(other.getYaw());
    }
}
