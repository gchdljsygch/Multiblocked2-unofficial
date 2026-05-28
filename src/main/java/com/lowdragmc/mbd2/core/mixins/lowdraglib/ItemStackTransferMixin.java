package com.lowdragmc.mbd2.core.mixins.lowdraglib;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.item.ItemTransferHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nonnull;

@Mixin(ItemStackTransfer.class)
public abstract class ItemStackTransferMixin {
    @Shadow(remap = false)
    protected abstract void onLoad();

    /**
     * @author pingsu
     * @reason
     */
    @Overwrite(remap = false)
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notifyChanges) {
        if (amount == 0)
            return ItemStack.EMPTY;

        ItemStackTransfer self = (ItemStackTransfer)(Object)this;
        int slots = self.getSlots();
        if (slot < 0 || slot >= slots) {
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + slots + ")");
        }

        ItemStack existing = self.getStackInSlot(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int toExtract = amount; // 关键修改：移除了 Math.min(amount, existing.getMaxStackSize())

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                self.setStackInSlot(slot, ItemStack.EMPTY);
                if (notifyChanges) {
                    self.onContentsChanged(slot);
                }
                return existing;
            } else {
                return existing.copy();
            }
        } else {
            if (!simulate) {
                ItemStack newStack = ItemTransferHelper.copyStackWithSize(existing, existing.getCount() - toExtract);
                self.setStackInSlot(slot, newStack);
                if (notifyChanges) {
                    self.onContentsChanged(slot);
                }
            }
            return ItemTransferHelper.copyStackWithSize(existing, toExtract);
        }
    }

    /**
     * @author
     * @reason
     */
    @Overwrite(remap = false)
    protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
        ItemStackTransfer self = (ItemStackTransfer)(Object)this;
        return Math.max(self.getSlotLimit(slot), 0);
    }

    /**
     * @author
     * @reason Preserve large stack counts during NBT serialization.
     */
    @Overwrite(remap = false)
    public CompoundTag serializeNBT() {
        ItemStackTransfer self = (ItemStackTransfer) (Object) this;
        ListTag itemList = new ListTag();

        for (int i = 0; i < self.getSlots(); i++) {
            ItemStack stack = self.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            CompoundTag itemTag = new CompoundTag();
            itemTag.putInt("Slot", i);

            ItemStack serializedStack = stack.copy();
            serializedStack.setCount(Math.min(Math.max(stack.getCount(), 1), 127));
            serializedStack.save(itemTag);
            itemTag.putInt("CountInt", stack.getCount());

            itemList.add(itemTag);
        }

        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", itemList);
        nbt.putInt("Size", self.getSlots());
        return nbt;
    }

    /**
     * @author
     * @reason Restore large stack counts from custom NBT field while keeping backward compatibility.
     */
    @Overwrite(remap = false)
    public void deserializeNBT(CompoundTag nbt) {
        ItemStackTransfer self = (ItemStackTransfer) (Object) this;
        self.setSize(nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : self.getSlots());

        ListTag itemList = nbt.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < itemList.size(); i++) {
            CompoundTag itemTag = itemList.getCompound(i);
            int slot = itemTag.getInt("Slot");
            if (slot < 0 || slot >= self.getSlots()) {
                continue;
            }

            ItemStack stack = ItemStack.of(itemTag);
            if (!stack.isEmpty() && itemTag.contains("CountInt", Tag.TAG_INT)) {
                stack.setCount(itemTag.getInt("CountInt"));
            }
            self.setStackInSlot(slot, stack);
        }

        onLoad();
    }
}
