package io.hotwop.worldmagic.file;

import io.hotwop.worldmagic.api.GameRuleSet;
import io.hotwop.worldmagic.api.settings.*;
import io.hotwop.worldmagic.generation.GameRuleFactory;
import io.hotwop.worldmagic.util.VersionUtil;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FileUtil{
    private FileUtil(){}

    public static WorldBorder.Settings buildWorldBorder(WorldBorderSettings plugin){
        WorldBorder handle=new WorldBorder();

        handle.setSize(plugin.size());
        handle.setDamageSafeZone(plugin.safeZone());
        handle.setDamagePerBlock(plugin.damagePerBlock());
        handle.setCenter(plugin.centerX(),plugin.centerZ());
        handle.setWarningBlocks(plugin.warningDistance());
        handle.setWarningTime(plugin.warningTime());

        return handle.createSettings();
    }

    public static AllowSettings fromFile(WorldFile.AllowSettings file){
        return new AllowSettings(file.animals,file.monsters,file.pvp);
    }

    public static Loading fromFile(WorldFile.Loading file){
        return new Loading(file.async,file.override,file.loadChunks,file.save,file.folderDeletion,file.loadControl);
    }

    public static SpawnPosition fromFile(WorldFile.SpawnPosition file){
        return new SpawnPosition(file.override,file.x,file.y,file.z,file.yaw);
    }

    public static WorldBorderSettings fromFile(WorldFile.WorldBorderSettings file){
        return new WorldBorderSettings(file.override,file.size,file.safeZone,file.damagePerBlock,file.center.x,file.center.z,file.warning.distance,file.warning.time);
    }

    public static WorldProperties fromFile(WorldFile.WorldProperties file){
        return new WorldProperties(file.seed,file.generateStructures,file.bonusChest,file.defaultGamemode,file.forceDefaultGamemode,file.difficulty,file.requiredPermission);
    }

    public static GameRuleFactory toFactory(GameRuleSet set,boolean override){
        List<GameRuleSet.GameRuleStatement<?>> statements=set.getStatements();
        GameRules out=VersionUtil.createGameRules();

        VersionUtil.visitGameRules(out, new GameRules.GameRuleTypeVisitor() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends GameRules.Value<T>> void visit(GameRules.@NotNull Key<T> key, GameRules.@NotNull Type<T> type) {
                String id=key.getId();

                statements.stream()
                    .filter(st->st.gameRule().getName().equals(id)).findAny()
                    .ifPresent(st->out.getRule(key).setFrom((T)st.value(),null));
            }
        });

        return new GameRuleFactory(override,out);
    }

    public static void fromFactory(GameRuleSet out,GameRuleFactory factory){
        VersionUtil.visitGameRules(factory.gameRules, new GameRules.GameRuleTypeVisitor() {
            @Override
            @SuppressWarnings("unchecked")
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                int value=factory.gameRules.getInt(key);
                if(type.createRule().get()==value)return;

                out.set((GameRule<Integer>)GameRule.getByName(key.getId()),value);
            }
            @Override
            @SuppressWarnings("unchecked")
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                boolean value=factory.gameRules.getBoolean(key);
                if(type.createRule().get()==value)return;

                out.set((GameRule<Boolean>)GameRule.getByName(key.getId()),value);
            }
        });
    }

    public static net.minecraft.world.Difficulty mapDifficulty(Difficulty difficulty){
        switch(difficulty){
            case PEACEFUL->{return net.minecraft.world.Difficulty.PEACEFUL;}
            case EASY->{return net.minecraft.world.Difficulty.EASY;}
            case NORMAL->{return net.minecraft.world.Difficulty.NORMAL;}
            case HARD->{return net.minecraft.world.Difficulty.HARD;}
            default->throw new RuntimeException();
        }
    }

    public static GameType mapGameMode(GameMode gamemode){
        switch(gamemode){
            case SURVIVAL->{return GameType.SURVIVAL;}
            case CREATIVE->{return GameType.CREATIVE;}
            case ADVENTURE->{return GameType.ADVENTURE;}
            case SPECTATOR->{return GameType.SPECTATOR;}
            default->throw new RuntimeException();
        }
    }
}
