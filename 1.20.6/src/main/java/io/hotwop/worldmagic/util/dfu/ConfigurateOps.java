package io.hotwop.worldmagic.util.dfu;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationNodeFactory;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

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

public final class ConfigurateOps implements DynamicOps<ConfigurationNode> {
    private static final ConfigurateOps UNCOMPRESSED = builder().build();
    private static final ConfigurateOps COMPRESSED = builder().compressed(true).build();

    private final ConfigurationNodeFactory<? extends @NotNull ConfigurationNode> factory;
    private final boolean compressed;
    private final Protection readProtection;
    private final Protection writeProtection;

    public static DynamicOps<ConfigurationNode> instance() {
        return instance(false);
    }

    public static DynamicOps<ConfigurationNode> instance(final boolean compressed) {
        return compressed ? COMPRESSED : UNCOMPRESSED;
    }

    public static DynamicOps<ConfigurationNode> forSerializers(final TypeSerializerCollection collection) {
        if (requireNonNull(collection, "collection").equals(TypeSerializerCollection.defaults())) {
            return UNCOMPRESSED;
        } else {
            return builder().factoryFromSerializers(collection).build();
        }
    }

    public static Dynamic<ConfigurationNode> wrap(final ConfigurationNode node) {
        if (node.options().serializers().equals(TypeSerializerCollection.defaults())) {
            return new Dynamic<>(instance(), node);
        } else {
            return builder().factoryFromNode(node).buildWrapping(node);
        }
    }

    public static DynamicOps<ConfigurationNode> fromNode(final ConfigurationNode value) {
        return builder().factoryFromNode(value).build();
    }

    public static ConfigurateOpsBuilder builder(){
        return new ConfigurateOpsBuilder();
    }


    ConfigurateOps(final ConfigurationNodeFactory<? extends @NotNull ConfigurationNode> factory, final boolean compressed,
                   final Protection readProtection, final Protection writeProtection) {
        this.factory = factory;
        this.compressed = compressed;
        this.readProtection = readProtection;
        this.writeProtection = writeProtection;
    }

    @Override
    public boolean compressMaps() {
        return this.compressed;
    }

    static Object keyFrom(final ConfigurationNode node) {
        if (node.isList() || node.isMap()) {
            throw new IllegalArgumentException("Key nodes must have scalar values");
        }
        return requireNonNull(node.raw(), "The provided key node must have a value");
    }

    ConfigurationNode guardOutputRead(final ConfigurationNode untrusted) {
        return switch (this.readProtection) {
            case COPY_DEEP -> untrusted.copy();
            case NONE -> untrusted;
        };
    }

    ConfigurationNode guardInputWrite(final ConfigurationNode untrusted) {
        return switch (this.writeProtection) {
            case COPY_DEEP -> untrusted.copy();
            case NONE -> untrusted;
        };
    }

    @Override
    public ConfigurationNode empty() {
        return this.factory.createNode();
    }

    @Override
    public ConfigurationNode emptyMap() {
        return empty().raw(ImmutableMap.of());
    }

    @Override
    public ConfigurationNode emptyList() {
        return empty().raw(ImmutableList.of());
    }

    @SuppressWarnings("unchecked")
    private <U> @Nullable U convertSelf(final DynamicOps<U> outOps, final ConfigurationNode input) {
        if (outOps instanceof ConfigurateOps) {
            return (U) input;
        } else {
            return null;
        }
    }

    @Override
    public <U> U convertTo(final DynamicOps<U> targetOps, final ConfigurationNode source) {
        final @Nullable U self = convertSelf(requireNonNull(targetOps, "targetOps"), requireNonNull(source, "source"));
        if (self != null) {
            return self;
        }

        if (source.isMap()) {
            return convertMap(targetOps, source);
        } else if (source.isList()) {
            return convertList(targetOps, source);
        } else {
            final @Nullable Object value = source.rawScalar();
            return switch (value) {
                case null -> targetOps.empty();
                case String s -> targetOps.createString(s);
                case Boolean b -> targetOps.createBoolean(b);
                case Short i -> targetOps.createShort(i);
                case Integer i -> targetOps.createInt(i);
                case Long l -> targetOps.createLong(l);
                case Float v -> targetOps.createFloat(v);
                case Double v -> targetOps.createDouble(v);
                case Byte b -> targetOps.createByte(b);
                case byte[] bytes -> targetOps.createByteList(ByteBuffer.wrap(bytes));
                case int[] ints -> targetOps.createIntList(IntStream.of(ints));
                case long[] longs -> targetOps.createLongList(LongStream.of(longs));
                default -> throw new IllegalArgumentException("Scalar value '" + source + "' has an unknown type: " + value.getClass().getName());
            };
        }
    }

