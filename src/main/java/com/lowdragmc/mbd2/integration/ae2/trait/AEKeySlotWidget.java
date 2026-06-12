package com.lowdragmc.mbd2.integration.ae2.trait;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.util.ConfigInventory;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget;
import com.lowdragmc.lowdraglib.gui.ingredient.IRecipeIngredientSlot;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.ClickableIngredient;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.jei.JEIPlugin;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.stack.EmiStack;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

@LDLRegister(name = "ae_key_slot", group = "widget.container", modID = "ae2")
@Accessors(chain = true)
public class AEKeySlotWidget extends Widget implements IRecipeIngredientSlot, IGhostIngredientTarget, IConfigurableWidget {
    private static final int UPDATE_STACK = 0;
    private static final int ACTION_SET_STACK = 0;
    private static final int ACTION_SET_AMOUNT = 1;
    private static final String SET_AMOUNT_TITLE = "mbd2.ae_key_slot.set_amount";
    private static final String SET_AMOUNT_TOOLTIP = "mbd2.ae_key_slot.set_amount.tooltip";
    private static final String SET_AMOUNT_VALUE = "mbd2.ae_key_slot.set_amount.value";

    @Nullable
    @Getter
    protected ConfigInventory inventory;
    @Getter
    protected int slotIndex;
    @Configurable(name = "ldlib.gui.editor.name.canTakeItems")
    protected boolean canTakeItems = true;
    @Configurable(name = "ldlib.gui.editor.name.canPutItems")
    protected boolean canPutItems = true;
    protected boolean canAcceptPhantom = true;
    protected boolean canSetAmount = true;
    @Configurable(name = "ldlib.gui.editor.name.showAmount")
    @Setter
    protected boolean showAmount = true;
    @Configurable(name = "ldlib.gui.editor.name.drawHoverOverlay")
    @Setter
    public boolean drawHoverOverlay = true;
    @Configurable(name = "ldlib.gui.editor.name.drawHoverTips")
    @Setter
    protected boolean drawHoverTips = true;
    @Setter
    protected BiConsumer<AEKeySlotWidget, List<Component>> onAddedTooltips;
    @Setter
    @Getter
    protected IngredientIO ingredientIO = IngredientIO.RENDER_ONLY;
    @Setter
    @Getter
    protected float XEIChance = 1f;
    @Setter
    protected Runnable changeListener;
    @Nullable
    @Getter
    protected GenericStack lastStack;

    public AEKeySlotWidget() {
        this(null, 0, 0, 0);
    }

    public AEKeySlotWidget(@Nullable ConfigInventory inventory, int slotIndex, int x, int y) {
        super(x, y, 18, 18);
        this.inventory = inventory;
        this.slotIndex = slotIndex;
    }

    @Override
    public void initTemplate() {
        setBackground(SlotWidget.ITEM_SLOT_TEXTURE);
    }

    @Override
    public AEKeySlotWidget setClientSideWidget() {
        super.setClientSideWidget();
        this.lastStack = getInventoryStack();
        return this;
    }

    public AEKeySlotWidget setConfigInventory(@Nullable ConfigInventory inventory, int slotIndex) {
        this.inventory = inventory;
        this.slotIndex = slotIndex;
        if (isClientSideWidget) {
            this.lastStack = getInventoryStack();
        }
        return this;
    }

    public AEKeySlotWidget setAEStack(@Nullable GenericStack stack) {
        setStack(stack);
        return this;
    }

    public AEKeySlotWidget setCanTakeItems(boolean canTakeItems) {
        this.canTakeItems = canTakeItems;
        return this;
    }

    public AEKeySlotWidget setCanPutItems(boolean canPutItems) {
        this.canPutItems = canPutItems;
        return this;
    }

    public AEKeySlotWidget setCanAcceptPhantom(boolean canAcceptPhantom) {
        this.canAcceptPhantom = canAcceptPhantom;
        return this;
    }

    public AEKeySlotWidget setCanSetAmount(boolean canSetAmount) {
        this.canSetAmount = canSetAmount;
        return this;
    }

    public List<Component> getAdditionalToolTips(List<Component> list) {
        if (this.onAddedTooltips != null) {
            this.onAddedTooltips.accept(this, list);
        }
        return list;
    }

    @Override
    public List<Component> getTooltipTexts() {
        List<Component> tooltips = getAdditionalToolTips(new ArrayList<>());
        tooltips.addAll(tooltipTexts);
        return tooltips;
    }

