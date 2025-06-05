package io.hotwop.worldmagic.api.settings;

import io.hotwop.worldmagic.api.DimensionLike;
import io.hotwop.worldmagic.api.GameRuleSet;
import io.hotwop.worldmagic.api.IncorrectImplementationException;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Plugin world settings
 */
public final class CustomWorldSettings{
    /**
     * Vanilla world id
     */
    public final NamespacedKey id;

    /**
     * Bukkit world id
     */
    public final String bukkitId;

    /**
     * World folder path
     */
    public final String folder;

    /**
     * Create CustomWorldSettings using vanilla id.
     *
     * <p>If vanilla id namespace is <b><i>minecraft</i></b> bukkit id resolves to vanilla id value.
     * Else bukkit id resolves to <i><b>namespace</b>_<b>value</b></i></p>
     *
     * <p>Folder path resolves to bukkit id</p>
     *
     * @param id vanilla id
     */
    public CustomWorldSettings(NamespacedKey id){
        this(id,id.namespace().equals(NamespacedKey.MINECRAFT)?id.value():id.namespace()+"_"+id.value());
    }

    /**
     * Create CustomWorldSettings via separated vanilla id and bukkit id.
     *
     * <p>Folder path resolves to bukkit id</p>
     *
     * @param id vanilla id
     * @param bukkitId bukkit id
     */
    public CustomWorldSettings(NamespacedKey id,String bukkitId){
        this(id,bukkitId,bukkitId);
    }

    /**
     * Create CustomWorldSettings via separated vanilla id, bukkit id and folder path.
     *
     * @param id vanilla id
     * @param bukkitId bukkit id
     * @param folder world folder path
     */
    public CustomWorldSettings(NamespacedKey id,String bukkitId,String folder){
        this.id=id;
        this.bukkitId=bukkitId;
        this.folder=folder;
    }

    private AllowSettings allowSettings=null;

    /**
     * Set what allowed in the world
     * @param allowSettings settings
     */
    public void setAllowSettings(AllowSettings allowSettings){
        Objects.requireNonNull(allowSettings,"allowSettings");
        this.allowSettings=allowSettings;
    }

    /**
     * Get allow settings
     * @return What allowed in the world
     */
    public @Nullable AllowSettings allowSettings(){
        return allowSettings;
    }

    private Loading loading=null;

    /**
     * Set world loading settings
     * @param loading settings
     */
    public void setLoadingSettings(Loading loading){
        Objects.requireNonNull(loading,"loading");
        this.loading=loading;
    }

    /**
     * Get loading settings
     * @return Loading settings
     */
    public @Nullable Loading loadingSettings(){
        return loading;
    }

    private WorldProperties worldProperties=null;

    /**
     * Set some world properties
     * @param properties world properties
     */
    public void setWorldProperties(WorldProperties properties){
        Objects.requireNonNull(properties,"properties");
        this.worldProperties=properties;
    }

    /**
     * Get world properties
     * @return world properties
     */
    public @Nullable WorldProperties worldProperties(){
        return worldProperties;
    }

    private WorldBorderSettings border=null;

    /**
     * Setup world border
     * @param border world border settings
     */
    public void setWorldBorderSettings(WorldBorderSettings border){
        Objects.requireNonNull(border,"border");
        this.border=border;
    }

    /**
     * Get world border settings
     * @return world border settings
     */
    public @Nullable WorldBorderSettings worldBorderSettings(){
        return border;
    }

    private SpawnPosition spawn=null;

    /**
     * Setup world spawn
     * @param spawn spawn settings
     */
    public void setSpawn(SpawnPosition spawn){
        Objects.requireNonNull(spawn,"spawn");
        this.spawn=spawn;
    }

    /**
     * Get world spawn settings
     * @return spawn settings
     */
    public @Nullable SpawnPosition spawn(){
        return spawn;
    }

    private boolean gameRuleOverride=false;
    private final GameRuleSet gameRuleSet=new GameRuleSet();

    /**
     * World configurable gamerules
     * @return World gamerules set
     */
    public GameRuleSet gameRuleSet(){
        return gameRuleSet;
    }

    /**
     * Setting up should plugin override already existing gamerules in the world
     * @param override whether should or not
     */
    public void setGameRuleOverride(boolean override){
        gameRuleOverride=override;
    }

    /**
     * Get gamerule override
     * @return gamerule override setting
     */
    public boolean isGameRuleOverride(){
        return gameRuleOverride;
    }

    private Location callbackLocation=null;

    /**
     * Setup location to return players if world unloads
     * <p>Default to overworld spawn</p>
     *
     * @param location callback location
     */
    @Contract(mutates="this")
    public void setCallbackLocation(Location location){throw new IncorrectImplementationException();}

    /**
     * Get callback location
     * @return callback location or null if it's overworld spawn
     */
    public @Nullable Location callbackLocation(){
        return callbackLocation;
    }

    private DimensionLike dimension=DimensionLike.createFromReference(NamespacedKey.minecraft("overworld"));

    /**
     * Setup dimension settings in the world
     * @param dimension settings
     */
    public void setDimension(DimensionLike dimension){
        Objects.requireNonNull(dimension,"dimension");
        this.dimension=dimension;
    }

    /**
     * Get dimension settings
     * @return dimension settings
     */
    public DimensionLike dimension(){
        return dimension;
    }
}
