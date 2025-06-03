package io.hotwop.worldmagic.generation;

import io.hotwop.worldmagic.util.VersionUtil;
import org.spongepowered.configurate.extra.dfu.v7.ConfigurateOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.GameRules;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;

public final class GameRuleFactory{
    public final GameRules gameRules;
    public final boolean override;

    public GameRuleFactory(boolean override,GameRules rules){
        this.gameRules=rules;
        this.override=override;
    }

    public static final class Serializer implements TypeSerializer<GameRuleFactory>{
        public static final Serializer instance=new Serializer();
        private Serializer(){}

        public GameRuleFactory deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
            boolean override=node.node("override").getBoolean();

            GameRules rules= VersionUtil.createGameRules();
            VersionUtil.visitGameRules(rules, new GameRules.GameRuleTypeVisitor() {
                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                    ConfigurationNode subNode=node.node(key.getId());
                    if(!subNode.virtual())rules.getRule(key).set(subNode.getBoolean(),null);
                }
                @Override
                public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                    ConfigurationNode subNode=node.node(key.getId());
                    if(!subNode.virtual())rules.getRule(key).set(subNode.getInt(),null);
                }
            });

            return new GameRuleFactory(override,rules);
        }

        public void serialize(@NotNull Type type, @Nullable GameRuleFactory obj, @NotNull ConfigurationNode node) throws SerializationException {
            if(obj==null)return;
            if(obj.override)node.node("override").set(true);

            ConfigurationNode gameRuleNode=NbtOps.INSTANCE.convertTo(ConfigurateOps.instance(),obj.gameRules.createTag());
            node.from(gameRuleNode);
        }
    }
}