    @Override
    public DataResult<Number> getNumberValue(final ConfigurationNode input) {
        if (!(input.isMap() || input.isList())) {
            final @Nullable Object value = input.rawScalar();
            if (value instanceof Number) {
                return DataResult.success((Number) value);
            } else if (value instanceof Boolean) {
                return DataResult.success((boolean) value ? 1 : 0);
            }

            if (compressMaps()) {
                final int result = input.getInt(Integer.MIN_VALUE);
                if (result == Integer.MIN_VALUE) {
                    return DataResult.error(()->"Value is not a number");
                }
                return DataResult.success(result);
            }
        }

        return DataResult.error(()->"Not a number: " + input);
    }

    @Override
    public DataResult<String> getStringValue(final ConfigurationNode input) {
        final @Nullable String value = input.getString();
        if (value != null) {
            return DataResult.success(value);
        }

        return DataResult.error(()->"Not a string: " + input);
    }

    @Override
    public ConfigurationNode createNumeric(final Number value) {
        return empty().raw(requireNonNull(value, "value"));
    }

    @Override
    public ConfigurationNode createBoolean(final boolean value) {
        return empty().raw(value);
    }

    @Override
    public ConfigurationNode createString(final String value) {
        return empty().raw(requireNonNull(value, "value"));
    }

    @Override
    public DataResult<ConfigurationNode> mergeToPrimitive(final ConfigurationNode prefix, final ConfigurationNode value) {
        if (!prefix.empty()) {
            return DataResult.error(()->"Cannot merge " + value + " into non-empty node " + prefix);
        }
        return DataResult.success(guardOutputRead(value));
    }

    @Override
    public DataResult<ConfigurationNode> mergeToList(final ConfigurationNode input, final ConfigurationNode value) {
        if (input.isList() || input.empty()) {
            final ConfigurationNode ret = guardOutputRead(input);
            ret.appendListNode().from(value);
            return DataResult.success(ret);
        }

        return DataResult.error(()->"mergeToList called on a node which is not a list: " + input, input);
    }

    @Override
    public DataResult<ConfigurationNode> mergeToList(final ConfigurationNode input, final List<ConfigurationNode> values) {
        if (input.isList() || input.empty()) {
            final ConfigurationNode ret = guardInputWrite(input);
            for (ConfigurationNode node : values) {
                ret.appendListNode().from(node);
            }
            return DataResult.success(ret);
        }

        return DataResult.error(()->"mergeToList called on a node which is not a list: " + input, input);
    }

    @Override
    public DataResult<ConfigurationNode> mergeToMap(final ConfigurationNode input, final ConfigurationNode key, final ConfigurationNode value) {
        if (input.isMap() || input.empty()) {
            final ConfigurationNode copied = guardInputWrite(input);
            copied.node(keyFrom(key)).from(value);
            return DataResult.success(copied);
        }

        return DataResult.error(()->"mergeToMap called on a node which is not a map: " + input, input);
    }

    @Override
    public DataResult<Stream<Pair<ConfigurationNode, ConfigurationNode>>> getMapValues(final ConfigurationNode input) {
        if (input.empty() || input.isMap()) {
            return DataResult.success(input.childrenMap().entrySet().stream()
                .map(entry -> Pair.of(BasicConfigurationNode.root(input.options()).raw(entry.getKey()),
                    guardOutputRead(entry.getValue()))));
        }

        return DataResult.error(()->"Not a map: " + input);
    }

