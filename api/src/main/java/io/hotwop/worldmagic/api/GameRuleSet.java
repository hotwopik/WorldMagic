package io.hotwop.worldmagic.api;

import org.bukkit.GameRule;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gamerule storage
 */
public final class GameRuleSet {
    private final Map<GameRule<?>,Object> map=new HashMap<>();

    /**
     * Gamerule storage
     */
    public GameRuleSet(){}

    /**
     * Set gamerule value
     *
     * @param gameRule gamerule
     * @param value value
     * @param <T> gamerule type
     */
    public <T> void set(GameRule<T> gameRule,T value){
        map.put(gameRule,value);
    }

    /**
     * Get gamerule value
     *
     * @param gameRule gamerule
     * @return value
     * @param <T> gamerule type
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T query(GameRule<T> gameRule){
        return (T)map.get(gameRule);
    }

    /**
     * Build list of gamerule statements
     *
     * @return List of gamerule statements
     */
    @Unmodifiable
    @SuppressWarnings("unchecked")
    public List<GameRuleStatement<?>> getStatements(){
        List<GameRuleStatement<?>> out=new ArrayList<>();
        map.forEach((rule,obj)->out.add(new GameRuleStatement<>((GameRule<Object>)rule,obj)));
        return List.copyOf(out);
    }

    /**
     * Pair gamerule and value
     *
     * @param gameRule gamerule
     * @param value value
     * @param <T> gamerule type
     */
    public record GameRuleStatement<T>(
        GameRule<T> gameRule,
        T value
    ){}
}