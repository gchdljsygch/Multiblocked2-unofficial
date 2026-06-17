package com.lowdragmc.mbd2.integration.arsnouveau.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;

/**
 * IO-filtered Source endpoint used when exposing MBD storage to external Ars Nouveau systems.
 *
 * <p>Read/drain operations are allowed only for {@link IO#OUT} or {@link IO#BOTH}; fill operations
 * are allowed only for {@link IO#IN} or {@link IO#BOTH}. Capacity metadata is still exposed so
 * relays can make normal routing decisions.</p>
 */
public class SourceStorageWrapper implements ISourceTile {
    private final ISourceTile storage;
    private final IO io;

    /**
     * @param storage backing Source storage
     * @param io access mode applied to external callers
     */
    public SourceStorageWrapper(ISourceTile storage, IO io) {
        this.storage = storage;
        this.io = io;
    }

    @Override
    public int getTransferRate() {
        return storage.getTransferRate();
    }

    @Override
    public boolean canAcceptSource() {
        return canReceive() && storage.canAcceptSource();
    }

    @Override
    public int getSource() {
        return canExtract() ? storage.getSource() : 0;
    }

    @Override
    public int getMaxSource() {
        return storage.getMaxSource();
    }

    @Override
    public void setMaxSource(int maxSource) {
        storage.setMaxSource(maxSource);
    }

    @Override
    public int setSource(int source) {
        return canReceive() ? storage.setSource(source) : storage.getSource();
    }

    @Override
    public int addSource(int source) {
        return canReceive() ? storage.addSource(source) : storage.getSource();
    }

    @Override
    public int removeSource(int source) {
        return canExtract() ? storage.removeSource(source) : storage.getSource();
    }

    /**
     * Returns whether external callers may add Source through this wrapper.
     */
    private boolean canReceive() {
        return io == IO.IN || io == IO.BOTH;
    }

    /**
     * Returns whether external callers may observe or remove Source through this wrapper.
     */
    private boolean canExtract() {
        return io == IO.OUT || io == IO.BOTH;
    }
}
