package io.hotwop.worldmagic.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;

import java.util.function.BiConsumer;

public enum Weather{
    clear("clear",(level,duration)->level.setWeatherParameters(getDuration(level,duration,ServerLevel.RAIN_DELAY),0,false,false)),
    raining("rain",(level,duration)->level.setWeatherParameters(0,getDuration(level,duration,ServerLevel.RAIN_DURATION),true,false)),
    thundering("thunder",(level,duration)->level.setWeatherParameters(0,getDuration(level,duration,ServerLevel.THUNDER_DURATION),true,true));

    public final String id;
    private final BiConsumer<ServerLevel,Integer> applier;

    Weather(String id,BiConsumer<ServerLevel,Integer> applier){
        this.id=id;
        this.applier=applier;
    }

    public Component getSetComponent(){
        return Component.translatable("commands.weather.set."+id);
    }
    public void apply(ServerLevel level, int duration){
        applier.accept(level,duration);
    }

    public static Weather query(ServerLevel level){
        if(level.isRaining()){
            if(level.isThundering())return thundering;
            else return raining;
        }else return clear;
    }

    private static int getDuration(ServerLevel level, int duration, IntProvider provider) {
        return duration == -1 ? provider.sample(level.getRandom()) : duration;
    }
}
