package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.ISerializableRenderer;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.IRendererResource;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.StaticResource;
import com.lowdragmc.mbd2.utils.UIResourceRendererContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;

/**
 * Loads LDLib static resources defensively and gives renderer resources their UI context.
 *
 * <p>MBD renderer resources need {@link UIResourceRendererContext} while they are deserialized so
 * nested renderer data can resolve workspace-relative assets. This mixin also avoids replacing an
 * existing cached resource with {@code null} when an edited file is temporarily invalid.</p>
 */
@Mixin(value = StaticResource.class, remap = false)
public abstract class StaticResourceMixin<T> {
    @Shadow
    @Final
    private Resource<T> resource;

    @Shadow
    @Final
    Map<File, T> staticResources;

    @Shadow
    @Final
    Map<File, Long> staticResourcesLastModified;

    @Shadow
    private boolean isStaticResourceLoaded;

    /**
     * Replaces LDLib static-resource loading with the guarded MBD implementation.
     *
     * @param cir callback receiving whether the visible static resource set changed
     */
    @Inject(method = "loadAndUpdateStaticResource", at = @At("HEAD"), cancellable = true)
    private void mbd2$loadAndUpdateStaticResourceSafely(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(mbd2$loadAndUpdateStaticResourceSafely());
    }

    /**
     * Scans the resource's static folder, reloads changed files, and removes disappeared entries.
     *
     * @return {@code true} when the cached resource map changed
     */
    @Unique
    private boolean mbd2$loadAndUpdateStaticResourceSafely() {
        if (resource == null) {
            return false;
        }

        var changed = false;
        var found = new HashSet<File>();
        var files = resource.getStaticLocation().listFiles();
        if (files != null) {
            for (var file : files) {
                if (file.isFile() && file.getName().endsWith(resource.getStaticResourceSuffix())) {
                    found.add(file);
                    if (staticResources.containsKey(file)) {
                        Long lastModified = staticResourcesLastModified.get(file);
                        if (lastModified == null || lastModified != file.lastModified()) {
                            var res = mbd2$readResourceFromFile(file);
                            if (res != null) {
                                staticResources.put(file, res);
                                staticResourcesLastModified.put(file, file.lastModified());
                                changed = true;
                            }
                        }
                    } else {
                        var res = mbd2$readResourceFromFile(file);
                        if (res != null) {
                            staticResources.put(file, res);
                            staticResourcesLastModified.put(file, file.lastModified());
                            changed = true;
                        }
                    }
                }
            }
        }

        var removed = new HashSet<>(staticResources.keySet());
        removed.removeAll(found);
        removed.removeIf(File::isFile);
        if (!removed.isEmpty()) {
            removed.forEach(file -> {
                staticResourcesLastModified.remove(file);
                staticResources.remove(file);
            });
            changed = true;
        }

        isStaticResourceLoaded = true;
        return changed;
    }

    /**
     * Reads one static resource file, applying renderer deserialization context when needed.
     *
     * @param file static resource file on disk
     * @return decoded resource value, or {@code null} when the file is invalid or unreadable
     */
    @Nullable
    @Unique
    @SuppressWarnings("unchecked")
    private T mbd2$readResourceFromFile(File file) {
        try {
            var fileData = NbtIo.read(file);
            if (fileData != null && fileData.getString("type").equals(resource.name())) {
                if (resource instanceof IRendererResource && fileData.get("data") instanceof CompoundTag rendererTag) {
                    try (var ignored = UIResourceRendererContext.push((Resource<IRenderer>) resource, true)) {
                        return (T) ISerializableRenderer.deserializeWrapper(rendererTag);
                    }
                }
                return resource.deserialize(fileData.get("data"));
            }
            LDLib.LOGGER.error("Failed to load static resource file {} from {}: ", file, this);
        } catch (IOException e) {
            LDLib.LOGGER.error("Failed to load static resource file {} from {}: ", file, this, e);
        }
        return null;
    }
}
