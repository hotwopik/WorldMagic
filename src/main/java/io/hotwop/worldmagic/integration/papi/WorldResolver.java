package io.hotwop.worldmagic.integration.papi;

import io.hotwop.worldmagic.CustomWorld;
import io.hotwop.worldmagic.WorldMagic;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.regex.Pattern;

public final class WorldResolver implements PlaceholderResolver {
    private final Pattern pattern;
    private final Function<CustomWorld, String> consumer;

    public WorldResolver(String name, Function<CustomWorld, String> consumer) {
        pattern = Pattern.compile("world_<[^>\\s]+>_" + name);
        this.consumer = consumer;
    }

    public String check(@NotNull String input) {
        if (pattern.matcher(input).matches()) {
            String name=input.split("_")[1];

            NamespacedKey id = NamespacedKey.fromString(name.substring(1,name.length()-1));
            if(id==null)return "";

            return consumer.apply(WorldMagic.getPluginWorld(id));
        }
        return null;
    }
}
