package io.hotwop.worldmagic.generation;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import io.hotwop.worldmagic.util.dfu.ConfigurateOps;
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

    public GameRuleFactory(boolean override,DynamicLike<?> gameRuleTag){
        this.gameRules=new GameRules(gameRuleTag);
        this.override=override;
    }

    public static final class Serializer implements TypeSerializer<GameRuleFactory>{
        public static final Serializer instance=new Serializer();
        private Serializer(){}

        public GameRuleFactory deserialize(@NotNull Type type, @NotNull ConfigurationNode node) throws SerializationException {
            boolean override=node.node("override").getBoolean();

            return new GameRuleFactory(override,new Dynamic<>(ConfigurateOps.instance(),node));
        }

        public void serialize(@NotNull Type type, @Nullable GameRuleFactory obj, @NotNull ConfigurationNode node) throws SerializationException {
            if(obj==null)return;
            if(obj.override)node.node("override").set(true);

            ConfigurationNode gameRuleNode=NbtOps.INSTANCE.convertTo(ConfigurateOps.instance(),obj.gameRules.createTag());
            node.from(gameRuleNode);
        }
    }
}
