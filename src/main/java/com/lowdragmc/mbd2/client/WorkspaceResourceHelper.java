package com.lowdragmc.mbd2.client;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.mbd2.MBD2;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Client-side utility for locating and scanning MBD workspace resources.
 *
 * <p>The helper bridges editor project files, the workspace assets directory, and Minecraft's resource manager. It is
 * used to discover model JSON files that exist in the editable workspace and model paths referenced by saved project
 * NBT. All filesystem scans are best-effort: IO failures are logged and represented as empty or partial results.</p>
 */
public final class WorkspaceResourceHelper {
    private static final Set<String> MODEL_LOCATION_KEYS = Set.of("modelLocation", "itemTransformModel");
    private static final Set<String> PROJECT_SUFFIXES = Set.of(".sm", ".mb", ".em", ".km");

    private WorkspaceResourceHelper() {
    }

    /**
     * Returns the root assets directory used by LowDragLib editor resources.
     *
     * @return workspace assets path, usually under the LDLib config/workspace directory
     */
    public static Path assetsRoot() {
        return LDLib.getLDLibDir().toPath().resolve("assets");
    }

    /**
     * Converts a resource location into its workspace filesystem path.
     *
     * @param location resource location relative to {@link #assetsRoot()}
     * @return platform-specific path to the resource file
     */
    public static Path workspaceResourcePath(ResourceLocation location) {
        return assetsRoot().resolve(location.getNamespace()).resolve(location.getPath().replace('/', java.io.File.separatorChar));
    }

    /**
     * Checks whether a workspace resource exists as a regular file.
     *
     * @param location resource location to test
     * @return {@code true} when the corresponding workspace path is a regular file
     */
    public static boolean workspaceResourceFileExists(ResourceLocation location) {
        return Files.isRegularFile(workspaceResourcePath(location));
    }

    /**
     * Converts a block/item model location into the JSON resource path used by resource packs.
     *
     * @param modelLocation logical model location such as {@code mbd2:machine/foo}
     * @return JSON resource location such as {@code mbd2:models/machine/foo.json}
     */
    public static ResourceLocation modelResourceLocation(ResourceLocation modelLocation) {
        return ResourceLocation.fromNamespaceAndPath(modelLocation.getNamespace(), "models/" + modelLocation.getPath() + ".json");
    }

    /**
     * Checks for a model JSON in the editable workspace assets tree.
     *
     * @param modelLocation logical model location without {@code models/} and {@code .json}
     * @return {@code true} when the workspace contains that model file
     */
    public static boolean workspaceModelFileExists(ResourceLocation modelLocation) {
        return workspaceResourceFileExists(modelResourceLocation(modelLocation));
    }

    /**
     * Checks Minecraft's active resource manager for a resource.
     *
     * @param location resource location to query
     * @return {@code true} when any active resource pack provides the resource
     */
    public static boolean resourceManagerHas(ResourceLocation location) {
        var minecraft = Minecraft.getInstance();
        return minecraft.getResourceManager().getResource(location).isPresent();
    }

    /**
     * Checks Minecraft's active resource manager for a model JSON.
     *
     * @param modelLocation logical model location without {@code models/} and {@code .json}
     * @return {@code true} when an active resource pack provides that model JSON
     */
    public static boolean resourceManagerHasModel(ResourceLocation modelLocation) {
        return resourceManagerHas(modelResourceLocation(modelLocation));
    }

    /**
     * Finds model files under the MBD workspace {@code models} directory.
     *
     * @return ordered set of logical model locations in the {@code mbd2} namespace
     */
    public static Set<ResourceLocation> findWorkspaceModelFiles() {
        var result = new LinkedHashSet<ResourceLocation>();
        var modelsPath = MBD2.getLocation().toPath().resolve("models");
        if (!Files.isDirectory(modelsPath)) {
            return result;
        }

        try (Stream<Path> files = Files.walk(modelsPath)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        var modelPath = modelsPath.relativize(path).toString().replace('\\', '/');
                        modelPath = modelPath.substring(0, modelPath.length() - ".json".length());
                        var location = ResourceLocation.tryBuild(MBD2.MOD_ID, modelPath);
                        if (location == null) {
                            MBD2.LOGGER.warn("Skipping invalid MBD2 workspace model path {}", modelPath);
                        } else {
                            result.add(location);
                        }
                    });
        } catch (IOException e) {
            MBD2.LOGGER.warn("Failed to scan MBD2 workspace models from {}", modelsPath, e);
        }
        return result;
    }

    /**
     * Scans saved MBD project files for renderer model references.
     *
     * <p>The scan walks the project root recursively and parses NBT project files with known suffixes. References are
     * collected from {@code modelLocation} and {@code itemTransformModel} string tags anywhere in the project tree.</p>
     *
     * @return ordered set of model locations referenced by saved projects
     */
    public static Set<ResourceLocation> findProjectReferencedModels() {
        var result = new LinkedHashSet<ResourceLocation>();
        var root = MBD2.getLocation().toPath();
        if (!Files.isDirectory(root)) {
            return result;
        }

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(WorkspaceResourceHelper::isProjectFile)
                    .forEach(path -> readProjectModelLocations(path, result));
        } catch (IOException e) {
            MBD2.LOGGER.warn("Failed to scan MBD2 project renderer model references from {}", root, e);
        }
        return result;
    }

    private static boolean isProjectFile(Path path) {
        var name = path.getFileName().toString();
        return PROJECT_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private static void readProjectModelLocations(Path path, Set<ResourceLocation> result) {
        try {
            var tag = NbtIo.read(path.toFile());
            if (tag != null) {
                collectModelLocations(tag, result);
            }
        } catch (IOException e) {
            MBD2.LOGGER.warn("Failed to read MBD2 project renderer model references from {}", path, e);
        }
    }

    private static void collectModelLocations(Tag tag, Set<ResourceLocation> result) {
        if (tag instanceof CompoundTag compoundTag) {
            for (var key : compoundTag.getAllKeys()) {
                var child = compoundTag.get(key);
                if (child == null) {
                    continue;
                }
                if (MODEL_LOCATION_KEYS.contains(key) && child.getId() == Tag.TAG_STRING) {
                    var location = ResourceLocation.tryParse(child.getAsString());
                    if (location != null) {
                        result.add(location);
                    }
                }
                collectModelLocations(child, result);
            }
        } else if (tag instanceof ListTag listTag) {
            for (var child : listTag) {
                collectModelLocations(child, result);
            }
        }
    }
}