    @Override
    public DataResult<MapLike<ConfigurationNode>> getMap(final ConfigurationNode input) {
        if (input.empty() || input.isMap()) {
            return DataResult.success(new NodeMaplike(this, input.options(), input.childrenMap()));
        } else {
            return DataResult.error(()->"Input node is not a map");
        }
    }

    @Override
    public DataResult<Consumer<Consumer<ConfigurationNode>>> getList(final ConfigurationNode input) {
        if (input.isList()) {
            return DataResult.success(action -> {
                for (ConfigurationNode child : input.childrenList()) {
                    action.accept(guardOutputRead(child));
                }
            });
        } else {
            return DataResult.error(()->"Input node is not a list");
        }
    }

    @Override
    public DataResult<Stream<ConfigurationNode>> getStream(final ConfigurationNode input) {
        if (input.empty() || input.isList()) {
            final Stream<ConfigurationNode> stream = input.childrenList().stream().map(this::guardOutputRead);
            return DataResult.success(stream);
        }

        return DataResult.error(()->"Not a list: " + input);
    }

    @Override
    public ConfigurationNode createMap(final Stream<Pair<ConfigurationNode, ConfigurationNode>> values) {
        final ConfigurationNode ret = empty();

        values.forEach(p -> ret.node(keyFrom(p.getFirst())).from(p.getSecond()));

        return ret;
    }

    @Override
    public ConfigurationNode createMap(final Map<ConfigurationNode, ConfigurationNode> values) {
        final ConfigurationNode ret = empty();

        for (Map.Entry<ConfigurationNode, ConfigurationNode> entry : values.entrySet()) {
            ret.node(keyFrom(entry.getKey())).from(entry.getValue());
        }

        return ret;
    }

    @Override
    public ConfigurationNode createList(final Stream<ConfigurationNode> input) {
        final ConfigurationNode ret = empty();
        input.forEach(it -> ret.appendListNode().from(it));
        return ret;
    }

    @Override
    public ConfigurationNode remove(final ConfigurationNode input, final String key) {
        if (input.isMap()) {
            final ConfigurationNode ret = guardInputWrite(input);
            ret.node(key).raw(null);
            return ret;
        }

        return input;
    }

    @Override
    public DataResult<ConfigurationNode> get(final ConfigurationNode input, final String key) {
        final ConfigurationNode ret = input.node(key);
        return ret.virtual() ? DataResult.error(()->"No element " + key + " in the map " + input) : DataResult.success(guardOutputRead(ret));
    }

    @Override
    public DataResult<ConfigurationNode> getGeneric(final ConfigurationNode input, final ConfigurationNode key) {
        final ConfigurationNode ret = input.node(keyFrom(key));
        return ret.virtual() ? DataResult.error(()->"No element " + key + " in the map " + input) : DataResult.success(guardOutputRead(ret));
    }

    @Override
    public ConfigurationNode set(final ConfigurationNode input, final String key, final ConfigurationNode value) {
        final ConfigurationNode ret = guardInputWrite(input);
        ret.node(key).from(value);
        return ret;
    }

    @Override
    public ConfigurationNode update(final ConfigurationNode input, final String key, final Function<ConfigurationNode, ConfigurationNode> function) {
        if (input.node(key).virtual()) {
            return input;
        }

        final ConfigurationNode ret = guardInputWrite(input);
        final ConfigurationNode child = ret.node(key);
        child.from(function.apply(child));
        return ret;
    }

    @Override
    public ConfigurationNode updateGeneric(final ConfigurationNode input, final ConfigurationNode wrappedKey,
                                           final Function<ConfigurationNode, ConfigurationNode> function) {
        final Object key = keyFrom(wrappedKey);
        if (input.node(key).virtual()) {
            return input;
        }

        final ConfigurationNode ret = guardInputWrite(input);

        final ConfigurationNode child = ret.node(key);
        child.from(function.apply(child));
        return ret;
    }

    @Override
    public String toString() {
        return "Configurate";
    }

    public enum Protection {
        COPY_DEEP,
        NONE
    }

}
