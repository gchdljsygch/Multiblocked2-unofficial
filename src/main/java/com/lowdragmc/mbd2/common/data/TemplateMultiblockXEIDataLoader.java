package com.lowdragmc.mbd2.common.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.pattern.TemplateMultiblockXEIData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

/**
 * Server data-pack reload listener for template multiblock XEI pages.
 */
@ParametersAreNonnullByDefault
public final class TemplateMultiblockXEIDataLoader extends SimpleJsonResourceReloadListener {
    private final Consumer<Map<ResourceLocation, TemplateMultiblockXEIData>> reloadConsumer;

    public TemplateMultiblockXEIDataLoader(
            Consumer<Map<ResourceLocation, TemplateMultiblockXEIData>> reloadConsumer) {
        super(new Gson(), TemplateMultiblockXEIData.DATA_DIRECTORY);
        this.reloadConsumer = Objects.requireNonNull(reloadConsumer, "reloadConsumer");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonEntries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, TemplateMultiblockXEIData> parsedEntries = new LinkedHashMap<>();
        jsonEntries.forEach((resourceId, json) -> {
            ResourceLocation entryId = normalizeId(resourceId);
            try {
                parsedEntries.put(entryId, TemplateMultiblockXEIData.fromJson(entryId, json));
            } catch (RuntimeException exception) {
                MBD2.LOGGER.error("Failed to load template multiblock XEI data {}", resourceId, exception);
            }
        });
        reloadConsumer.accept(parsedEntries);
    }

    private static ResourceLocation normalizeId(ResourceLocation resourceId) {
        String prefix = TemplateMultiblockXEIData.DATA_DIRECTORY + "/";
        if (resourceId.getPath().startsWith(prefix)) {
            String path = resourceId.getPath().substring(prefix.length());
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Template XEI entry has an empty id: " + resourceId);
            }
            return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path);
        }
        return resourceId;
    }
}
