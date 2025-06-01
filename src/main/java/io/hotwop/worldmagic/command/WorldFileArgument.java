package io.hotwop.worldmagic.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.hotwop.worldmagic.WorldMagic;
import io.hotwop.worldmagic.file.WorldFile;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.minecraft.network.chat.Component;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class WorldFileArgument implements CustomArgumentType<WorldFile,NamespacedKey>{
    public static final WorldFileArgument instance=new WorldFileArgument();
    private WorldFileArgument(){}

    public static final DynamicCommandExceptionType unknownWorldFileException=new DynamicCommandExceptionType(obj-> Component.literal("World file "+obj+" not exist"));

    public @NotNull WorldFile parse(@NotNull StringReader reader) throws CommandSyntaxException {
        NamespacedKey fileId=NodeBuilders.readNamespacedKey(reader);
        WorldFile file=WorldMagic.getWorldFile(fileId);

        if(file==null)throw unknownWorldFileException.create(fileId.asString());

        return file;
    }
    public @NotNull ArgumentType<NamespacedKey> getNativeType() {
        return ArgumentTypes.namespacedKey();
    }

    @Override
    public @NotNull <S> CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> ctx, @NotNull SuggestionsBuilder builder) {
        String input=builder.getRemaining();

        if(input.isEmpty())WorldMagic.getWorldFileIds().forEach(id->builder.suggest(id.asString()));
        else WorldMagic.getWorldFileIds().stream().filter(id->id.asString().startsWith(input)||id.asMinimalString().startsWith(input))
            .forEach(id->builder.suggest(id.asString()));

        return builder.buildFuture();
    }
}
