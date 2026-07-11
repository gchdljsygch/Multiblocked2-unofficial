package com.lowdragmc.mbd2.api.registry;

import com.lowdragmc.mbd2.api.pattern.IMultiblockXEI;
import com.lowdragmc.mbd2.api.pattern.TemplateMultiblockXEI;
import com.lowdragmc.mbd2.api.pattern.TemplateMultiblockXEIData;
import com.lowdragmc.mbd2.common.machine.definition.MultiblockMachineDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Client-side registry for multiblock XEI pages.
 *
 * <p>All registered MBD multiblocks are included automatically. Addons only
 * need to call {@link #register(IMultiblockXEI)} for pages backed by another
 * multiblock implementation. Register entries during client setup, before JEI,
 * REI, or EMI performs recipe registration.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MultiblockXEIRegistry {
    private static final Map<ResourceLocation, IMultiblockXEI> EXTERNAL_ENTRIES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, TemplateMultiblockXEIData> DATA_PACK_ENTRIES = new LinkedHashMap<>();
    private static final List<Runnable> CHANGE_LISTENERS = new ArrayList<>();

    private MultiblockXEIRegistry() {
    }

    /**
     * Registers or replaces an external XEI page.
     *
     * <p>Replacing an entry with the same id is intentional: an addon can
     * provide a richer preview for an existing MBD definition without creating
     * duplicate pages.</p>
     *
     * @param entry external page definition
     */
    public static void register(IMultiblockXEI entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.getId(), "entry.id");
        synchronized (MultiblockXEIRegistry.class) {
            EXTERNAL_ENTRIES.put(entry.getId(), entry);
        }
        notifyChanged();
    }

    /**
     * Registers a multiblock definition without adding it to the MBD machine
     * definition registry.
     *
     * @param definition definition to expose in XEI
     */
    public static void register(MultiblockMachineDefinition definition) {
        register(IMultiblockXEI.of(definition));
    }

    /**
     * Removes an external page. Built-in pages discovered from
     * {@link MBDRegistries#MACHINE_DEFINITIONS} cannot be removed through this
     * method.
     *
     * @param id page id
     * @return whether an external page was removed
     */
    public static boolean unregister(ResourceLocation id) {
        boolean removed;
        synchronized (MultiblockXEIRegistry.class) {
            removed = id != null && EXTERNAL_ENTRIES.remove(id) != null;
        }
        if (removed) {
            notifyChanged();
        }
        return removed;
    }

    /**
     * Replaces the client-side snapshot received from the server data-pack
     * loader.
     *
     * <p>The snapshot is authoritative: entries removed by a data-pack reload
     * disappear from the registry. A data-pack entry with an id matching a
     * built-in or external entry takes precedence in {@link #entries()}.</p>
     *
     * @param entries complete data-pack entry snapshot
     */
    public static void setDataPackEntries(Collection<TemplateMultiblockXEIData> entries) {
        synchronized (MultiblockXEIRegistry.class) {
            DATA_PACK_ENTRIES.clear();
            if (entries != null) {
                for (TemplateMultiblockXEIData entry : entries) {
                    if (entry != null) {
                        DATA_PACK_ENTRIES.put(entry.id(), entry);
                    }
                }
            }
        }
        notifyChanged();
    }

    /**
     * Clears all server-provided entries when the client leaves a world.
     */
    public static void clearDataPackEntries() {
        setDataPackEntries(List.of());
    }

    /**
     * Adds a callback invoked after any XEI registry change.
     *
     * @param listener callback, normally an XEI integration runtime refresh
     */
    public static synchronized void addChangeListener(Runnable listener) {
        if (listener != null && !CHANGE_LISTENERS.contains(listener)) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    /**
     * Removes a previously registered change callback.
     *
     * @param listener callback to remove
     */
    public static synchronized void removeChangeListener(Runnable listener) {
        CHANGE_LISTENERS.remove(listener);
    }

    /**
     * Returns the complete set of pages consumed by JEI, REI, and EMI.
     *
     * <p>Built-in definitions are inserted first and external entries are
     * inserted afterwards, so an external entry with a matching id replaces
     * the built-in page. The returned list is a snapshot and can be retained by
     * an XEI implementation during its registration callback.</p>
     *
     * @return ordered snapshot of all XEI pages
     */
    public static synchronized List<IMultiblockXEI> entries() {
        Map<ResourceLocation, IMultiblockXEI> entries = new LinkedHashMap<>();
        for (var definition : MBDRegistries.MACHINE_DEFINITIONS.values()) {
            if (definition instanceof MultiblockMachineDefinition multiblockDefinition) {
                entries.put(multiblockDefinition.id(), IMultiblockXEI.of(multiblockDefinition));
            }
        }
        entries.putAll(EXTERNAL_ENTRIES);
        DATA_PACK_ENTRIES.values().forEach(data -> entries.put(data.id(), new TemplateMultiblockXEI(data)));
        return List.copyOf(entries.values());
    }

    private static void notifyChanged() {
        List<Runnable> listeners;
        synchronized (MultiblockXEIRegistry.class) {
            listeners = List.copyOf(CHANGE_LISTENERS);
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable throwable) {
                // An integration callback must not prevent other XEI listeners
                // from seeing a data-pack update.
                com.lowdragmc.mbd2.MBD2.LOGGER.error("Failed to refresh an XEI integration", throwable);
            }
        }
    }
}
