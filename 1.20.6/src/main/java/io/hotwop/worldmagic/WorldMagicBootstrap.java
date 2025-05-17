package io.hotwop.worldmagic;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;

public final class WorldMagicBootstrap implements PluginBootstrap{
    private static ComponentLogger logger;
    public static ComponentLogger logger(){
        return logger;
    }

    public void bootstrap(@NotNull BootstrapContext ctx) {
        logger=ctx.getLogger();
    }
}
