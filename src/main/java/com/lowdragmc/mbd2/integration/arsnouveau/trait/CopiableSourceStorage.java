package com.lowdragmc.mbd2.integration.arsnouveau.trait;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.IntTag;

public class CopiableSourceStorage implements ISourceTile, ITagSerializable<IntTag>, IContentChangeAware {
    @Getter
    @Setter
    public Runnable onContentsChanged = () -> {};

    private final int transferRate;
    private int maxSource;
    private int source;

    public CopiableSourceStorage(int maxSource, int transferRate) {
        this(maxSource, transferRate, 0);
    }

    public CopiableSourceStorage(int maxSource, int transferRate, int source) {
        this.maxSource = Math.max(1, maxSource);
        this.transferRate = Math.max(1, transferRate);
        this.source = clamp(source);
    }

    public CopiableSourceStorage copy() {
        return new CopiableSourceStorage(maxSource, transferRate, source);
    }

    @Override
    public int getTransferRate() {
        return transferRate;
    }

    @Override
    public boolean canAcceptSource() {
        return source < maxSource;
    }

    @Override
    public int getSource() {
        return source;
    }

    @Override
    public int getMaxSource() {
        return maxSource;
    }

    @Override
    public void setMaxSource(int maxSource) {
        this.maxSource = Math.max(1, maxSource);
        setSource(source);
    }

    @Override
    public int setSource(int source) {
        var clamped = clamp(source);
        if (this.source != clamped) {
            this.source = clamped;
            onContentsChanged.run();
        }
        return this.source;
    }

    @Override
    public int addSource(int source) {
        return setSource(this.source + Math.max(0, source));
    }

    @Override
    public int removeSource(int source) {
        return setSource(this.source - Math.max(0, source));
    }

    @Override
    public IntTag serializeNBT() {
        return IntTag.valueOf(source);
    }

    @Override
    public void deserializeNBT(IntTag nbt) {
        setSource(nbt.getAsInt());
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, maxSource));
    }
}
