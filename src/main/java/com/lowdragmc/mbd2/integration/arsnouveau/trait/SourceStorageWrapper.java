package com.lowdragmc.mbd2.integration.arsnouveau.trait;

import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;

public class SourceStorageWrapper implements ISourceTile {
    private final ISourceTile storage;
    private final IO io;

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

    private boolean canReceive() {
        return io == IO.IN || io == IO.BOTH;
    }

    private boolean canExtract() {
        return io == IO.OUT || io == IO.BOTH;
    }
}
