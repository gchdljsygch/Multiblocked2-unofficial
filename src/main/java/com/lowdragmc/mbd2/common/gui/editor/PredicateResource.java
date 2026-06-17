package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.lowdragmc.mbd2.common.gui.editor.PredicateResource.RESOURCE_NAME;

/**
 * Editor resource type that stores reusable multiblock {@link SimplePredicate} definitions.
 *
 * <p>The resource is backed by the LDLib workspace {@code assets/resources/predicates} directory and always provides
 * built-in {@code any} and {@code air} predicates. Serialized values use the predicate wrapper format so editor
 * resources can survive registration changes across reloads.</p>
 */
@LDLRegister(name = RESOURCE_NAME, group = "resource")
public class PredicateResource extends Resource<SimplePredicate> {
    /**
     * Resource group name used by LowDragLib panels and project NBT.
     */
    public final static String RESOURCE_NAME = "mbd2.gui.editor.group.predicate";

    /**
     * Creates the predicate resource and registers built-in predicates.
     */
    public PredicateResource() {
        super(new File(LDLib.getLDLibDir(), "assets/resources/predicates"));
        addBuiltinResource("any", SimplePredicate.ANY);
        addBuiltinResource("air", SimplePredicate.AIR);
    }

    /**
     * Returns the stable resource name used in editor UI and project data.
     */
    @Override
    public String name() {
        return RESOURCE_NAME;
    }

    /**
     * Creates the predicate resource container shown in the resource panel.
     *
     * @param resourcePanel owning editor resource panel
     * @return container widget for predicate previews and actions
     */
    @Override
    public ResourceContainer<SimplePredicate, ? extends Widget> createContainer(ResourcePanel resourcePanel) {
        return new PredicateResourceContainer(this, resourcePanel);
    }

    /**
     * Serializes a predicate resource entry.
     *
     * @param predicate predicate to serialize
     * @return wrapper tag, or {@code null} when the predicate cannot be serialized
     */
    @Nullable
    @Override
    public Tag serialize(SimplePredicate predicate) {
        return SimplePredicate.serializeWrapper(predicate);
    }

    /**
     * Deserializes a predicate resource entry.
     *
     * @param tag stored predicate wrapper
     * @return deserialized predicate, or {@link SimplePredicate#ANY} for invalid data
     */
    @Override
    public SimplePredicate deserialize(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            return SimplePredicate.deserializeWrapper(compoundTag);
        }
        return SimplePredicate.ANY;
    }

    /**
     * Loads built-in predicate resources from NBT while preserving the default {@code any} and {@code air} entries.
     *
     * @param nbt serialized resource map
     */
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        getBuiltinResources().clear();
        addBuiltinResource("any", SimplePredicate.ANY);
        addBuiltinResource("air", SimplePredicate.AIR);
        for (String key : nbt.getAllKeys()) {
            addBuiltinResource(key, deserialize(nbt.get(key)));
        }
    }
}
