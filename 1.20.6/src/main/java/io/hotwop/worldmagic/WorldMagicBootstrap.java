package io.hotwop.worldmagic;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.command.BorderSubCommand;
import io.hotwop.worldmagic.generation.CustomWorld;
import io.hotwop.worldmagic.util.Weather;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.PaperCommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

public final class WorldMagicBootstrap implements PluginBootstrap{
    private static ComponentLogger logger;
    public static ComponentLogger logger(){
        return logger;
    }

    public void bootstrap(@NotNull BootstrapContext ctx) {
        logger=ctx.getLogger();

        LifecycleEventManager<BootstrapContext> manager=ctx.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS,e->{
            Commands commands=e.registrar();

            commands.register(buildCommand(), List.of("wm"));
        });
    }

    public static final DynamicCommandExceptionType unknownWorldException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" not exist or isn't WorldMagic world"));
    public static final DynamicCommandExceptionType notLoadControlWorldException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" load control is disabled"));

    public static final DynamicCommandExceptionType worldAlreadyLoadedException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" already loaded"));
    public static final DynamicCommandExceptionType worldAlreadyUnloadedException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" already unloaded"));

    public static final DynamicCommandExceptionType worldNotLoaded=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" isn't loaded"));

    private LiteralCommandNode<CommandSourceStack> buildCommand(){
        return Commands.literal("worldmagic")
            .requires(ctx->ctx.getSender().hasPermission("worldmagic.command"))
            .then(Commands.argument("world", ArgumentTypes.namespacedKey())
                .suggests((ctx,builder)->{
                    String input=builder.getRemaining();

                    if(input.isEmpty())WorldMagic.getPluginWorlds()
                        .forEach(wr->builder.suggest(wr.id.asString()));
                    else WorldMagic.getPluginWorlds().stream()
                        .filter(wr->wr.id.asString().startsWith(input)||wr.id.asMinimalString().startsWith(input))
                        .forEach(wr->builder.suggest(wr.id.asString()));

                    return builder.buildFuture();
                })
                .then(Commands.literal("load").executes(ctx->{
                    CustomWorld world=getWorld(ctx);

                    if(!world.loading.loadControl())throw notLoadControlWorldException.create(world.id.asString());
                    if(world.loaded())throw worldAlreadyLoadedException.create(world.id.asString());

                    world.load();
                    ctx.getSource().getSender().sendMessage("Loading world...");
                    return 1;
                }))
                .then(Commands.literal("unload").executes(ctx->{
                    CustomWorld world=getWorld(ctx);

                    if(!world.loading.loadControl())throw notLoadControlWorldException.create(world.id.asString());
                    if(!world.loaded())throw worldAlreadyUnloadedException.create(world.id.asString());

                    world.unload();
                    ctx.getSource().getSender().sendMessage("Unloading world...");
                    return 1;
                }))
                .then(buildGameruleNode("gamerule",
                    (key,type,value,ctx)->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        String val=value.toString();
                        WorldGameRuleChangeEvent event=new WorldGameRuleChangeEvent(world.world(),ctx.getSource().getSender(), GameRule.getByName(key.getId()),val);
                        WorldMagic.pluginManager().callEvent(event);
                        if(event.isCancelled()){
                            ctx.getSource().getSender().sendMessage(net.kyori.adventure.text.Component.translatable("multiplayer.status.cancelled"));
                            return 0;
                        }
                        ServerLevel level=world.level();
                        GameRules.Value<?> rule=level.getGameRules().getRule(key);

                        rule.deserialize(val);
                        rule.onChanged(level);
                        ctx.getSource().getSender().sendMessage(net.kyori.adventure.text.Component.translatable("commands.gamerule.set").arguments(net.kyori.adventure.text.Component.text(key.getId()),net.kyori.adventure.text.Component.text(val)));
                        return 1;
                    },
                    (key,type,ctx)->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        String value=world.level().getGameRules().getRule(key).serialize();
                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.gamerule.query",key.getId(),value)));
                        return 1;
                    }
                ))
                .then(buildDifficultyNode("difficulty",
                    (diff,ctx)->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        WorldMagic.vanillaServer().setDifficulty(world.level(),diff,true);
                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.difficulty.success",diff.getDisplayName())));
                        return diff.getId()+1;
                    },
                    ctx->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        Difficulty value=world.level().getDifficulty();
                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.difficulty.query",value.getDisplayName())));
                        return value.getId();
                    }
                ))
                .then(buildWeatherNode("weather",
                    (weather,duration,ctx)->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        weather.apply(world.level(),duration);
                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(weather.getSetComponent()));
                        return duration;
                    },
                    ctx->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        Weather weather=Weather.query(world.level());
                        ctx.getSource().getSender().sendMessage("Weather in "+world.id.asString()+" is "+weather.name());
                        return weather.ordinal();
                    }
                ))
                .then(BorderSubCommand.buildNode())
                .then(Commands.literal("spawn").then(Commands.argument("pos",ArgumentTypes.blockPosition())
                    .executes(ctx->{
                        CustomWorld world=getWorld(ctx);
                        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                        BlockPosition pos=ctx.getArgument("pos",BlockPositionResolver.class).resolve(ctx.getSource());
                        world.world().setSpawnLocation(pos.blockX(),pos.blockY(),pos.blockZ());

                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.setworldspawn.success", pos.blockX(),pos.blockY(),pos.blockZ(), 0)));
                        return 1;
                    })
                    .then(Commands.argument("yaw",FloatArgumentType.floatArg(-180,180))
                        .executes(ctx->{
                            CustomWorld world=getWorld(ctx);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            BlockPosition pos=ctx.getArgument("pos",BlockPositionResolver.class).resolve(ctx.getSource());
                            float yaw=ctx.getArgument("yaw",Float.class);

                            world.world().setSpawnLocation(pos.blockX(),pos.blockY(),pos.blockZ(),yaw);
                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.setworldspawn.success", pos.blockX(),pos.blockY(),pos.blockZ(), yaw)));
                            return 1;
                        })
                    )
                ))
                .then(Commands.literal("move").then(Commands.argument("target",ArgumentTypes.entities())
                    .then(Commands.argument("pos",Vec3Argument.vec3(true))
                        .executes(ctx->move(false,world->getPos(world,ctx),ctx))
                        .then(Commands.argument("rotation", RotationArgument.rotation())
                            .executes(ctx->move(true,world->getPosAndRotation(world,ctx),ctx))
                        )
                    )
                    .then(Commands.literal("spawn")
                        .executes(ctx->move(true,World::getSpawnLocation,ctx))
                    )
                    .then(Commands.literal("here")
                        .executes(ctx->move(true,world->{
                            Location loc=ctx.getSource().getLocation().clone();
                            loc.setWorld(world);
                            return loc;
                        },ctx))
                    )
                ))
            )
            .build();
    }

    private static Location getPos(World world,CommandContext<CommandSourceStack> ctx){
        Coordinates coords=ctx.getArgument("pos", Coordinates.class);
        Vec3 vec=coords.getPosition((net.minecraft.commands.CommandSourceStack)ctx.getSource());
        return new Location(world,vec.x,vec.y,vec.z);
    }

    private static Location getPosAndRotation(World world,CommandContext<CommandSourceStack> ctx){
        Coordinates coords=ctx.getArgument("pos",Coordinates.class);
        Vec3 pos=coords.getPosition((net.minecraft.commands.CommandSourceStack)ctx.getSource());

        Coordinates rotation=ctx.getArgument("rotation",Coordinates.class);
        Vec2 rot=rotation.getRotation((net.minecraft.commands.CommandSourceStack)ctx.getSource());

        return new Location(world,pos.x,pos.y,pos.z,rot.x,rot.y);
    }

    private static int move(boolean rotation,Function<World,Location> getLocation,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException{
        CustomWorld world=getWorld(ctx);
        if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

        List<Entity> entities=ctx.getArgument("target",EntitySelectorArgumentResolver.class).resolve(ctx.getSource());

        Location location=getLocation.apply(world.world());
        entities.forEach(entity->{
            Location loc;
            if(rotation)loc=location;
            else{
                loc=location.clone();
                loc.setYaw(entity.getYaw());
                loc.setPitch(entity.getPitch());
            }

            entity.teleportAsync(loc,PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(success->{
                if(!success)entity.teleport(loc);
            });
        });

        if(entities.size()==1)ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.teleport.success.location.single",
            entities.getFirst().name(),
            String.format(Locale.ROOT, "%f", location.getX()),
            String.format(Locale.ROOT, "%f", location.getY()),
            String.format(Locale.ROOT, "%f", location.getZ())
        )));
        else ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.teleport.success.location.multiple",
            entities.size(),
            String.format(Locale.ROOT, "%f", location.getX()),
            String.format(Locale.ROOT, "%f", location.getY()),
            String.format(Locale.ROOT, "%f", location.getZ())
        )));

        return 1;
    }

    public static CustomWorld getWorld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException{
        NamespacedKey worldId=ctx.getArgument("world",NamespacedKey.class);
        CustomWorld world=WorldMagic.getPluginWorld(worldId);

        if(world==null)throw unknownWorldException.create(worldId.asString());
        return world;
    }

    public static LiteralCommandNode<CommandSourceStack> buildGameruleNode(String root, GameruleOperation value, GameruleQuery get) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(root);

        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
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
                .then(Commands.argument("duration",ArgumentTypes.time(1))
                    .executes(ctx->op.consume(weather,ctx.getArgument("duration",Integer.class),ctx))
                )
            );
        }

        return builder.build();
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

    @FunctionalInterface
    public interface GameruleOperation{
        int consume(GameRules.Key<?> key,GameRules.Type<?> type,Object value,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
    @FunctionalInterface
    public interface GameruleQuery{
        int consume(GameRules.Key<?> key,GameRules.Type<?> type,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface DifficultyOperation{
        int consume(Difficulty value,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
    @FunctionalInterface
    public interface WeatherOperation{
        int consume(Weather value,int duration,CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    @FunctionalInterface
    public interface QueryOperation{
        int consume(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }
}
