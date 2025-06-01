package io.hotwop.worldmagic;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.command.*;
import io.hotwop.worldmagic.file.WorldFile;
import io.hotwop.worldmagic.util.Util;
import io.hotwop.worldmagic.util.Weather;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.event.world.WorldGameRuleChangeEvent;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import org.bukkit.*;
import org.bukkit.event.world.TimeSkipEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class WorldMagicBootstrap implements PluginBootstrap{
    private static ComponentLogger logger;
    public static ComponentLogger logger(){
        return logger;
    }

    public void bootstrap(@NotNull BootstrapContext ctx) {
        logger=ctx.getLogger();

        if(SharedConstants.getCurrentVersion().getDataVersion().getVersion()<3839){
            throw new RuntimeException("Versions under 1.20.6 are unsupported");
        }

        try{
            WorldMagicBootstrap.class.getClassLoader().loadClass(Util.class.getName());
        }catch(ClassNotFoundException e){
            throw new RuntimeException(e);
        }

        LifecycleEventManager<BootstrapContext> manager=ctx.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS,e->{
            Commands commands=e.registrar();

            commands.register(buildCommand(), List.of("wm"));
        });
    }

    public static final DynamicCommandExceptionType notLoadControlWorldException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" load control is disabled"));

    public static final DynamicCommandExceptionType worldAlreadyLoadedException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" already loaded"));
    public static final DynamicCommandExceptionType worldAlreadyUnloadedException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" already unloaded"));

    public static final DynamicCommandExceptionType worldNotLoaded=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" isn't loaded"));

    public static final DynamicCommandExceptionType worldCreationError=new DynamicCommandExceptionType(obj->Component.literal("Error to create world: "+obj));

    private static final String[] globalPermissionSet={
        "worldmagic.command",
        "worldmagic.command.world",
        "worldmagic.command.create",
        "worldmagic.command.reload"
    };
    private static final String[] worldPermissionSet={
        "worldmagic.command",
        "worldmagic.command.world"
    };
    private static final String[] createPermissionSet={
        "worldmagic.command",
        "worldmagic.command.create"
    };
    private static final String[] reloadPermissionSet={
        "worldmagic.command",
        "worldmagic.command.reload"
    };

    private LiteralCommandNode<CommandSourceStack> buildCommand(){
        return Commands.literal("worldmagic")
            .requires(ctx->Util.hasOneOfPermission(ctx.getSender(),globalPermissionSet))
            .then(Commands.literal("world")
                .requires(ctx->Util.hasOneOfPermission(ctx.getSender(),worldPermissionSet))
                .then(Commands.argument("world",CustomWorldArgument.instance)
                    .then(Commands.literal("load").executes(ctx->{
                        CustomWorld world=ctx.getArgument("world",CustomWorld.class);

                        if(!world.loading.loadControl())throw notLoadControlWorldException.create(world.id.asString());
                        if(world.loaded())throw worldAlreadyLoadedException.create(world.id.asString());

                        world.load();
                        ctx.getSource().getSender().sendMessage("Loading world...");
                        return 1;
                    }))
                    .then(Commands.literal("unload").executes(ctx->{
                        CustomWorld world=ctx.getArgument("world", CustomWorld.class);

                        if(!world.loading.loadControl())throw notLoadControlWorldException.create(world.id.asString());
                        if(!world.loaded())throw worldAlreadyUnloadedException.create(world.id.asString());

                        world.unload();
                        ctx.getSource().getSender().sendMessage("Unloading world...");
                        return 1;
                    }))
                    .then(NodeBuilders.buildGameruleNode("gamerule",
                        (key,type,value,ctx)->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
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
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            String value=world.level().getGameRules().getRule(key).serialize();
                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.gamerule.query",key.getId(),value)));
                            return 1;
                        }
                    ))
                    .then(NodeBuilders.buildDifficultyNode("difficulty",
                        (diff,ctx)->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            WorldMagic.vanillaServer().setDifficulty(world.level(),diff,true);
                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.difficulty.success",diff.getDisplayName())));
                            return diff.getId()+1;
                        },
                        ctx->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            Difficulty value=world.level().getDifficulty();
                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.difficulty.query",value.getDisplayName())));
                            return value.getId();
                        }
                    ))
                    .then(NodeBuilders.buildWeatherNode("weather",
                        (weather,duration,ctx)->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            weather.apply(world.level(),duration);
                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(weather.getSetComponent()));
                            return duration;
                        },
                        ctx->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            Weather weather=Weather.query(world.level());
                            ctx.getSource().getSender().sendMessage("Weather in "+world.id.asString()+" is "+weather.name());
                            return weather.ordinal();
                        }
                    ))
                    .then(Commands.literal("time")
                        .then(NodeBuilders.buildSetTimeNode("set",(time, ctx)->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            ServerLevel level=world.level();

                            TimeSkipEvent event = new TimeSkipEvent(world.world(), TimeSkipEvent.SkipReason.COMMAND, time-level.getDayTime());
                            Bukkit.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                level.setDayTime(level.getDayTime() + event.getSkipAmount());
                            }

                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.time.set", time)));
                            return time%24000;
                        }))
                        .then(Commands.literal("add").then(Commands.argument("value",ArgumentTypes.time())
                            .executes(ctx->{
                                CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                if(!world.loaded())throw worldNotLoaded.create(world.id.asString());
                                int time=ctx.getArgument("value",Integer.class);

                                ServerLevel level=world.level();

                                TimeSkipEvent event = new TimeSkipEvent(world.world(), TimeSkipEvent.SkipReason.COMMAND, time);
                                Bukkit.getPluginManager().callEvent(event);
                                if (!event.isCancelled()) {
                                    level.setDayTime(level.getDayTime() + event.getSkipAmount());
                                }

                                long out=level.getDayTime();

                                ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.time.set", out)));
                                return ((Long)out).intValue()%24000;
                            })
                        ))
                        .then(Commands.literal("query")
                            .then(Commands.literal("world-exists")
                                .executes(ctx->{
                                    CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                    if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                                    long time=world.level().getGameTime();
                                    timeMessage(ctx.getSource(),time);
                                    return ((Long)time).intValue();
                                })
                            )
                            .then(Commands.literal("days")
                                .executes(ctx->{
                                    CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                    if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                                    long time=world.level().getDayTime()/24000L;
                                    timeMessage(ctx.getSource(),time);
                                    return ((Long)time).intValue();
                                })
                            )
                            .then(Commands.literal("hours")
                                .executes(ctx->{
                                    CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                    if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                                    long time=world.level().getDayTime();
                                    timeMessage(ctx.getSource(),time);
                                    return ((Long)time).intValue();
                                })
                            )
                            .then(Commands.literal("hours-in-day")
                                .executes(ctx->{
                                    CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                    if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                                    long time=world.level().getGameTime()%24000L;
                                    timeMessage(ctx.getSource(),time);
                                    return ((Long)time).intValue();
                                })
                            )
                        )
                    )
                    .then(BorderSubCommand.buildNode())
                    .then(Commands.literal("spawn").then(Commands.argument("pos",ArgumentTypes.blockPosition())
                        .executes(ctx->{
                            CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                            if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                            BlockPosition pos=ctx.getArgument("pos",BlockPositionResolver.class).resolve(ctx.getSource());
                            world.world().setSpawnLocation(pos.blockX(),pos.blockY(),pos.blockZ());

                            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.setworldspawn.success", pos.blockX(),pos.blockY(),pos.blockZ(), 0)));
                            return 1;
                        })
                        .then(Commands.argument("yaw",FloatArgumentType.floatArg(-180,180))
                            .executes(ctx->{
                                CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                                if(!world.loaded())throw worldNotLoaded.create(world.id.asString());

                                BlockPosition pos=ctx.getArgument("pos",BlockPositionResolver.class).resolve(ctx.getSource());
                                float yaw=ctx.getArgument("yaw",Float.class);

                                world.world().setSpawnLocation(pos.blockX(),pos.blockY(),pos.blockZ(),yaw);
                                ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.setworldspawn.success", pos.blockX(),pos.blockY(),pos.blockZ(), yaw)));
                                return 1;
                            })
                        )
                    ))
                    .then(MoveSubCommand.buildNode())
                    .then(DeleteSubCommand.buildNode())
                )
            )
            .then(Commands.literal("create")
                .requires(ctx->Util.hasOneOfPermission(ctx.getSender(),createPermissionSet))
                .then(Commands.argument("prototype",WorldFileArgument.instance).then(Commands.argument("id",ArgumentTypes.namespacedKey())
                    .executes(ctx->{
                        WorldFile file=ctx.getArgument("prototype",WorldFile.class);
                        NamespacedKey id=ctx.getArgument("id",NamespacedKey.class);

                        ctx.getSource().getSender().sendMessage("Creating world...");
                        try{
                            WorldMagic.createWorldFromFile(id,file);
                        }catch(WorldMagic.WorldCreationException e){
                            throw worldCreationError.create(e.getMessage());
                        }

                        return 1;
                    })
                    .then(Commands.argument("bukkitId",StringArgumentType.word())
                        .executes(ctx->{
                            WorldFile file=ctx.getArgument("prototype",WorldFile.class);
                            NamespacedKey id=ctx.getArgument("id",NamespacedKey.class);
                            String bukkitId=ctx.getArgument("bukkitId",String.class);

                            ctx.getSource().getSender().sendMessage("Creating world...");
                            try{
                                WorldMagic.createWorldFromFile(id,bukkitId,file);
                            }catch(WorldMagic.WorldCreationException e){
                                throw worldCreationError.create(e.getMessage());
                            }

                            return 1;
                        })
                        .then(Commands.argument("folder",StringArgumentType.string())
                            .executes(ctx->{
                                WorldFile file=ctx.getArgument("prototype",WorldFile.class);
                                NamespacedKey id=ctx.getArgument("id",NamespacedKey.class);
                                String bukkitId=ctx.getArgument("bukkitId",String.class);
                                String folder=ctx.getArgument("folder",String.class);

                                ctx.getSource().getSender().sendMessage("Creating world...");
                                try{
                                    WorldMagic.createWorldFromFile(id,bukkitId,folder,file);
                                }catch(WorldMagic.WorldCreationException e){
                                    throw worldCreationError.create(e.getMessage());
                                }

                                return 1;
                            })
                        )
                    )
                ))
            )
            .then(Commands.literal("reload")
                .requires(ctx->Util.hasOneOfPermission(ctx.getSender(),reloadPermissionSet))
                .executes(ctx->{
                    ctx.getSource().getSender().sendMessage("Reloading world files...");

                    new Thread(()->WorldMagic.instance().loadWorldFiles()).start();
                    return 1;
                })
            )
            .build();
    }

    private static void timeMessage(CommandSourceStack ctx,long time){
        ctx.getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.time.query",time)));
    }
}
