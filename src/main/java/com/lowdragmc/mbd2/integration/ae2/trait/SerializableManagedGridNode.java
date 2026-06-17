package com.lowdragmc.mbd2.integration.ae2.trait;

import appeng.api.networking.IGridNodeListener;
import appeng.me.ManagedGridNode;
import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;

/**
 * Managed AE2 grid node that participates in LDLib persistence and content-change callbacks.
 */
@Getter
@Setter
public class SerializableManagedGridNode extends ManagedGridNode implements ITagSerializable<CompoundTag>, IContentChangeAware {

    private Runnable onContentsChanged = () -> {
    };

    /**
     * Creates a managed AE2 grid node that can be serialized by LDLib.
     * <p>
     * The node is not created in the world by this constructor; callers should call AE2's create/destroy lifecycle from
     * the owning machine/server thread.
     *
     * @param nodeOwner owner object passed back to the node listener
     * @param listener  AE2 grid-node listener for state changes
     * @param <T>       owner type expected by the listener
     */
    public <T> SerializableManagedGridNode(T nodeOwner, IGridNodeListener<? super T> listener) {
        super(nodeOwner, listener);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        saveToNBT(tag);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        loadFromNBT(nbt);
    }
}
