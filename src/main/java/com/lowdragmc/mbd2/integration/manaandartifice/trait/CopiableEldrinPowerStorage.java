package com.lowdragmc.mbd2.integration.manaandartifice.trait;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.mna.api.affinity.Affinity;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-affinity Eldrin power storage that can be copied for recipe simulation.
 */
public class CopiableEldrinPowerStorage implements ITagSerializable<CompoundTag>, IContentChangeAware {
    @Getter
    @Setter
    public Runnable onContentsChanged = () -> {
    };

    private final EnumMap<Affinity, Float> power = new EnumMap<>(Affinity.class);
    private float capacity;

    public CopiableEldrinPowerStorage(float capacity, List<Affinity> affinities) {
        this(capacity, affinities, Map.of());
    }

    public CopiableEldrinPowerStorage(float capacity, List<Affinity> affinities, Map<Affinity, Float> power) {
        this.capacity = Math.max(1, capacity);
        for (var affinity : affinities) {
            if (affinity != null && affinity != Affinity.UNKNOWN) {
                this.power.put(affinity, clamp(power.getOrDefault(affinity, 0f)));
            }
        }
        if (this.power.isEmpty()) {
            this.power.put(Affinity.ARCANE, 0f);
        }
    }

    public CopiableEldrinPowerStorage copy() {
        return new CopiableEldrinPowerStorage(capacity, List.copyOf(power.keySet()), power);
    }

    public List<Affinity> getAffinities() {
        return List.copyOf(power.keySet());
    }

    public boolean supplies(Affinity affinity) {
        return power.containsKey(affinity);
    }

    public float getCapacity(Affinity affinity) {
        return supplies(affinity) ? capacity : 0;
    }

    public float getCapacity() {
        return capacity;
    }

    public void setCapacity(float capacity) {
        this.capacity = Math.max(1, capacity);
        for (var affinity : power.keySet()) {
            power.put(affinity, clamp(power.get(affinity)));
        }
        onContentsChanged.run();
    }

    public float getCharge(Affinity affinity) {
        return power.getOrDefault(affinity, 0f);
    }

    public void setCharge(Affinity affinity, float amount) {
        if (supplies(affinity)) {
            var clamped = clamp(amount);
            if (Float.compare(getCharge(affinity), clamped) != 0) {
                power.put(affinity, clamped);
                onContentsChanged.run();
            }
        }
    }

    public float charge(Affinity affinity, float amount) {
        if (!supplies(affinity) || amount <= 0) {
            return 0;
        }
        var accepted = Math.min(amount, capacity - getCharge(affinity));
        if (accepted > 0) {
            setCharge(affinity, getCharge(affinity) + accepted);
        }
        return accepted;
    }

    public float consume(Affinity affinity, float amount) {
        if (!supplies(affinity) || amount <= 0) {
            return 0;
        }
        var extracted = Math.min(amount, getCharge(affinity));
        if (extracted > 0) {
            setCharge(affinity, getCharge(affinity) - extracted);
        }
        return extracted;
    }

    public float getTotalCharge() {
        return power.values().stream().reduce(0f, Float::sum);
    }

    public float getTotalCapacity() {
        return capacity * power.size();
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        for (var entry : power.entrySet()) {
            tag.putFloat(entry.getKey().name(), entry.getValue());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        for (var affinity : Affinity.values()) {
            if (power.containsKey(affinity) && nbt.contains(affinity.name(), Tag.TAG_FLOAT)) {
                power.put(affinity, clamp(nbt.getFloat(affinity.name())));
            }
        }
        onContentsChanged.run();
    }

    private float clamp(float amount) {
        return Math.max(0, Math.min(amount, capacity));
    }
}
