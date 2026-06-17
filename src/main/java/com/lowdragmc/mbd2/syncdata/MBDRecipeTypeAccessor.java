package com.lowdragmc.mbd2.syncdata;

import com.lowdragmc.lowdraglib.syncdata.AccessorOp;
import com.lowdragmc.lowdraglib.syncdata.accessor.CustomObjectAccessor;
import com.lowdragmc.lowdraglib.syncdata.payload.ITypedPayload;
import com.lowdragmc.lowdraglib.syncdata.payload.StringPayload;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Sync-data accessor for serializing recipe type references by registry name.
 */
public class MBDRecipeTypeAccessor extends CustomObjectAccessor<MBDRecipeType> {

    public MBDRecipeTypeAccessor() {
        super(MBDRecipeType.class, true);
    }

    @Override
    public ITypedPayload<?> serialize(AccessorOp accessorOp, MBDRecipeType recipeType) {
        return StringPayload.of(recipeType.getRegistryName().toString());
    }

    @Override
    public MBDRecipeType deserialize(AccessorOp accessorOp, ITypedPayload<?> payload) {
        if (payload instanceof StringPayload stringPayload) {
            var id = ResourceLocation.tryParse(stringPayload.getPayload());
            return id == null ? null : MBDRegistries.RECIPE_TYPES.get(id);
        }
        return null;
    }
}
