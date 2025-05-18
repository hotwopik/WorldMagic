package io.hotwop.worldmagic.util.dfu;

import static java.util.Objects.requireNonNull;

import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationNodeFactory;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Fixed version of org.spongepowered:configurate-extra-dfu4:4.2.0, original can be founded at https://github.com/SpongePowered/Configurate/tree/trunk/extra/dfu4/src/main/java/org/spongepowered/configurate/extra/dfu/v4

public final class ConfigurateOpsBuilder{
    private ConfigurationNodeFactory<? extends @NotNull ConfigurationNode> nodeSupplier = CommentedConfigurationNode.factory();
    private boolean compressed;
    private ConfigurateOps.Protection readProtection = ConfigurateOps.Protection.COPY_DEEP;
    private ConfigurateOps.Protection writeProtection = ConfigurateOps.Protection.COPY_DEEP;

    ConfigurateOpsBuilder() {}

    public ConfigurateOpsBuilder factory(final ConfigurationNodeFactory<? extends @NotNull ConfigurationNode> supplier) {
        this.nodeSupplier = requireNonNull(supplier, "nodeSupplier");
        return this;
    }

    public ConfigurateOpsBuilder factoryFromSerializers(final TypeSerializerCollection collection) {
        requireNonNull(collection, "collection");
        return factory(options -> CommentedConfigurationNode.root(options.serializers(collection)));
    }

    public ConfigurateOpsBuilder factoryFromNode(final ConfigurationNode node) {
        final ConfigurationOptions options = requireNonNull(node, "node").options();
        return factory(new ConfigurationNodeFactory<>() {
            @Override
            public ConfigurationNode createNode(final @NotNull ConfigurationOptions options) {
                return CommentedConfigurationNode.root(options);
            }

            @Override public @NotNull ConfigurationOptions defaultOptions() {
                return options;
            }
        });
    }

    public ConfigurateOpsBuilder compressed(final boolean compressed) {
        this.compressed = compressed;
        return this;
    }

    public ConfigurateOpsBuilder readProtection(final ConfigurateOps.Protection readProtection) {
        this.readProtection = requireNonNull(readProtection, "readProtection");
        return this;
    }

    public ConfigurateOpsBuilder writeProtection(final ConfigurateOps.Protection writeProtection) {
        this.writeProtection = requireNonNull(writeProtection, "writeProtection");
        return this;
    }

    public ConfigurateOpsBuilder readWriteProtection(final ConfigurateOps.Protection protection) {
        requireNonNull(protection, "protection");
        this.readProtection = protection;
        this.writeProtection = protection;
        return this;
    }

    public ConfigurateOps build() {
        return new ConfigurateOps(this.nodeSupplier, this.compressed, this.readProtection, this.writeProtection);
    }

    public Dynamic<ConfigurationNode> buildWrapping(final ConfigurationNode node) {
        return new Dynamic<>(build(), node);
    }

}
