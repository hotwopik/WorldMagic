package io.hotwop.worldmagic.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.util.VersionUtil;
import io.hotwop.worldmagic.util.Weather;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.PaperCommands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.function.Supplier;

public final class NodeBuilders{
    private NodeBuilders(){}

    public static LiteralCommandNode<CommandSourceStack> buildGameruleNode(String root, GameruleOperation value, GameruleQuery get, Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(root);

        VersionUtil.visitGameRules(VersionUtil.createGameRulesFromContext(((PaperCommands)commands).getBuildContext()),new GameRules.GameRuleTypeVisitor(){
            public <T extends GameRules.Value<T>> void visit(GameRules.@NotNull Key<T> key, GameRules.@NotNull Type<T> type) {
                builder.then(Commands.literal(key.getId())
                    .executes(ctx -> get.consume(key, type, ctx))
                    .then(Commands.argument("value", getGameruleArgument(type))
                        .executes(ctx -> {
                            Object val = ctx.getArgument("value", Object.class);
                            return value.consume(key, type, val, ctx);
                        })
                    )
                );
            }
        });

        return builder.build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildDifficultyNode(String root, DifficultyOperation op, QueryOperation query){
        LiteralArgumentBuilder<CommandSourceStack> builder=Commands.literal(root).executes(query::consume);

        for(Difficulty diff:Difficulty.values()){
            builder.then(Commands.literal(diff.getKey()).executes(ctx->op.consume(diff,ctx)));
        }

        return builder.build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildWeatherNode(String root, WeatherOperation op, QueryOperation query){
        LiteralArgumentBuilder<CommandSourceStack> builder=Commands.literal(root).executes(query::consume);

        for(Weather weather:Weather.values()){
            builder.then(Commands.literal(weather.id)
                .executes(ctx->op.consume(weather,-1,ctx))
                .then(Commands.argument("duration", ArgumentTypes.time(1))
                    .executes(ctx->op.consume(weather,ctx.getArgument("duration",Integer.class),ctx))
                )
            );
        }

        return builder.build();
    }

    public static LiteralCommandNode<CommandSourceStack> buildSetTimeNode(String root,SetTimeOperation op){
        LiteralArgumentBuilder<CommandSourceStack> builder=Commands.literal(root)
            .then(Commands.argument("value",ArgumentTypes.time(0))
                .executes(ctx->op.consume(ctx.getArgument("value",Integer.class),ctx))
            );

        for(SetTimeTimes time:SetTimeTimes.values()){
            builder.then(Commands.literal(time.name())
                .executes(ctx->op.consume(time.time,ctx))
            );
        }

        return builder.build();
    }

    public enum SetTimeTimes{
        day(1000),
        noon(6000),
        night(13000),
        midnight(18000);

        public final int time;
        SetTimeTimes(int time){
            this.time=time;
        }
    }

    private static final Field argumentSupplerField;

    static{
        try{
            argumentSupplerField=GameRules.Type.class.getDeclaredField("argument");
        }catch(NoSuchFieldException e){
            throw new RuntimeException(e);
        }

        argumentSupplerField.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends GameRules.Value<T>> ArgumentType<?> getGameruleArgument(GameRules.Type<T> type){
        Supplier<ArgumentType<?>> supp;
        try{
            supp=(Supplier<ArgumentType<?>>)argumentSupplerField.get(type);
        } catch(IllegalAccessException e){
            throw new RuntimeException(e);
        }
        return supp.get();
    }

    public static NamespacedKey readNamespacedKey(StringReader reader) throws CommandSyntaxException{
        int i = reader.getCursor();

        while (reader.canRead() && ResourceLocation.isAllowedInResourceLocation(reader.peek())) {
            reader.skip();
        }

        String str=reader.getString().substring(i, reader.getCursor());

        NamespacedKey out=NamespacedKey.fromString(str);
        if(out==null){
            reader.setCursor(i);
            throw ResourceLocation.ERROR_INVALID.createWithContext(reader);
        }

        return out;
    }

    @FunctionalInterface
    public interface GameruleOperation{
        int consume(GameRules.Key<?> key, GameRules.Type<?> type, Object value, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
    @FunctionalInterface
    public interface GameruleQuery{
        int consume(GameRules.Key<?> key,GameRules.Type<?> type,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface DifficultyOperation{
        int consume(Difficulty value, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
    @FunctionalInterface
    public interface WeatherOperation{
        int consume(Weather value, int duration, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
    @FunctionalInterface
    public interface SetTimeOperation{
        int consume(int time,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface QueryOperation{
        int consume(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
}
