package io.hotwop.worldmagic.integration.papi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PlaceholderResolver {
    @Nullable String check(@NotNull String input);
}
