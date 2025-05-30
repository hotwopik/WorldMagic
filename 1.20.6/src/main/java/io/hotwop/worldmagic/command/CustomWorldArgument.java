package io.hotwop.worldmagic.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.CustomWorld;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.minecraft.network.chat.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class CustomWorldArgument implements CustomArgumentType<CustomWorld,NamespacedKey>{
    public static final CustomWorldArgument instance=new CustomWorldArgument();

    public static final DynamicCommandExceptionType unknownWorldException=new DynamicCommandExceptionType(obj->Component.literal("World "+obj+" not exist or isn't WorldMagic world"));

    private CustomWorldArgument(){}

    @Override
    public @NotNull CustomWorld parse(@NotNull StringReader reader) throws CommandSyntaxException{
        NamespacedKey worldId=NodeBuilders.readNamespacedKey(reader);
        CustomWorld world=WorldMagic.getPluginWorld(worldId);

        if(world==null)throw unknownWorldException.create(worldId.asString());

        return world;
    }

    @Override
    public @NotNull ArgumentType<NamespacedKey> getNativeType() {
        return ArgumentTypes.namespacedKey();
    }

    @Override
    public @NotNull <S> CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> ctx, @NotNull SuggestionsBuilder builder) {
        String input=builder.getRemaining();

        if(input.isEmpty())WorldMagic.getPluginWorlds()
            .forEach(wr->builder.suggest(wr.id.asString()));
        else WorldMagic.getPluginWorlds().stream()
            .filter(wr->wr.id.asString().startsWith(input)||wr.id.asMinimalString().startsWith(input))
            .forEach(wr->builder.suggest(wr.id.asString()));

        return builder.buildFuture();
    }
}
