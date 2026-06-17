package com.lowdragmc.mbd2.common.machine.definition.config;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Serializable tree and lookup table of {@link MachineState}s.
 * <p>
 * The root state owns the persisted tree. {@link #initStateMachine()} rebuilds
 * parent links and the name-to-state map used by runtime machines when they
 * switch states. Missing state lookups fall back to the root state instead of
 * returning {@code null}.
 *
 * @param <T> concrete machine state type stored by this state machine
 */
public class StateMachine<T extends MachineState> implements ITagSerializable<CompoundTag> {
    @Getter
    protected final T rootState;
    // runtime
    protected final Map<String, T> states = new HashMap<>();

    /**
     * Creates a state machine from a root state and initializes runtime lookup
     * data.
     *
     * @param rootState root state for the machine definition
     */
    public StateMachine(T rootState) {
        this.rootState = rootState;
        initStateMachine();
    }

    /**
     * Creates the default state tree for a single-block/entity machine.
     * <p>
     * The tree contains {@code base}, {@code working}, {@code waiting}, and
     * {@code suspend} states.
     *
     * @param builderCreator supplier for concrete state builders
     * @param renderer       renderer assigned to the root {@code base} state
     * @param <T>            concrete state type
     * @return root state of the default tree
     */
    public static <T extends MachineState> T createSingleDefault(Supplier<MachineState.Builder<T>> builderCreator, IRenderer renderer) {
        var builder = builderCreator.get().name("base")
                .renderer(renderer)
                .shape(Shapes.block())
                .lightLevel(0)
                .child(builderCreator.get()
                        .name("working")
                        .child(builderCreator.get()
                                .name("waiting")
                                .build())
                        .build())
                .child(builderCreator.get()
                        .name("suspend")
                        .build());
        return builder.build();
    }

    /**
     * Creates the default state tree for a multiblock controller.
     * <p>
     * The tree contains {@code base}, {@code formed}, {@code working},
     * {@code waiting}, and {@code suspend} states.
     *
     * @param builderCreator supplier for concrete state builders
     * @param renderer       renderer assigned to the root {@code base} state
     * @param <T>            concrete state type
     * @return root state of the default tree
     */
    public static <T extends MachineState> T createMultiblockDefault(Supplier<MachineState.Builder<T>> builderCreator, IRenderer renderer) {
        var builder = builderCreator.get().name("base")
                .renderer(renderer)
                .shape(Shapes.block())
                .lightLevel(0)
                .child(builderCreator.get()
                        .name("formed")
                        .child(builderCreator.get()
                                .name("working")
                                .child(builderCreator.get()
                                        .name("waiting")
                                        .build())
                                .build())
                        .child(builderCreator.get()
                                .name("suspend")
                                .build())
                        .build());
        return builder.build();
    }

    /**
     * Creates a default single-machine state tree with no renderer.
     *
     * @param builderCreator supplier for concrete state builders
     * @param <T>            concrete state type
     * @return root state of the default tree
     */
    public static <T extends MachineState> T createDefault(Supplier<MachineState.Builder<T>> builderCreator) {
        return createSingleDefault(builderCreator, IRenderer.EMPTY);
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        tag.put("root", rootState.serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        rootState.deserializeNBT(tag.getCompound("root"));
        initStateMachine();
    }

    /**
     * Initialize the state machine. It should be called after the state machine is changed.
     */
    public void initStateMachine() {
        states.clear();
        this.rootState.init(this);
    }

    /**
     * Add a state to the state machine. It should be called only in the {@link MachineState#init(StateMachine)} method.
     * <br/>
     * In general, you don't need to call this method. it will be called automatically during {@link StateMachine#initStateMachine()}
     */
    protected void addState(T state) {
        states.put(state.name(), state);
    }

    /**
     * Looks up a state by name.
     *
     * @param name state name
     * @return matching state, or the root state when no state is registered with
     * that name
     */
    public T getState(String name) {
        return states.getOrDefault(name, rootState);
    }

    /**
     * Checks whether a state name exists.
     *
     * @param name state name
     * @return {@code true} when the state is registered
     */
    public boolean hasState(String name) {
        return states.containsKey(name);
    }
}