    @Override
    public List<Component> getFullTooltipTexts() {
        var tooltips = new ArrayList<Component>();
        var stack = getDisplayStack();
        if (stack != null) {
            tooltips.addAll(AEKeyRendering.getTooltip(stack.what()));
            tooltips.add(Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL)));
            if (canSetAmount && maySetStackOnThisSide(stack)) {
                tooltips.add(Component.translatable(SET_AMOUNT_TOOLTIP));
            }
        }
        tooltips.addAll(getTooltipTexts());
        return tooltips;
    }

    @Override
    public List<Object> getXEIIngredients() {
        var stack = getDisplayStack();
        if (stack == null) {
            return Collections.emptyList();
        }
        var jeiIngredient = getJEIClickableIngredient(stack);
        if (jeiIngredient != null) {
            return List.of(jeiIngredient);
        }
        var wrappedStack = GenericStack.wrapInItemStack(stack);
        if (LDLib.isReiLoaded()) {
            return SlotWidget.REICallWrapper.getReiIngredients(wrappedStack);
        } else if (LDLib.isEmiLoaded()) {
            return SlotWidget.EMICallWrapper.getEmiIngredients(wrappedStack, getXEIChance());
        }
        return List.of(wrappedStack);
    }

    @Override
    public @Nullable Object getXEICurrentIngredient() {
        var stack = getDisplayStack();
        if (stack == null) {
            return null;
        }
        var jeiIngredient = getJEIClickableIngredient(stack);
        if (jeiIngredient != null) {
            return jeiIngredient;
        }
        if (LDLib.isEmiLoaded()) {
            return SlotWidget.EMICallWrapper.getEmiIngredient(GenericStack.wrapInItemStack(stack), getXEIChance());
        }
        return null;
    }

    @Nullable
    private Object getJEIClickableIngredient(GenericStack stack) {
        if (LDLib.isJeiLoaded() && JEIPlugin.jeiHelpers != null) {
            var ingredient = appeng.integration.modules.jei.GenericEntryStackHelper.stackToIngredient(
                    JEIPlugin.jeiHelpers.getIngredientManager(), stack);
            if (ingredient != null) {
                var pos = getPosition();
                var size = getSize();
                return new ClickableIngredient<>(ingredient, pos.x, pos.y, size.width, size.height);
            }
        }
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        if (isClientSideWidget && inventory != null) {
            this.lastStack = getInventoryStack();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var stack = getDisplayStack();
        var pos = getPosition();
        if (stack != null) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, pos.x + 1, pos.y + 1, stack.what());
            if (showAmount && stack.amount() > 0) {
                StackSizeRenderer.renderSizeLabel(graphics, Minecraft.getInstance().font, pos.x + 1, pos.y + 1,
                        stack.what().formatAmount(stack.amount(), AmountFormat.SLOT));
            }
        }
        drawOverlay(graphics, mouseX, mouseY, partialTicks);
        if (drawHoverOverlay && isMouseOverElement(mouseX, mouseY) && getHoverElement(mouseX, mouseY) == this) {
            RenderSystem.colorMask(true, true, true, false);
            DrawerHelper.drawSolidRect(graphics, pos.x + 1, pos.y + 1, getSize().width - 2, getSize().height - 2, 0x80FFFFFF);
            RenderSystem.colorMask(true, true, true, true);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (drawHoverTips && isMouseOverElement(mouseX, mouseY) && getHoverElement(mouseX, mouseY) == this) {
            if (gui != null) {
                gui.getModularUIGui().setHoverTooltip(getFullTooltipTexts(), GenericStack.wrapInItemStack(getDisplayStack()), null, null);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1f);
        } else {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY) || gui == null) {
            return false;
        }
        if (button == 1 && canTakeItems && mayClearStackOnThisSide()) {
            setStack(null);
            writeClientAction(ACTION_SET_STACK, buffer -> GenericStack.writeBuffer(null, buffer));
            playButtonClickSound();
            return true;
        }
        if (button == 2 && canSetAmount) {
            var stack = getDisplayStack();
            if (stack != null && maySetAmountOnThisSide(stack)) {
                openAmountDialog(stack);
                playButtonClickSound();
                return true;
            }
        }
        if (button == 0 && canPutItems) {
            var stack = toGenericStack(gui.getModularUIContainer().getCarried());
            if (stack != null && maySetStackOnThisSide(stack)) {
                setStack(stack);
                writeClientAction(ACTION_SET_STACK, buffer -> GenericStack.writeBuffer(stack, buffer));
                playButtonClickSound();
                return true;
            }
        }
        if (LDLib.isEmiLoaded()) {
            return SlotWidget.EMICallWrapper.mouseClicked(getXEICurrentIngredient(), button);
        }
        return false;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        var stack = toGenericStack(ingredient);
        if (stack == null || !mayAcceptPhantomOnThisSide(stack)) {
            return Collections.emptyList();
        }
        Rect2i rectangle = toRectangleBox();
        return List.of(new Target() {
            @Nonnull
            @Override
            public Rect2i getArea() {
                return rectangle;
            }

            @Override
            public void accept(Object ingredient) {
                var accepted = toGenericStack(ingredient);
                if (accepted != null && mayAcceptPhantomOnThisSide(accepted)) {
                    setStack(accepted);
                    writeClientAction(ACTION_SET_STACK, buffer -> GenericStack.writeBuffer(accepted, buffer));
                }
            }
        });
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        var stack = getInventoryStack();
        if (!Objects.equals(stack, lastStack)) {
            this.lastStack = stack;
            writeUpdateInfo(UPDATE_STACK, buffer -> GenericStack.writeBuffer(stack, buffer));
            notifyChangeListener();
        }
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        var stack = getInventoryStack();
        this.lastStack = stack;
        GenericStack.writeBuffer(stack, buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        this.lastStack = GenericStack.readBuffer(buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == UPDATE_STACK) {
            this.lastStack = GenericStack.readBuffer(buffer);
            notifyChangeListener();
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_SET_STACK) {
            setStack(GenericStack.readBuffer(buffer));
            syncStackToClient();
        } else if (id == ACTION_SET_AMOUNT) {
            var requested = GenericStack.readBuffer(buffer);
            if (requested != null) {
                setStackAmount(requested);
                syncStackToClient();
            }
        } else {
            super.handleClientAction(id, buffer);
        }
    }

    @Nullable
    public static GenericStack toGenericStack(@Nullable Object ingredient) {
        if (ingredient instanceof GenericStack stack) {
            return stack;
        }
        if (ingredient instanceof ItemStack itemStack) {
            return GenericStack.fromItemStack(itemStack);
        }
        if (ingredient instanceof net.minecraftforge.fluids.FluidStack fluidStack) {
            return GenericStack.fromFluidStack(fluidStack);
        }
        if (ingredient instanceof FluidStack fluidStack) {
            return fluidStack.isEmpty() ? null : new GenericStack(AEFluidKey.of(fluidStack.getFluid(), fluidStack.getTag()), fluidStack.getAmount());
        }
        if (LDLib.isEmiLoaded() && ingredient instanceof EmiStack emiStack) {
            return toGenericStack(emiStack);
        }
        if (LDLib.isJeiLoaded()) {
            if (ingredient instanceof ITypedIngredient<?> typedIngredient) {
                return appeng.integration.modules.jei.GenericEntryStackHelper.ingredientToStack(typedIngredient);
            }
            return appeng.integration.modules.jei.GenericEntryStackHelper.ingredientToStack(ingredient);
        }
        return null;
    }

    @Nullable
    private static GenericStack toGenericStack(EmiStack emiStack) {
        Item item = emiStack.getKeyOfType(Item.class);
        if (item != null) {
            var itemStack = new ItemStack(item);
            itemStack.setTag(emiStack.getNbt());
            var key = AEItemKey.of(itemStack);
            return key == null ? null : new GenericStack(key, emiStack.getAmount());
        }
        Fluid fluid = emiStack.getKeyOfType(Fluid.class);
        if (fluid != null) {
            return new GenericStack(AEFluidKey.of(fluid, emiStack.getNbt()), emiStack.getAmount());
        }
        return null;
    }

    @Nullable
    protected GenericStack getDisplayStack() {
        return inventory != null && isValidSlot() && isClientSideWidget ? getInventoryStack() : lastStack;
    }

    @Nullable
    protected GenericStack getInventoryStack() {
        var inventory = this.inventory;
        if (!isValidSlot(inventory)) {
            return null;
        }
        return inventory.getStack(slotIndex);
    }

    protected boolean isValidSlot() {
        return isValidSlot(inventory);
    }

    protected boolean isValidSlot(@Nullable ConfigInventory inventory) {
        return inventory != null && slotIndex >= 0 && slotIndex < inventory.size();
    }

    protected void setStack(@Nullable GenericStack stack) {
        var inventory = this.inventory;
        if (isValidSlot(inventory)) {
            if (stack == null) {
                if (!mayClearStack()) {
                    return;
                }
            } else if (!maySetStack(stack)) {
                return;
            }
            inventory.setStack(slotIndex, stack);
            this.lastStack = getInventoryStack();
        } else {
            this.lastStack = stack;
        }
        notifyChangeListener();
    }

    protected void setStackAmount(GenericStack requested) {
        var current = isRemote() || isClientSideWidget ? getDisplayStack() : getInventoryStack();
        if (current == null || !current.what().equals(requested.what())) {
            return;
        }
        if (requested.amount() <= 0) {
            setStack(null);
        } else {
            setStack(requested);
        }
    }

    protected void syncStackToClient() {
        var stack = getInventoryStack();
        this.lastStack = stack;
        writeUpdateInfo(UPDATE_STACK, buffer -> GenericStack.writeBuffer(stack, buffer));
    }

    protected boolean maySetStack(GenericStack stack) {
        var inventory = this.inventory;
        if (!canPutItems || !isValidSlot(inventory)) {
            return false;
        }
        var current = inventory.getStack(slotIndex);
        return inventory.canInsert()
                && (current == null || current.what().equals(stack.what()) || inventory.canExtract())
                && inventory.isAllowed(stack);
    }

    protected boolean mayClearStack() {
        var inventory = this.inventory;
        return canTakeItems && isValidSlot(inventory) && inventory.canExtract();
    }

    protected boolean maySetStackOnThisSide(GenericStack stack) {
        return isValidSlot() ? maySetStack(stack) : canPutItems;
    }

    protected boolean mayAcceptPhantomOnThisSide(GenericStack stack) {
        return canAcceptPhantom && maySetStackOnThisSide(stack);
    }

    protected boolean mayClearStackOnThisSide() {
        return isValidSlot() ? mayClearStack() : canTakeItems;
    }

    protected boolean maySetAmountOnThisSide(GenericStack requested) {
        var current = getDisplayStack();
        if (current == null || !current.what().equals(requested.what())) {
            return false;
        }
        if (requested.amount() <= 0) {
            return mayClearStackOnThisSide();
        }
        return maySetStackOnThisSide(requested);
    }

    @OnlyIn(Dist.CLIENT)
    protected void openAmountDialog(GenericStack stack) {
        var dialog = new DialogWidget(gui.mainGroup, true);
        dialog.addWidget(new ImageWidget(0, 0, dialog.getSize().width, dialog.getSize().height, new ColorRectTexture(0x66000000)));

        int width = 176;
        int height = 86;
        int x = (dialog.getSize().width - width) / 2;
        int y = (dialog.getSize().height - height) / 2;
        var panel = new WidgetGroup(x, y, width, height);
        panel.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        dialog.addWidget(panel);

        panel.addWidget(new ImageWidget(8, 7, width - 16, 12,
                new TextTexture(SET_AMOUNT_TITLE).setWidth(width - 16).setDropShadow(false).setType(TextTexture.TextType.LEFT)));
        panel.addWidget(new ImageWidget(8, 24, width - 16, 10,
                new TextTexture(SET_AMOUNT_VALUE).setWidth(width - 16).setDropShadow(false).setType(TextTexture.TextType.LEFT)));

        var textField = new TextFieldWidget(8, 38, width - 16, 18, null, null);
        textField.setCurrentString(Long.toString(stack.amount()));
        textField.setMaxStringLength(19);
        textField.setNumbersOnly(0L, getMaxAmount(stack));
        panel.addWidget(textField);

        Runnable submit = () -> {
            var amount = parseAmount(stack, textField.getCurrentString());
            if (amount == null) {
                return;
            }
            var requested = new GenericStack(stack.what(), amount);
            if (!maySetAmountOnThisSide(requested)) {
                return;
            }
            setStackAmount(requested);
            writeClientAction(ACTION_SET_AMOUNT, buffer -> GenericStack.writeBuffer(requested, buffer));
            dialog.close();
        };

        panel.addWidget(createVanillaButton(26, height - 24, 54, 20, "ldlib.gui.tips.confirm", submit));
        panel.addWidget(createVanillaButton(width - 80, height - 24, 54, 20, "ldlib.gui.tips.cancel", dialog::close));
    }

    private static ButtonWidget createVanillaButton(int x, int y, int width, int height, String text, Runnable onClick) {
        var textTexture = new TextTexture(text).setWidth(width).setDropShadow(false);
        var button = new ButtonWidget(x, y, width, height,
                new GuiTextureGroup(ResourceBorderTexture.VANILLA_BUTTON_NORMAL, textTexture),
                clickData -> onClick.run());
        button.setHoverTexture(new GuiTextureGroup(ResourceBorderTexture.VANILLA_BUTTON_SELECTED,
                new TextTexture(text).setWidth(width).setDropShadow(false)));
        button.setClickedTexture(new GuiTextureGroup(ResourceBorderTexture.VANILLA_BUTTON_PRESSED,
                new TextTexture(text).setWidth(width).setDropShadow(false)));
        return button;
    }

    private static boolean isValidAmountText(GenericStack stack, @Nullable String input) {
        if (input == null || input.isBlank()) {
            return true;
        }
        return parseAmount(stack, input) != null;
    }

    @Nullable
    private static Long parseAmount(GenericStack stack, @Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            var amount = Long.parseLong(input.trim());
            var maxAmount = stack.what() instanceof AEFluidKey ? Long.MAX_VALUE : Integer.MAX_VALUE;
            return amount >= 0 && amount <= maxAmount ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long getMaxAmount(GenericStack stack) {
        return stack.what() instanceof AEFluidKey ? Long.MAX_VALUE : Integer.MAX_VALUE;
    }

    protected void notifyChangeListener() {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
