package com.lowdragmc.mbd2.common.item;

import com.lowdragmc.lowdraglib.Platform;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SearchComponentWidget;
import com.lowdragmc.mbd2.api.machine.IMachine;
import com.lowdragmc.mbd2.api.machine.IMultiController;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.network.MBD2Network;
import com.lowdragmc.mbd2.common.network.packets.SPatternErrorPosPacket;
import com.lowdragmc.mbd2.config.ConfigHolder;
import com.lowdragmc.mbd2.utils.BuilderMaterialBindings;
import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Multi-mode developer gadget for building and debugging MBD machines.
 *
 * <p>The item uses damage values as modes: {@code 0} is the multiblock builder, {@code 1} is the recipe debugger, and
 * {@code 2} is the multiblock debugger. Builder-specific state such as slow-build, selected pattern, and material
 * bindings is stored on the stack NBT via {@link BuilderMaterialBindings}; recipe-debugger state stores the selected
 * recipe id on the stack. Item state is mutable and should be read or changed on the logical thread that owns the
 * player inventory.</p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MBDGadgetsItem extends Item implements HeldItemUIFactory.IHeldItemUIHolder {

    /**
     * Creates the non-stackable fire-resistant gadget item.
     */
    public MBDGadgetsItem() {
        super(new Item.Properties()
                .fireResistant()
                .stacksTo(1));
    }

    /**
     * Checks whether the stack is in multiblock-builder mode.
     *
     * @param stack gadget stack to inspect
     * @return {@code true} when the damage value is {@code 0}
     */
    public boolean isMultiblockBuilder(ItemStack stack) {
        return stack.getDamageValue() == 0;
    }

    /**
     * Checks whether the stack is in recipe-debugger mode.
     *
     * @param stack gadget stack to inspect
     * @return {@code true} when the damage value is {@code 1}
     */
    public boolean isRecipeDebugger(ItemStack stack) {
        return stack.getDamageValue() == 1;
    }

    /**
     * Checks whether the stack is in multiblock-debugger mode.
     *
     * @param stack gadget stack to inspect
     * @return {@code true} when the damage value is {@code 2}
     */
    public boolean isMultiblockDebugger(ItemStack stack) {
        return stack.getDamageValue() == 2;
    }

    /**
     * Reads the recipe id selected by the recipe debugger.
     *
     * @param stack gadget stack carrying optional debugger NBT
     * @return selected recipe id, or {@code null} when missing or invalid
     */
    @Nullable
    public ResourceLocation getRecipe(ItemStack stack) {
        var tag = stack.getTag();
        return tag != null && tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    /**
     * Stores the recipe id selected by the recipe debugger.
     *
     * <p>Side effects: creates or updates the stack NBT.</p>
     *
     * @param stack  gadget stack to mutate
     * @param recipe non-null recipe id
     */
    public void setRecipe(ItemStack stack, ResourceLocation recipe) {
        stack.getOrCreateTag().putString("recipe", recipe.toString());
    }

    /**
     * Returns the translation key for the current gadget mode.
     *
     * @param pStack stack being displayed
     * @return base item id plus the mode suffix, or builder variant key when builder bindings mark slow/instant build
     */
    @Override
    public String getDescriptionId(ItemStack pStack) {
        if (BuilderMaterialBindings.isBuilder(pStack)) {
            return BuilderMaterialBindings.isSlowBuild(pStack)
                    ? "item.mbd2.mbd_gadgets.multiblock_builder.slow"
                    : "item.mbd2.mbd_gadgets.multiblock_builder.instant";
        }
        var id = super.getDescriptionId(pStack);
        if (isMultiblockBuilder(pStack)) {
            return id + ".multiblock_builder";
        } else if (isRecipeDebugger(pStack)) {
            return id + ".recipe_debugger";
        } else if (isMultiblockDebugger(pStack)) {
            return id + ".multiblock_debugger";
        }
        return id;
    }

    /**
     * Adds mode-specific tooltip lines.
     *
     * <p>The tooltip reports the selected recipe id for recipe debugging and builder state such as selected pattern and
     * bound item/fluid source coordinates. Side effects: appends components only.</p>
     *
     * @param stack      stack being inspected
     * @param level      optional level context
     * @param components mutable tooltip list
     * @param isAdvanced vanilla advanced-tooltip flag
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, components, isAdvanced);
        components.add(Component.translatable("tooltip.mbd2.open_wheel", Component.translatable("key.mbd2.open_gadget_wheel")).withStyle(ChatFormatting.GREEN));
        var id = getDescriptionId(stack);
        if (isMultiblockBuilder(stack))
            components.add(Component.translatable(id + ".tooltip"));
        else if (isRecipeDebugger(stack)) {
            components.add(Component.translatable(id + ".tooltip.0"));
            components.add(Component.translatable(id + ".tooltip.1"));
            var recipe = getRecipe(stack);
            if (recipe != null) {
                components.add(Component.translatable(id + ".tooltip.2", recipe.toString()));
            }
        } else if (isMultiblockDebugger(stack)) {
            components.add(Component.translatable(id + ".tooltip"));
        }
        if (BuilderMaterialBindings.isBuilder(stack)) {
            components.add(Component.translatable("mbd2.builder.pattern.tooltip", BuilderMaterialBindings.getPatternIndex(stack) + 1));

            var item = BuilderMaterialBindings.readBoundItemPos(stack);
            if (item != null) {
                var p = item.pos();
                String tooltipKey = BuilderMaterialBindings.isBoundItemSourceME(stack) ?
                        "mbd2.builder.bind.me.tooltip" : "mbd2.builder.bind.item.tooltip";
                components.add(Component.translatable(tooltipKey, p.getX(), p.getY(), p.getZ()));
            }

            var fluid = BuilderMaterialBindings.readBoundFluidPos(stack);
            if (fluid != null) {
                var p = fluid.pos();
                components.add(Component.translatable("mbd2.builder.bind.fluid.tooltip", p.getX(), p.getY(), p.getZ()));
            }
        }
    }

    private boolean isUsed;

    /**
     * Handles right-click-in-air behavior for gadget modes.
     *
     * <p>Recipe-debugger mode opens its held-item UI on the server. Crouching passes through so block interactions can
     * bind builder sources or clear/debug multiblock state. The {@code isUsed} guard consumes the follow-up use call
     * that vanilla can issue after a block interaction was already handled.</p>
     *
     * @param pLevel    level containing the player
     * @param pPlayer   player using the item
     * @param pUsedHand hand containing the stack
     * @return held-item result for the interaction
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        var stack = pPlayer.getItemInHand(pUsedHand);
        if (isUsed) {
            isUsed = false;
            return InteractionResultHolder.success(stack);
        }
        if (pPlayer.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        } else if (pPlayer instanceof ServerPlayer serverPlayer && isRecipeDebugger(stack)) {
            HeldItemUIFactory.INSTANCE.openUI(serverPlayer, pUsedHand);
            return InteractionResultHolder.success(stack);
        }
        return super.use(pLevel, pPlayer, pUsedHand);
    }

    /**
     * Handles block-targeted gadget behavior before vanilla item use.
     *
     * <p>Builder mode can bind adjacent item/fluid handlers while crouching or auto-build a controller pattern while
     * standing. Multiblock-debugger mode reports structure validation results and sends the first pattern error
     * position to the client for preview. Recipe-debugger mode checks the selected recipe against the clicked machine,
     * including recipe modification hooks, and prints diagnostic messages to the player. Server-side interactions can
     * mutate stack NBT and player inventory dirty state; client-side multiblock mismatch preview is visual only.</p>
     *
     * @param stack   stack being used
     * @param context clicked-block context
     * @return success when a gadget mode handled the click, otherwise pass
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var player = context.getPlayer();
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.isCrouching() && BuilderMaterialBindings.isBuilder(stack)) {
            Level level = serverPlayer.level();
            BlockPos pos = context.getClickedPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                serverPlayer.displayClientMessage(Component.translatable("mbd2.builder.bind.failure.no_capability"), true);
                return InteractionResult.SUCCESS;
            }

            if (BuilderMaterialBindings.hasMEItemStorage(be)) {
                BuilderMaterialBindings.bindMEItemPos(stack, level, pos);
                serverPlayer.displayClientMessage(Component.translatable("mbd2.builder.bind.me.success", pos.getX(), pos.getY(), pos.getZ()), true);
                return InteractionResult.SUCCESS;
            }

            boolean boundAny = false;
            if (BuilderMaterialBindings.hasItemHandler(be)) {
                BuilderMaterialBindings.bindItemPos(stack, level, pos);
                serverPlayer.displayClientMessage(Component.translatable("mbd2.builder.bind.item.success", pos.getX(), pos.getY(), pos.getZ()), true);
                boundAny = true;
            }
            if (BuilderMaterialBindings.hasFluidHandler(be)) {
                BuilderMaterialBindings.bindFluidPos(stack, level, pos);
                serverPlayer.displayClientMessage(Component.translatable("mbd2.builder.bind.fluid.success", pos.getX(), pos.getY(), pos.getZ()), true);
                boundAny = true;
            }

            if (!boundAny) {
                serverPlayer.displayClientMessage(Component.translatable("mbd2.builder.bind.failure.no_capability"), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (player != null && player.isCrouching() && isMultiblockDebugger(stack)) {
            Level level = context.getLevel();
            var controllerPos = context.getClickedPos();
            IMultiController controller = IMultiController.ofController(level, controllerPos).orElse(null);
            int durationTicks = ConfigHolder.multiblockPreviewDuration * 20;
            if (level.isClientSide && controller instanceof MBDMultiblockMachine multiblock) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> showOccupiedMismatchPreview(multiblock, controllerPos, durationTicks));
                return InteractionResult.SUCCESS;
            }
        }
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isCrouching()) {
            if (isMultiblockBuilder(stack)) {
                var controller = IMultiController.ofController(player.level(), context.getClickedPos()).orElse(null);
                if (controller != null) {
                    var pattern = controller.getPattern();
                    if (pattern != null) {
                        int patternIndex = getSelectedPatternIndex(stack, controller);
                        pattern.autoBuild(player, new MultiblockState(player.level(), context.getClickedPos()), patternIndex);
                    }
                    isUsed = true;
                    return InteractionResult.SUCCESS;
                }
            } else if (isMultiblockDebugger(stack)) {
                var controller = IMultiController.ofController(player.level(), context.getClickedPos()).orElse(null);
                if (controller != null) {
                    if (controller.isFormed()) {
                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.is_formed"));
                    } else if (controller.checkPatternWithLock()) {
                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.success"));
                        if (controller instanceof MBDMultiblockMachine multiblock && multiblock.getDefinition().multiblockSettings().catalyst().isEnable()) {
                            if (!multiblock.getDefinition().multiblockSettings().catalyst().getFilterItems().isEmpty()) {
                                var items = Component.literal("[");
                                for (ItemStack filterItem : multiblock.getDefinition().multiblockSettings().catalyst().getFilterItems()) {
                                    items.append(filterItem.getDisplayName()).append(Component.literal(", "));
                                }
                                items.append(Component.literal("]"));
                                serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.catalyst.items", items));
                            }
                            if (!multiblock.getDefinition().multiblockSettings().catalyst().getFilterTags().isEmpty()) {
                                var tags = Component.literal("[");
                                for (ResourceLocation filterTag : multiblock.getDefinition().multiblockSettings().catalyst().getFilterTags()) {
                                    tags.append(Component.literal(filterTag.toString())).append(Component.literal(", "));
                                }
                                tags.append(Component.literal("]"));
                                serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.catalyst.tags", tags));
                            }
                        }
                    } else {
                        var error = controller.getMultiblockState().error;
                        if (error != null) {
                            MBD2Network.NETWORK.sendToPlayer(new SPatternErrorPosPacket(error.getPos()), serverPlayer);
                            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.failure.error.info", error.getErrorInfo()));
                        } else {
                            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.failure.no_error"));
                        }
                    }
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.multiblock_debugger.failure.error.not_controller"));
                }
                return InteractionResult.SUCCESS;
            } else if (isRecipeDebugger(stack) && getRecipe(stack) != null && serverPlayer.getServer() != null) {
                var machine = IMachine.ofMachine(player.level(), context.getClickedPos()).orElse(null);
                if (machine != null) {
                    var recipe = getRecipe(stack);
                    var recipeManager = serverPlayer.getServer().getRecipeManager();
                    for (MBDRecipeType recipeType : MBDRegistries.RECIPE_TYPES) {
                        for (MBDRecipe mbdRecipe : recipeManager.getAllRecipesFor(recipeType)) {
                            if (Objects.equals(mbdRecipe.id, recipe)) {
                                if (machine.getRecipeType() != recipeType) {
                                    serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.warning.recipe_type",
                                            Component.literal("id").withStyle(style ->
                                                    style.withColor(ChatFormatting.YELLOW)
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                    Component.literal(machine.getRecipeType().toString())))),
                                            Component.literal("id").withStyle(style ->
                                                    style.withColor(ChatFormatting.YELLOW)
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                    Component.literal(mbdRecipe.id.toString()))))
                                    ));
                                }
                                var result = mbdRecipe.matchRecipe(machine);
                                if (result.isSuccess()) {
                                    result = mbdRecipe.matchTickRecipe(machine);
                                    if (result.isSuccess()) {
                                        result = mbdRecipe.checkConditions(machine.getRecipeLogic());
                                    }
                                }
                                if (result.isSuccess()) {
                                    serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.raw.success",
                                            Component.literal("id").withStyle(style ->
                                                    style.withColor(ChatFormatting.YELLOW)
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                    Component.literal(mbdRecipe.id.toString()))))));
                                    var modifiedRecipe = machine.doModifyRecipe(mbdRecipe);
                                    if (modifiedRecipe == mbdRecipe) {
                                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.empty"));
                                    } else if (modifiedRecipe == null) {
                                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.failure.0"));
                                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.failure.1"));
                                    } else {
                                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.has"));
                                        result = modifiedRecipe.matchRecipe(machine);
                                        if (result.isSuccess()) {
                                            result = modifiedRecipe.matchTickRecipe(machine);
                                            if (result.isSuccess()) {
                                                result = modifiedRecipe.checkConditions(machine.getRecipeLogic());
                                            }
                                        }
                                        if (result.isSuccess()) {
                                            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.success"));
                                        } else {
                                            serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.modified.failure.0"));
                                            if (result.reason() != null) {
                                                serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.failure.reason").append(result.reason().get()));
                                            }
                                        }
                                    }
                                    isUsed = true;
                                    return InteractionResult.SUCCESS;
                                } else {
                                    serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.raw.failure.0",
                                            Component.literal("id").withStyle(style ->
                                                    style.withColor(ChatFormatting.YELLOW)
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                    Component.literal(mbdRecipe.id.toString()))))));
                                    if (result.reason() != null) {
                                        serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.failure.reason").append(result.reason().get()));
                                    }
                                }
                                isUsed = true;
                                return InteractionResult.SUCCESS;
                            }
                        }
                    }
                    isUsed = true;
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Shows a temporary client preview for multiblock occupied-position mismatches.
     *
     * @param multiblock    multiblock machine being inspected
     * @param controllerPos controller block position
     * @param durationTicks preview duration in ticks; non-positive values are left to the client helper to interpret
     */
    private static void showOccupiedMismatchPreview(MBDMultiblockMachine multiblock, BlockPos controllerPos, int durationTicks) {
        com.lowdragmc.mbd2.client.MultiblockDebuggerClient.showPreviewWithOccupiedMismatch(multiblock, controllerPos, durationTicks);
    }

    /**
     * Reads and clamps the builder's selected pattern index for a controller.
     *
     * <p>If the stored index is beyond the controller definition's pattern count, the stack NBT is updated to the
     * highest valid zero-based index before returning it.</p>
     *
     * @param stack      builder stack carrying pattern selection
     * @param controller target multiblock controller
     * @return selected zero-based pattern index
     */
    private static int getSelectedPatternIndex(ItemStack stack, IMultiController controller) {
        int patternIndex = BuilderMaterialBindings.getPatternIndex(stack);
        if (controller instanceof MBDMultiblockMachine multiblock) {
            int patternCount = multiblock.getDefinition().getPatterns(multiblock).length;
            if (patternCount > 0) {
                int clamped = Math.min(patternIndex, patternCount - 1);
                if (clamped != patternIndex) {
                    BuilderMaterialBindings.setPatternIndex(stack, clamped);
                    return clamped;
                }
            }
        }
        return patternIndex;
    }

    /**
     * Creates the held-item UI for selecting a recipe to debug.
     *
     * <p>The search box lists all registered MBD recipe ids from the current integrated/dedicated server and writes the
     * selected id to the held stack. The UI is meaningful for recipe-debugger mode but can be created for any held
     * gadget stack by LowDragLib.</p>
     *
     * @param entityPlayer player viewing the UI
     * @param holder       LowDragLib held-item holder that exposes the mutable stack
     * @return modular UI for recipe id selection
     */
    @Override
    public ModularUI createUI(Player entityPlayer, HeldItemUIFactory.HeldItemHolder holder) {
        var x = (200 - 150) / 2;
        var y = (50 - 10) / 2;
        var searchComponent = new SearchComponentWidget<>(x, y, 150, 10,
                new SearchComponentWidget.IWidgetSearch<ResourceLocation>() {
                    @Override
                    public String resultDisplay(ResourceLocation value) {
                        return value.toString();
                    }

                    @Override
                    public void selectResult(ResourceLocation value) {
                        setRecipe(holder.getHeld(), value);
                    }

                    @Override
                    public void search(String word, Consumer<ResourceLocation> find) {
                        if (Platform.getMinecraftServer() != null) {
                            var recipeManager = Platform.getMinecraftServer().getRecipeManager();
                            for (MBDRecipeType recipeType : MBDRegistries.RECIPE_TYPES) {
                                if (Thread.currentThread().isInterrupted()) return;
                                for (var recipe : recipeManager.getAllRecipesFor(recipeType)) {
                                    if (recipe.id.toString().contains(word.toLowerCase())) {
                                        find.accept(recipe.id);
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public void serialize(ResourceLocation value, FriendlyByteBuf buf) {
                        buf.writeUtf(value.toString());
                    }

                    @Override
                    public ResourceLocation deserialize(FriendlyByteBuf buf) {
                        return ResourceLocation.parse(buf.readUtf());
                    }
                }, true);
        var currentRecipe = getRecipe(holder.getHeld());
        searchComponent.setShowUp(true);
        searchComponent.setCapacity(5);
        var textFieldWidget = searchComponent.textFieldWidget;
        textFieldWidget.setCurrentString(currentRecipe == null ? "" : currentRecipe.toString());
        return new ModularUI(200, 50, holder, entityPlayer)
                .background(ResourceBorderTexture.BORDERED_BACKGROUND)
                .widget(searchComponent)
                .widget(new ImageWidget(x, y - 12, 150, 10, new TextTexture("item.mbd2.mbd_gadgets.recipe_debugger.recipe_id").setWidth(150)));
    }
}
