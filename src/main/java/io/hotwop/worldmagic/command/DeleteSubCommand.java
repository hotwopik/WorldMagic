package io.hotwop.worldmagic.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.CustomWorld;
import io.hotwop.worldmagic.WorldDeletionException;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.WorldMagicBootstrap;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class DeleteSubCommand{
    private DeleteSubCommand(){}

    private static boolean awaitDeletion=false;

    public static LiteralCommandNode<CommandSourceStack> buildNode(){
        return Commands.literal("delete")
            .requires(ctx->ctx.getSender().hasPermission("worldmagic.command.world.delete"))
            .executes(ctx->{
                CustomWorld world=ctx.getArgument("world", CustomWorld.class);

                switch(ctx.getSource().getSender()){
                    case Player pl->{
                        pl.sendMessage(Component
                            .text("Are you sure?")
                            .appendNewline()
                            .append(Component.text("5 seconds for answer: "))
                            .append(Component
                                .text("[Yes]",NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.callback(
                                    aud->{
                                        aud.sendMessage(Component.text("Deleting world..."));
                                        try{
                                            WorldMagic.deleteWorld(world.id);
                                        }catch (WorldDeletionException e){
                                            aud.sendMessage(Component.text("Error to delete world: "+e.getMessage(),NamedTextColor.RED));
                                        }
                                    },
                                    bl->bl
                                        .uses(1)
                                        .lifetime(Duration.ofSeconds(5))
                                ))
                            )
                        );

                        return 1;
                    }
                    case ConsoleCommandSender console->{
                        if(awaitDeletion){
                            awaitDeletion=false;

                            try{
                                WorldMagic.deleteWorld(world.id);
                            }catch(WorldDeletionException e){
                                throw WorldMagicBootstrap.worldDeleteException.create(e.getMessage());
                            }
                        }else{
                            awaitDeletion=true;
                            Bukkit.getAsyncScheduler().runDelayed(WorldMagic.instance(),task->awaitDeletion=false,5,TimeUnit.SECONDS);

                            console.sendMessage(Component
                                .text("Are you sure?")
                                .appendNewline()
                                .append(Component.text("5 seconds for answer: "))
                                .append(Component.text("Type command again to confirm"))
                            );
                        }

                        return 1;
                    }
                    default->{
                        return 0;
                    }
                }
            })
            .build();
    }
}
