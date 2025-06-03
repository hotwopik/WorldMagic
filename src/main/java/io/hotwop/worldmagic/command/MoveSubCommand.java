package io.hotwop.worldmagic.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.WorldMagicBootstrap;
import io.hotwop.worldmagic.CustomWorld;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class MoveSubCommand{
    private MoveSubCommand(){}

    public static LiteralCommandNode<CommandSourceStack> buildNode(){
        return Commands.literal("move").then(Commands.argument("target", ArgumentTypes.entities())
            .then(Commands.argument("pos", Vec3Argument.vec3(true))
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
        ).build();
    }

    private static Location getPos(World world, CommandContext<CommandSourceStack> ctx){
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

    private static int move(boolean rotation, Function<World,Location> getLocation, CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CustomWorld world=ctx.getArgument("world", CustomWorld.class);
        if(!world.loaded())throw WorldMagicBootstrap.worldNotLoaded.create(world.id.asString());

        List<Entity> entities=ctx.getArgument("target", EntitySelectorArgumentResolver.class).resolve(ctx.getSource());

        Location location=getLocation.apply(world.world());
        entities.forEach(entity->{
            Location loc;
            if(rotation)loc=location;
            else{
                loc=location.clone();
                loc.setYaw(entity.getYaw());
                loc.setPitch(entity.getPitch());
            }

            entity.teleportAsync(loc, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(success->{
                if(!success)entity.teleport(loc);
            });
        });

        if(entities.size()==1)ctx.getSource().getSender().sendMessage(PaperAdventure.asAdventure(Component.translatable("commands.teleport.success.location.single",
            PaperAdventure.asVanilla(entities.getFirst().name()),
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
}
