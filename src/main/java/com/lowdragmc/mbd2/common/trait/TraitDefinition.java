package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.syncdata.IAutoPersistedSerializable;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineTraitPanel;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Editable definition for one machine trait type.
 *
 * <p>The business goal is to store the project/configuration data needed to
 * create a runtime {@link ITrait}, render it in the editor, and persist it in a
 * machine definition file. Definitions are mutable editor state and should be
 * edited on the UI or definition-loading thread, then treated as read-only by
 * runtime machines.</p>
 */
@Getter
@Setter
public abstract class TraitDefinition implements IConfigurable, IAutoPersistedSerializable {
    /**
     * Serializes a trait definition for project files or synced editor data.
     *
     * <p>Side effects are delegated to the definition's auto-persisted
     * serializer. The returned tag includes the definition type metadata required
     * by {@link #deserializeDefinition(CompoundTag)}.</p>
     *
     * @param definition definition to encode; must not be {@code null}
     * @return compound containing all persisted definition fields
     */
    public static CompoundTag serializeDefinition(TraitDefinition definition) {
        return definition.serializeNBT();
    }

    /**
     * Recreates a trait definition from persisted NBT.
     *
     * <p>Business goal: route serialized definition data through
     * {@link MBDRegistries#TRAIT_DEFINITION_TYPES} so addon traits can restore
     * their own definition classes. Side effects: creates a new definition
     * instance and mutates it through {@link #deserializeNBT(CompoundTag)}.</p>
     *
     * @param tag source compound containing a {@code _type} string
     * @return deserialized definition, or {@code null} when the type id is not
     * registered
     */
    @Nullable
    public static TraitDefinition deserializeDefinition(CompoundTag tag) {
        var type = tag.getString("_type");
        var wrapper = MBDRegistries.TRAIT_DEFINITION_TYPES.get(type);
        if (wrapper != null) {
            var definition = wrapper.creator().get();
            definition.deserializeNBT(tag);
            return definition;
        }
        return null;
    }

    @Configurable(name = "config.definition.trait.name")
    private String name = name();

    @Configurable(name = "config.definition.trait.priority", tips = "config.definition.trait.priority.tooltip")
    @NumberRange(range = {Integer.MIN_VALUE, Integer.MAX_VALUE})
    private int priority;

    /**
     * Creates a runtime trait for a concrete machine.
     *
     * <p>Preconditions: call after the owning {@link MBDMachine} has been
     * constructed and this definition has been fully loaded. Side effects are
     * implementation-specific object construction only; world mutation should be
     * delayed to trait lifecycle callbacks.</p>
     *
     * @param machine machine that will own the created trait
     * @return new runtime trait instance bound to the machine
     */
    public abstract ITrait createTrait(MBDMachine machine);

    /**
     * Returns the icon used by the machine editor trait list.
     *
     * @return non-null icon texture for this trait definition
     */
    public abstract IGuiTexture getIcon();

    /**
     * Returns whether a machine definition may contain multiple traits of this
     * type.
     *
     * @return {@code true} when multiple instances are valid; {@code false} when
     * the editor should keep only one definition of this type
     */
    public boolean allowMultiple() {
        return true;
    }

    /**
     * Returns an optional block-entity renderer contributed by this trait.
     *
     * <p>Side effects: none expected. Called on client render setup paths; server
     * logic should not depend on the returned renderer.</p>
     *
     * @param machine machine whose trait renderer is being requested
     * @return renderer to compose into the machine render pipeline, or
     * {@link IRenderer#EMPTY} when this trait has no renderer
     */
    public IRenderer getBESRenderer(IMachine machine) {
        return IRenderer.EMPTY;
    }

    /**
     * Returns the translation key used for the definition name.
     *
     * @return key in {@code config.definition.<group>.<name>.name} form
     */
    @Override
    public String getTranslateKey() {
        return "config.definition.%s.%s.name".formatted(this.group(), this.name());
    }

    /**
     * Performs extra editor rendering after the preview world has rendered.
     *
     * <p>Client-side only. Side effects should be limited to drawing or updating
     * temporary editor preview state.</p>
     *
     * @param panel trait panel currently displaying this definition
     */
    @OnlyIn(Dist.CLIENT)
    public void renderAfterWorldInTraitPanel(MachineTraitPanel panel) {
    }
}
