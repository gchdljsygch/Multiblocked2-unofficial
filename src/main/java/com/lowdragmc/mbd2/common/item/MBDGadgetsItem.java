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
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.ingredient.EntityIngredient;
import com.lowdragmc.mbd2.api.recipe.ingredient.FluidIngredient;
import com.lowdragmc.mbd2.api.recipe.ingredient.SizedIngredient;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.api.recipe.RecipeLogic;
import com.lowdragmc.mbd2.api.registry.MBDRegistries;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.machine.MBDMultiblockMachine;
import com.lowdragmc.mbd2.common.trait.recipethread.RecipeThreadTrait;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final int CONTENT_PREVIEW_LIMIT = 3;
    private static final int CONTENT_JSON_PREVIEW_LIMIT = 160;

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
            } else if (isRecipeDebugger(stack) && serverPlayer.getServer() != null) {
                var machine = IMachine.ofMachine(player.level(), context.getClickedPos()).orElse(null);
                if (machine != null) {
                    MBDRecipe runtimeRecipe = showRecipeDebuggerRuntimeInfo(serverPlayer, machine);
                    var recipe = getRecipe(stack);
                    if (recipe == null) {
                        isUsed = true;
                        return InteractionResult.SUCCESS;
                    }
                    var recipeManager = serverPlayer.getServer().getRecipeManager();
                    for (MBDRecipeType recipeType : MBDRegistries.RECIPE_TYPES) {
                        for (MBDRecipe mbdRecipe : recipeManager.getAllRecipesFor(recipeType)) {
                            if (Objects.equals(mbdRecipe.id, recipe)) {
                                if (runtimeRecipe == null || !Objects.equals(runtimeRecipe.id, mbdRecipe.id)) {
                                    showRecipeDebuggerRecipeContents(serverPlayer, mbdRecipe);
                                }
                                if (!machine.isRecipeTypeAllowed(recipeType)) {
                                    serverPlayer.sendSystemMessage(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.warning.recipe_type",
                                            Component.literal("id").withStyle(style ->
                                                    style.withColor(ChatFormatting.YELLOW)
                                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                                    Component.literal(machine.getRecipeTypes().toString())))),
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
     * Prints the clicked machine's currently displayed recipe runtime state to the recipe debugger user.
     *
     * @param serverPlayer player receiving the diagnostics
     * @param machine      clicked machine
     */
    @Nullable
    private static MBDRecipe showRecipeDebuggerRuntimeInfo(ServerPlayer serverPlayer, IMachine machine) {
        RecipeLogic logic = getDebuggerDisplayRecipeLogic(machine);
        serverPlayer.sendSystemMessage(Component.translatable(
                "item.mbd2.mbd_gadgets.recipe_debugger.runtime.status",
                recipeStatusComponent(logic.getStatus())));

        MBDRecipe originRecipe = logic.getLastOriginRecipe();
        MBDRecipe runningRecipe = logic.getLastRecipe();
        MBDRecipe displayRecipe = runningRecipe != null ? runningRecipe : originRecipe;
        if (displayRecipe == null) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.recipe.none"));
        } else {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.recipe",
                    recipeIdTextComponent(displayRecipe)));
            showRecipeDebuggerRecipeContents(serverPlayer, displayRecipe);
        }
        if (originRecipe != null && runningRecipe != null && !Objects.equals(originRecipe.id, runningRecipe.id)) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.origin_recipe",
                    recipeIdTextComponent(originRecipe)));
        }

        if (logic.getDuration() > 0 || logic.getProgress() > 0) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.progress",
                    logic.getProgress(), logic.getDuration(), toPercent(logic.getProgressPercent())));
        }
        if (logic.getFuelMaxTime() > 0 || logic.getFuelTime() > 0) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.fuel",
                    logic.getFuelTime(), logic.getFuelMaxTime(), toPercent(logic.getFuelProgressPercent())));
        }
        if (logic.getLastFuelRecipe() != null) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.fuel_recipe",
                    recipeIdTextComponent(logic.getLastFuelRecipe())));
        }
        if (logic.isWaiting() && logic.getWaitingReason() != null) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.waiting_reason",
                    logic.getWaitingReason()));
        }
        return displayRecipe;
    }

    private static RecipeLogic getDebuggerDisplayRecipeLogic(IMachine machine) {
        if (machine instanceof MBDMachine mbdMachine) {
            RecipeThreadTrait trait = RecipeThreadTrait.get(mbdMachine);
            return trait == null ? mbdMachine.getCurrentRecipeLogic() : trait.getRecipeLogicForExternalDisplay();
        }
        return machine.getRecipeLogic();
    }

    private static Component recipeStatusComponent(RecipeLogic.Status status) {
        return Component.translatable("recipe_logic.status." + status.name().toLowerCase(Locale.ROOT));
    }

    private static Component recipeIdTextComponent(MBDRecipe recipe) {
        return Component.literal(recipe.id.toString()).withStyle(ChatFormatting.YELLOW);
    }

    private static void showRecipeDebuggerRecipeContents(ServerPlayer serverPlayer, MBDRecipe recipe) {
        if (!hasRecipeContents(recipe.inputs) && !hasRecipeContents(recipe.outputs)) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.contents.empty",
                    recipeIdTextComponent(recipe)));
            return;
        }
        serverPlayer.sendSystemMessage(Component.translatable(
                "item.mbd2.mbd_gadgets.recipe_debugger.runtime.contents.header",
                recipeIdTextComponent(recipe)));
        sendRecipeDebuggerContents(serverPlayer,
                Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.side.input").withStyle(ChatFormatting.AQUA),
                recipe.inputs);
        sendRecipeDebuggerContents(serverPlayer,
                Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.side.output").withStyle(ChatFormatting.GOLD),
                recipe.outputs);
    }

    private static boolean hasRecipeContents(Map<RecipeCapability<?>, List<Content>> contents) {
        return contents.values().stream().anyMatch(list -> list != null && !list.isEmpty());
    }

    private static void sendRecipeDebuggerContents(ServerPlayer serverPlayer, Component side,
                                                   Map<RecipeCapability<?>, List<Content>> contents) {
        for (var entry : contents.entrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            List<Content> contentList = entry.getValue();
            if (contentList == null || contentList.isEmpty()) continue;
            for (Content content : contentList) {
                MutableComponent line = Component.translatable(
                        "item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.line",
                        side,
                        capability.getTraslateComponent(),
                        recipeContentComponent(capability, content));
                Component metadata = recipeContentMetadataComponent(content);
                if (metadata != null) {
                    line.append(Component.literal(" ")).append(metadata);
                }
                serverPlayer.sendSystemMessage(line);
            }
        }
    }

    private static Component recipeContentComponent(RecipeCapability<?> capability, Content content) {
        Object inner = content.getContent();
        if (inner instanceof Ingredient ingredient) {
            return itemIngredientComponent(ingredient);
        }
        if (inner instanceof FluidIngredient fluidIngredient) {
            return fluidIngredientComponent(fluidIngredient);
        }
        if (inner instanceof EntityIngredient entityIngredient) {
            return entityIngredientComponent(entityIngredient);
        }
        if (inner instanceof Number || inner instanceof Boolean || inner instanceof CharSequence) {
            return Component.literal(String.valueOf(inner)).withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal(serializedContentText(capability, content)).withStyle(ChatFormatting.GRAY);
    }

    private static Component itemIngredientComponent(Ingredient ingredient) {
        int amount = 1;
        Ingredient displayIngredient = ingredient;
        if (ingredient instanceof SizedIngredient sizedIngredient) {
            amount = sizedIngredient.getAmount();
            displayIngredient = sizedIngredient.getInner();
        }

        MutableComponent result = Component.literal(amount + "x ");
        ItemStack[] stacks = displayIngredient.getItems();
        appendItemCandidates(result, stacks);
        if (displayIngredient instanceof StrictNBTIngredient) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.nbt"));
        }
        return result;
    }

    private static Component fluidIngredientComponent(FluidIngredient fluidIngredient) {
        MutableComponent result = Component.literal(fluidIngredient.getAmount() + "x ");
        var stacks = fluidIngredient.getStacks();
        int limit = Math.min(stacks.length, CONTENT_PREVIEW_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (i > 0) result.append(Component.literal(", "));
            result.append(stacks[i].getDisplayName());
        }
        appendMoreCandidates(result, stacks.length);
        if (stacks.length == 0) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.unknown"));
        }
        if (fluidIngredient.getNbt() != null) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.nbt"));
        }
        return result;
    }

    private static Component entityIngredientComponent(EntityIngredient entityIngredient) {
        MutableComponent result = Component.literal(entityIngredient.getCount() + "x ");
        var types = entityIngredient.getTypes();
        int limit = Math.min(types.length, CONTENT_PREVIEW_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (i > 0) result.append(Component.literal(", "));
            result.append(types[i].getDescription());
        }
        appendMoreCandidates(result, types.length);
        if (types.length == 0) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.unknown"));
        }
        if (entityIngredient.getNbt() != null) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.nbt"));
        }
        return result;
    }

    private static void appendItemCandidates(MutableComponent result, ItemStack[] stacks) {
        int limit = Math.min(stacks.length, CONTENT_PREVIEW_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (i > 0) result.append(Component.literal(", "));
            result.append(stacks[i].getDisplayName());
        }
        appendMoreCandidates(result, stacks.length);
        if (stacks.length == 0) {
            result.append(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.unknown"));
        }
    }

    private static void appendMoreCandidates(MutableComponent result, int totalCount) {
        if (totalCount > CONTENT_PREVIEW_LIMIT) {
            result.append(Component.translatable(
                    "item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.more",
                    totalCount - CONTENT_PREVIEW_LIMIT));
        }
    }

    @Nullable
    private static Component recipeContentMetadataComponent(Content content) {
        List<Component> parts = new ArrayList<>();
        if (content.perTick) {
            parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.per_tick"));
        }
        if (content.chance != 1) {
            if (content.chance == 0) {
                parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.not_consumed"));
            } else {
                parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.chance",
                        toPercent(content.chance) + "%"));
            }
        }
        if (content.tierChanceBoost != 0) {
            parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.tier_boost",
                    toPercent(content.tierChanceBoost) + "%"));
        }
        if (!content.slotName.isEmpty()) {
            parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.slot",
                    content.slotName));
        }
        if (!content.uiName.isEmpty()) {
            parts.add(Component.translatable("item.mbd2.mbd_gadgets.recipe_debugger.runtime.content.metadata.ui",
                    content.uiName));
        }
        if (parts.isEmpty()) {
            return null;
        }
        MutableComponent result = Component.literal("[");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) result.append(Component.literal(", "));
            result.append(parts.get(i));
        }
        return result.append(Component.literal("]"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String serializedContentText(RecipeCapability capability, Content content) {
        String text;
        try {
            text = capability.serializer.toJson(capability.of(content.getContent())).toString();
        } catch (RuntimeException e) {
            text = String.valueOf(content.getContent());
        }
        if (text.length() > CONTENT_JSON_PREVIEW_LIMIT) {
            return text.substring(0, CONTENT_JSON_PREVIEW_LIMIT) + "...";
        }
        return text;
    }

    private static int toPercent(double progress) {
        return (int) Math.round(progress * 100.0);
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
