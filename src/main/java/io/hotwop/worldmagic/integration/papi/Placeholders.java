package io.hotwop.worldmagic.integration.papi;

import io.hotwop.worldmagic.CustomWorld;
import io.hotwop.worldmagic.WorldMagic;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class Placeholders extends PlaceholderExpansion {
    @Override
    public @NotNull String getIdentifier() {
        return "worldmagic";
    }

    @Override
    public @NotNull String getAuthor() {
        return "hotwop";
    }

    @Override
    public @NotNull String getVersion() {
        return WorldMagic.instance().getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders(){
        return List.of(
            "worldmagic_current_id",
            "worldmagic_current_name",
            "worldmagic_current_folder",
            "worldmagic_current_permission",
            "worldmagic_current_save",
            "worldmagic_current_pvp",
            "worldmagic_world_<world id>_name",
            "worldmagic_world_<world id>_folder",
            "worldmagic_world_<world id>_permission",
            "worldmagic_world_<world id>_save",
            "worldmagic_world_<world id>_pvp"
        );
    }

    private static final List<PlaceholderResolver> placeholders=List.of(
        new WorldResolver("name",Placeholders::getName),
        new WorldResolver("folder",Placeholders::getFolder),
        new WorldResolver("permission",Placeholders::getPermission),
        new WorldResolver("save",Placeholders::getSave),
        new WorldResolver("pvp",Placeholders::getPvp)
    );

    @Override
    public @Nullable String onRequest(OfflinePlayer ofl, @NotNull String params){
        Player online=ofl==null?null:ofl.getPlayer();

        switch(params){
            case "current_id"->{
                return checkPublicWorld(online,Placeholders::getId);
            }
            case "current_name"->{
                return checkPublicWorld(online,Placeholders::getName);
            }
            case "current_folder"->{
                return checkPublicWorld(online,Placeholders::getFolder);
            }
            case "current_permission"->{
                return checkPublicWorld(online,Placeholders::getPermission);
            }
            case "current_save"->{
                return checkPublicWorld(online,Placeholders::getSave);
            }
            case "current_pvp"->{
                return checkPublicWorld(online,Placeholders::getPvp);
            }
        }

        for(PlaceholderResolver resolver:placeholders){
            String out=resolver.check(params);
            if(out!=null)return out;
        }

        return null;
    }

    private static String checkPublicWorld(Player pl, Function<CustomWorld,String> consumer){
        if(pl!=null)return consumer.apply(WorldMagic.isPluginWorld(pl.getWorld()));
        return "";
    }

    private static String getId(CustomWorld cw){
        if(cw!=null)return cw.id.asString();
        return "";
    }

    private static String getName(CustomWorld cw){
        if(cw!=null)return cw.bukkitId;
        return "";
    }

    private static String getFolder(CustomWorld cw){
        if(cw!=null)return cw.folder;
        return "";
    }

    private static String getPermission(CustomWorld cw){
        if(cw!=null){
            String perm=cw.worldProperties.requiredPermission();
            return perm==null?"none":perm;
        }
        return "";
    }

    private static String getSave(CustomWorld cw){
        if(cw!=null)return cw.loading.save()?"1":"0";
        return "";
    }

    private static String getPvp(CustomWorld cw){
        if(cw!=null)return cw.allowSettings.pvp()?"1":"0";
        return "";
    }
}
