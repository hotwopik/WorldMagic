package io.hotwop.worldmagic.api.settings;

import io.hotwop.worldmagic.api.DimensionLike;
import io.hotwop.worldmagic.api.GameRuleSet;
import io.hotwop.worldmagic.util.ImmutableLocation;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CustomWorldSettings {
    public final NamespacedKey id;
    public final String bukkitId;
    public final String folder;

    public CustomWorldSettings(NamespacedKey id){
        this(id,id.namespace().equals(NamespacedKey.MINECRAFT)?id.value():id.namespace()+"_"+id.value());
    }

    public CustomWorldSettings(NamespacedKey id, String bukkitId){
        this(id,bukkitId,bukkitId);
    }

    public CustomWorldSettings(NamespacedKey id, String bukkitId, String folder){
        this.id=id;
        this.bukkitId=bukkitId;
        this.folder=folder;
    }

    private AllowSettings allowSettings=null;
    public void setAllowSettings(AllowSettings allowSettings){
        Objects.requireNonNull(allowSettings,"allowSettings");
        this.allowSettings=allowSettings;
    }
    public @Nullable AllowSettings allowSettings(){
        return allowSettings;
    }

    private Loading loading=null;
    public void setLoadingSettings(Loading loading){
        Objects.requireNonNull(loading,"loading");
        this.loading=loading;
    }
    public @Nullable Loading loadingSettings(){
        return loading;
    }

    private WorldProperties worldProperties=null;
    public void setWorldProperties(WorldProperties properties){
        Objects.requireNonNull(properties,"properties");
        this.worldProperties=properties;
    }
    public @Nullable WorldProperties worldProperties(){
        return worldProperties;
    }

    private WorldBorderSettings border=null;
    public void setWorldBorderSettings(WorldBorderSettings border){
        Objects.requireNonNull(border,"border");
        this.border=border;
    }
    public @Nullable WorldBorderSettings worldBorderSettings(){
        return border;
    }

    private SpawnPosition spawn=null;
    public void setSpawn(SpawnPosition spawn){
        Objects.requireNonNull(spawn,"spawn");
        this.spawn=spawn;
    }
    public @Nullable SpawnPosition spawn(){
        return spawn;
    }

    private boolean gameRuleOverride=false;
    private final GameRuleSet gameRuleSet =new GameRuleSet();

    public GameRuleSet gameRuleSet(){
        return gameRuleSet;
    }

    public void setGameRuleOverride(boolean override){
        gameRuleOverride=override;
    }
    public boolean isGameRuleOverride(){
        return gameRuleOverride;
    }

    private Location callbackLocation=null;

    public void setCallbackLocation(Location location){
        Objects.requireNonNull(location,"location");
        callbackLocation=new ImmutableLocation(callbackLocation);
    }
    public @Nullable Location callbackLocation(){
        return callbackLocation;
    }

    private DimensionLike dimension=DimensionLike.createFromReference(NamespacedKey.minecraft("overworld"));
    public void setDimension(DimensionLike dimension){
        Objects.requireNonNull(dimension,"dimension");
        this.dimension=dimension;
    }
    public DimensionLike dimension(){
        return dimension;
    }
}
