package com.lowdragmc.mbd2.common.data;

import com.lowdragmc.lowdraglib.syncdata.IAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.NbtTagPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.StringPayload;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.syncdata.ChemicalStackAccessor;
import com.lowdragmc.mbd2.syncdata.MBDRecipeAccessor;
import com.lowdragmc.mbd2.syncdata.MBDRecipeTypeAccessor;

import static com.lowdragmc.lowdraglib.syncdata.TypedPayloadRegistries.register;

/**
 * Registers LowDragLib synced-field accessors for MBD2 domain objects.
 * <p>
 * These accessors teach the sync system how to serialize recipe instances,
 * recipe types, and optional Mekanism chemical stacks into typed payloads.
 * They must be registered before managed fields using those types are synced.
 * <p>
 * Thread safety: initialization is a startup action. Accessor registration is
 * global and should not be repeated with different accessor instances at
 * runtime.
 */
public class MBDSyncedFieldAccessors {
    /**
     * Accessor for syncing {@link com.lowdragmc.mbd2.api.recipe.MBDRecipe}
     * values through NBT payloads.
     */
    public static final IAccessor MBD_RECIPE_ACCESSOR = new MBDRecipeAccessor();
    /**
     * Accessor for syncing {@link com.lowdragmc.mbd2.api.recipe.MBDRecipeType}
     * values through string payloads.
     */
    public static final IAccessor MBD_RECIPE_TYPE_ACCESSOR = new MBDRecipeTypeAccessor();
    /**
     * Mekanism chemical stack accessor. Non-null only when Mekanism is loaded.
     */
    public static IAccessor CHEMICAL_STACK_ACCESSOR;

    /**
     * Registers all MBD2 sync accessors with LowDragLib's typed payload
     * registry.
     */
    public static void init() {
        register(NbtTagPayload.class, NbtTagPayload::new, MBD_RECIPE_ACCESSOR, 1000);
        register(StringPayload.class, StringPayload::new, MBD_RECIPE_TYPE_ACCESSOR, 1000);
        if (MBD2.isMekanismLoaded()) {
            CHEMICAL_STACK_ACCESSOR = new ChemicalStackAccessor();
            register(NbtTagPayload.class, NbtTagPayload::new, CHEMICAL_STACK_ACCESSOR, 1000);
        }
    }
}
