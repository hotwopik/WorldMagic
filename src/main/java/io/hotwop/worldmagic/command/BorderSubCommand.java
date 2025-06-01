package io.hotwop.worldmagic.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.WorldMagicBootstrap;
import io.hotwop.worldmagic.CustomWorld;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Locale;
import java.util.function.Function;

public final class BorderSubCommand{
    private BorderSubCommand(){}

    public static final SimpleCommandExceptionType ERROR_SAME_SIZE = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.nochange"));
    public static final SimpleCommandExceptionType ERROR_TOO_SMALL = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.small"));
    public static final SimpleCommandExceptionType ERROR_TOO_BIG = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.big", 5.9999968E7D));
    public static final SimpleCommandExceptionType ERROR_TOO_FAR_OUT = new SimpleCommandExceptionType(Component.translatable("commands.worldborder.set.failed.far", 2.9999984E7D));

    public static LiteralCommandNode<CommandSourceStack> buildNode(){
        return Commands.literal("border")
            .then(Commands.literal("add").then(Commands.argument("distance",DoubleArgumentType.doubleArg())
                .executes(ctx->changeSize(ctx,start->start+ctx.getArgument("distance",Double.class),current->current))
                .then(Commands.argument("milliseconds", LongArgumentType.longArg(1))
                    .executes(ctx->changeSize(ctx,start->start+ctx.getArgument("distance",Double.class),current->current+ctx.getArgument("milliseconds",Long.class)))
                )
            ))
            .then(Commands.literal("set").then(Commands.argument("distance",DoubleArgumentType.doubleArg())
                .executes(ctx->changeSize(ctx,start->ctx.getArgument("distance",Double.class),current->0L))
                .then(Commands.argument("milliseconds", LongArgumentType.longArg(1))
                    .executes(ctx->changeSize(ctx,start->ctx.getArgument("distance",Double.class),current->ctx.getArgument("milliseconds",Long.class)))
                )
            ))
            .then(Commands.literal("center").then(Commands.argument("x",DoubleArgumentType.doubleArg()).then(Commands.argument("z",DoubleArgumentType.doubleArg())
                .executes(ctx->{
                    CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                    if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

                    double x=ctx.getArgument("x",Double.class);
                    double z=ctx.getArgument("z",Double.class);

                    if(Math.abs(x)>2.9999984E7D||Math.abs(z)>2.9999984E7D)throw ERROR_TOO_FAR_OUT.create();

                    WorldBorder border=world.level().getWorldBorder();
                    border.setCenter(x,z);

                    ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.center.success", String.format(Locale.ROOT, "%.2f", x), String.format(Locale.ROOT, "%.2f", z))));
                    return 1;
                })
            )))
            .then(Commands.literal("damage")
                .then(Commands.literal("amount").then(Commands.argument("value",DoubleArgumentType.doubleArg(0))
                    .executes(ctx->{
                        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

                        double value=ctx.getArgument("value",Double.class);

                        WorldBorder border=world.level().getWorldBorder();
                        border.setDamagePerBlock(value);

                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.damage.amount.success", String.format(Locale.ROOT, "%.2f", value))));
                        return 1;
                    })
                ))
                .then(Commands.literal("buffer").then(Commands.argument("distance",DoubleArgumentType.doubleArg(0))
                    .executes(ctx->{
                        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

                        double distance=ctx.getArgument("distance",Double.class);

                        WorldBorder border=world.level().getWorldBorder();
                        border.setDamageSafeZone(distance);

                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.damage.buffer.success", String.format(Locale.ROOT, "%.2f", distance))));
                        return 1;
                    })
                ))
            )
            .then(Commands.literal("warning")
                .then(Commands.literal("distance").then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx->{
                        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

                        int value=ctx.getArgument("value",Integer.class);

                        WorldBorder border=world.level().getWorldBorder();
                        border.setWarningBlocks(value);

                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.warning.distance.success", value)));
                        return 1;
                    })
                ))
                .then(Commands.literal("time").then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx->{
                        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
                        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

                        int value=ctx.getArgument("value",Integer.class);

                        WorldBorder border=world.level().getWorldBorder();
                        border.setWarningTime(value);

                        ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.warning.time.success", value)));
                        return 1;
                    })
                ))
            )
            .build();
    }

    private static int changeSize(CommandContext<CommandSourceStack> ctx, Function<Double,Double> getEnd, Function<Long,Long> getTime) throws CommandSyntaxException {
        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

        WorldBorder border=world.level().getWorldBorder();
        double start=border.getSize();
        double end=getEnd.apply(start);

        if(start==end)throw ERROR_SAME_SIZE.create();
        if(end<1)throw ERROR_TOO_SMALL.create();
        if(end>5.9999968e7)throw ERROR_TOO_BIG.create();

        double change=end-start;
        long time=getTime.apply(border.getLerpRemainingTime());

        if(time==0){
            border.setSize(end);
            ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.set.immediate", String.format(Locale.ROOT, "%.1f", end))));
        }else{
            border.lerpSizeBetween(start,end,time);

            if(change>0)ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.set.grow", String.format(Locale.ROOT, "%.1f", end), Long.toString(time / 1000L))));
            else ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.worldborder.set.shrink", String.format(Locale.ROOT, "%.1f", end), Long.toString(time / 1000L))));
        }

        return (int)change;
    }
}
