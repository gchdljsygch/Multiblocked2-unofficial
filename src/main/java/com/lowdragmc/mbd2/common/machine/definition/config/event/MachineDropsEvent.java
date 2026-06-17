package com.lowdragmc.mbd2.common.machine.definition.config.event;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.graphprocessor.data.parameter.ExposedParameter;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterGet;
import com.lowdragmc.mbd2.common.graphprocessor.GraphParameterSet;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fired when a block-backed machine contributes item drops.
 * <p>
 * The mutable {@link #drops} list is the actual drop list being assembled.
 * Graph handlers receive the original list as {@code drops.in} and can replace
 * its contents by returning a list through {@code drops.out}. Non-ItemStack
 * values in {@code drops.out} are ignored.
 */
@Getter
@LDLRegister(name = "MachineDropsEvent", group = "MachineEvent")
public class MachineDropsEvent extends MachineEvent {
    /**
     * Entity responsible for the drop operation, or the context entity supplied
     * by the caller.
     */
    @GraphParameterGet
    public final Entity entity;
    /**
     * Mutable drop list. Adding, removing, or replacing entries affects the
     * drops spawned by the caller.
     */
    @GraphParameterGet(identity = "drops.in")
    @GraphParameterSet(identity = "drops.out")
    public List<ItemStack> drops;

    /**
     * Creates a drop event backed by the caller's mutable drop list.
     * <p>
     * Side effects after graph processing may include clearing and repopulating {@code drops} from {@code drops.out}.
     * Pass a mutable list; immutable lists will fail if handlers attempt replacement.
     *
     * @param machine machine contributing drops
     * @param entity  entity context for the drop operation, such as the breaker or cause
     * @param drops   mutable drop list that the caller will later spawn or return
     */
    public MachineDropsEvent(MBDMachine machine, Entity entity, List<ItemStack> drops) {
        super(machine);
        this.entity = entity;
        this.drops = drops;
    }

    @Override
    public void bindParameters(Map<String, ExposedParameter> exposedParameters) {
        super.bindParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("entity")).ifPresent(p -> p.setValue(entity));
        Optional.ofNullable(exposedParameters.get("drops.in")).ifPresent(p -> p.setValue(drops));
    }

    @Override
    public void gatherParameters(Map<String, ExposedParameter> exposedParameters) {
        super.gatherParameters(exposedParameters);
        Optional.ofNullable(exposedParameters.get("drops.out")).ifPresent(p -> {
            if (p.getValue() instanceof List list) {
                if (list.isEmpty()) {
                    drops.clear();
                } else {
                    drops.clear();
                    for (Object o : list) {
                        if (o instanceof ItemStack itemStack) {
                            drops.add(itemStack);
                        }
                    }
                }
            }
        });
    }
}
