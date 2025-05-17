package io.hotwop.worldmagic;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;

public final class WorldMagicLoader implements PluginLoader{
    public void classloader(@NotNull PluginClasspathBuilder builder) {
        MavenLibraryResolver resolver=new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder("maven-central","default","https://repo1.maven.org/maven2/").build());
        resolver.addRepository(new RemoteRepository.Builder("paper","default","https://repo.papermc.io/repository/maven-public/").build());

        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-extra-dfu4:4.2.0"),null));

        builder.addLibrary(resolver);
    }
}
