package com.lowdragmc.mbd2.integration.emi;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * EMI recipe transfer support for encoding MBD multiblock previews as AE2 processing patterns.
 */
public class AE2PatternEncodingEmiHandler implements EmiRecipeHandler<PatternEncodingTermMenu> {

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<PatternEncodingTermMenu> screen) {
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof MultiblockInfoEmiCategory.MultiblockInfoEmiRecipe;
    }

    @Override
    public boolean alwaysDisplaySupport(EmiRecipe recipe) {
        return supportsRecipe(recipe);
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<PatternEncodingTermMenu> context) {
        if (!supportsRecipe(recipe)) {
            return false;
        }
        var menu = context.getScreenHandler();
        if (recipe.getInputs().isEmpty() || recipe.getInputs().size() > menu.getProcessingInputSlots().length) {
            return false;
        }
        if (recipe.getOutputs().isEmpty() || toGenericStack(recipe.getOutputs().get(0)) == null) {
            return false;
        }
        for (var input : recipe.getInputs()) {
            if (toGenericStack(input) == null) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<PatternEncodingTermMenu> context) {
        if (!canCraft(recipe, context)) {
            return false;
        }

        var menu = context.getScreenHandler();
        menu.setMode(EncodingMode.PROCESSING);
        menu.mode = EncodingMode.PROCESSING;

        var inputSlots = menu.getProcessingInputSlots();
        for (var slot : inputSlots) {
            setFilter(slot, ItemStack.EMPTY);
        }
        var outputSlots = menu.getProcessingOutputSlots();
        for (var slot : outputSlots) {
            setFilter(slot, ItemStack.EMPTY);
        }

        var inputs = recipe.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            var stack = toGenericStack(inputs.get(i));
            if (stack == null) {
                return false;
            }
            setFilter(inputSlots[i], GenericStack.wrapInItemStack(stack));
        }

        var output = toGenericStack(recipe.getOutputs().get(0));
        if (output == null) {
            return false;
        }
        setFilter(outputSlots[0], GenericStack.wrapInItemStack(output));
        returnToEncodingScreen(context);
        return true;
    }

    private static void returnToEncodingScreen(EmiCraftContext<PatternEncodingTermMenu> context) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != context.getScreen()) {
            minecraft.setScreen(context.getScreen());
        }
    }

    private static void setFilter(FakeSlot slot, ItemStack stack) {
        slot.set(stack);
        NetworkHandler.instance().sendToServer(
                new InventoryActionPacket(InventoryAction.SET_FILTER, slot.index, stack));
    }

    @Nullable
    private static GenericStack toGenericStack(EmiIngredient ingredient) {
        long amount = Math.max(1L, ingredient.getAmount());
        for (var stack : ingredient.getEmiStacks()) {
            var genericStack = toGenericStack(stack, amount);
            if (genericStack != null) {
                return genericStack;
            }
        }
        return null;
    }

    @Nullable
    private static GenericStack toGenericStack(EmiStack stack) {
        return toGenericStack(stack, Math.max(1L, stack.getAmount()));
    }

    @Nullable
    private static GenericStack toGenericStack(EmiStack stack, long amount) {
        if (stack.isEmpty()) {
            return null;
        }

        Item item = stack.getKeyOfType(Item.class);
        if (item != null) {
            var itemStack = stack.getItemStack().copy();
            if (itemStack.isEmpty()) {
                itemStack = new ItemStack(item);
            }
            CompoundTag tag = stack.getNbt();
            itemStack.setTag(tag == null ? null : tag.copy());
            itemStack.setCount(toItemCount(amount));

            var wrapped = GenericStack.fromItemStack(itemStack);
            if (wrapped != null) {
                return new GenericStack(wrapped.what(), amount);
            }

            var key = AEItemKey.of(itemStack);
            return key == null ? null : new GenericStack(key, amount);
        }

        Fluid fluid = stack.getKeyOfType(Fluid.class);
        if (fluid != null) {
            return new GenericStack(AEFluidKey.of(fluid, stack.getNbt()), amount);
        }

        return null;
    }

    private static int toItemCount(long amount) {
        if (amount > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) amount);
    }
}
