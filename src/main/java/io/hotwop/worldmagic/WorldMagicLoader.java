package io.hotwop.worldmagic;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.Map;
import java.util.Set;

public final class WorldMagicLoader implements PluginLoader{
    @Override
    public void classloader(PluginClasspathBuilder builder) {
        MavenLibraryResolver resolver=new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
            "papermc-repo",
            "default",
            "https://repo.papermc.io/repository/maven-public/"
        ).build());

        resolver.addDependency(new Dependency(
            new DefaultArtifact(
                "org.spongepowered:configurate-extra-dfu7:4.3.0-SNAPSHOT",
                Map.of("transitive","false")
            ),
            null,
            false,
            Set.of(
                new Exclusion("org.spongepowered","configurate-core","*","*"),
                new Exclusion("com.mojang","datafixerupper","*","*"),
                new Exclusion("com.google.errorprone","error_prone_annotations","*","*")
            )
        ));

        builder.addLibrary(resolver);
    }
}
