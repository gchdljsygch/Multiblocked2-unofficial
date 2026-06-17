package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Trait facet that exposes a Forge capability.
 *
 * <p>The business goal is to let machine traits answer block-entity capability
 * queries without forcing every trait to be a full block entity. Implementations
 * usually return side-filtered wrappers from {@link #getCapContent(IO)} and may
 * merge several wrappers when multiple traits provide the same capability. Calls
 * happen on normal Forge capability lookup paths and should avoid expensive
 * mutation.</p>
 *
 * @param <T> capability content type, such as an item handler, fluid handler, or
 *            energy storage
 */
public interface ICapabilityProviderTrait<T> {

    /**
     * Resolves capability IO direction for a queried side.
     *
     * @param side queried world side, or {@code null} for internal/unsided access
     * @return capability access allowed on that side
     */
    IO getCapabilityIO(@Nullable Direction side);

    /**
     * Returns the Forge capability token exposed by this trait.
     *
     * @return capability accepted by {@link ICapabilityProvider#getCapability}
     */
    Capability<? super T> getCapability();

    /**
     * Returns the side-filtered capability object.
     *
     * <p>Side effects should be limited to creating lightweight wrappers or
     * returning cached wrappers; the returned object determines what external
     * callers can insert, extract, read, or mutate.</p>
     *
     * @param capabilityIO effective IO for the queried side
     * @return capability content object for Forge to expose
     */
    T getCapContent(IO capabilityIO);

    /**
     * Merges capability contents from multiple providers.
     *
     * <p>Business goal: let the block entity expose one combined capability when
     * several traits provide the same Forge capability. The default returns the
     * first provided content, or creates this trait's unsided content when the
     * list is empty.</p>
     *
     * @param contents capability contents collected from compatible providers
     * @return merged capability content exposed to Forge
     */
    default T mergeContents(List<T> contents) {
        return !contents.isEmpty() ? contents.get(0) : getCapContent(getCapabilityIO(null));
    }
}
