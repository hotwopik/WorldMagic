package io.hotwop.worldmagic.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.hotwop.worldmagic.CustomWorld;
import io.hotwop.worldmagic.WorldMagic;
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
                                    aud->WorldMagic.deleteWorld(world.id),
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

                            WorldMagic.deleteWorld(world.id);
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
