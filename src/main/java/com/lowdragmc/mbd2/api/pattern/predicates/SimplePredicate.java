package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurable;
import com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.IAutoPersistedSerializable;
import com.lowdragmc.mbd2.api.block.ProxyPartBlock;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.pattern.MultiblockState;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import com.lowdragmc.mbd2.api.pattern.error.PatternStringError;
import com.lowdragmc.mbd2.api.pattern.error.SinglePredicateError;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.common.gui.editor.MultiblockMachineProject;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockPatternPanel;
import com.lowdragmc.mbd2.common.machine.definition.config.toggle.ToggleDirection;
import com.lowdragmc.mbd2.integration.ldlib.MBDLDLibPlugin;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base predicate for one concrete alternative in a multiblock pattern position.
 *
 * <p>The business goal is to combine a world-state test with editor metadata,
 * preview candidates, auto-build candidates, count limits, IO markers, and
 * optional NBT/front-facing requirements. Instances are mutable configuration
 * objects. They are not thread-safe while edited, but after a pattern is built
 * they should treat the world as read-only and only mutate the supplied
 * {@link MultiblockState}'s counters, error, IO, and match context.</p>
 */
public class SimplePredicate implements IAutoPersistedSerializable, IConfigurable {
    /**
     * Singleton predicate accepting every loaded position.
     */
    public static SimplePredicate ANY = new SimplePredicate(x -> true, null);
    /**
     * Singleton predicate accepting empty world positions.
     */
    public static SimplePredicate AIR = new SimplePredicate(blockWorldState -> blockWorldState.getWorld().isEmptyBlock(blockWorldState.getPos()), null);
    /**
     * Preview and auto-build candidates. A null supplier means this predicate has
     * no displayable candidate.
     */
    @Nullable
    public Supplier<BlockInfo[]> candidates;
    /**
     * Raw predicate evaluated against the active pattern cursor.
     */
    public Predicate<MultiblockState> predicate;
    /**
     * Lazily built texture used by the editor predicate preview.
     */
    public Supplier<IGuiTexture> previewTexture = () -> IGuiTexture.EMPTY;
    /**
     * Minimum number of matches across the full pattern; {@code -1} disables
     * the lower bound.
     */
    @Configurable(name = "config.block_pattern.predicate.minCount", tips = {"config.block_pattern.predicate.minCount.tooltip.0", "config.block_pattern.predicate.minCount.tooltip.1"})
    @NumberRange(range = {-1, Integer.MAX_VALUE})
    public int minCount = -1;
    /**
     * Maximum number of matches across the full pattern; {@code -1} disables
     * the upper bound.
     */
    @Configurable(name = "config.block_pattern.predicate.maxCount", tips = {"config.block_pattern.predicate.maxCount.tooltip.0", "config.block_pattern.predicate.maxCount.tooltip.1"})
    @NumberRange(range = {-1, Integer.MAX_VALUE})
    public int maxCount = -1;
    /**
     * Minimum number of matches in each repeated aisle layer; {@code -1}
     * disables the lower bound.
     */
    @Configurable(name = "config.block_pattern.predicate.minLayerCount", tips = {"config.block_pattern.predicate.minLayerCount.tooltip.0", "config.block_pattern.predicate.minLayerCount.tooltip.1"})
    @NumberRange(range = {-1, Integer.MAX_VALUE})
    public int minLayerCount = -1;
    /**
     * Maximum number of matches in each repeated aisle layer; {@code -1}
     * disables the upper bound.
     */
    @Configurable(name = "config.block_pattern.predicate.maxLayerCount", tips = {"config.block_pattern.predicate.maxLayerCount.tooltip.0", "config.block_pattern.predicate.maxLayerCount.tooltip.1"})
    @NumberRange(range = {-1, Integer.MAX_VALUE})
    public int maxLayerCount = -1;
    /**
     * Number of candidates shown in previews; {@code -1} uses default/unlimited
     * preview behavior.
     */
    @Configurable(name = "config.block_pattern.predicate.previewCount", tips = {"config.block_pattern.predicate.previewCount.tooltip.0", "config.block_pattern.predicate.previewCount.tooltip.1"})
    @NumberRange(range = {-1, Integer.MAX_VALUE})
    public int previewCount = -1;
    /**
     * Whether formed-structure preview rendering should hide positions matched
     * by this predicate.
     */
    @Configurable(name = "config.block_pattern.predicate.disableRenderFormed", tips = "config.block_pattern.predicate.disableRenderFormed.tooltip")
    public boolean disableRenderFormed = false;
    /**
     * Recipe IO marker written to the match context for positions that satisfy
     * this predicate.
     */
    @Configurable(name = "config.block_pattern.predicate.io", tips = "config.block_pattern.predicate.io.tooltip")
    public IO io = IO.BOTH;
    /**
     * Optional recipe slot name associated with matched positions.
     */
    @Configurable(name = "config.block_pattern.predicate.slotName", tips = "config.block_pattern.predicate.slotName.tooltip")
    public String slotName;
    /**
     * Partial NBT required on the matched block entity. Empty tag disables the
     * check.
     */
    @Configurable(name = "config.block_pattern.predicate.nbt", tips = "config.block_pattern.predicate.nbt.tooltip")
    public CompoundTag nbt = new CompoundTag();
    /**
     * Partial NBT required on the controller block entity. Empty tag disables
     * the check.
     */
    @Configurable(name = "config.block_pattern.predicate.controller_nbt", tips = "config.block_pattern.predicate.controller_nbt.tooltip")
    public CompoundTag controllerNbt = new CompoundTag();
    /**
     * Optional required controller front direction. When enabled, state
     * predicates do not apply automatic pattern-facing rotation.
     */
    @Configurable(name = "config.block_pattern.predicate.controllerFront", tips = "config.block_pattern.predicate.controllerFront.tooltip", subConfigurable = true)
    public ToggleDirection controllerFront = new ToggleDirection();
    /**
     * Extra tooltip lines shown for preview/JEI candidates.
     */
    @Configurable(name = "config.block_pattern.predicate.tooltips", tips = "config.block_pattern.predicate.tooltips.tooltip", collapse = false)
    public final List<Component> toolTips = new ArrayList<>();
    /**
     * Whether the formed structure should expose the matched position as an
     * openable UI target.
     */
    @Configurable(name = "config.block_pattern.predicate.allowOpenUI", tips = {"config.block_pattern.predicate.allowOpenUI.tooltip.0", "config.block_pattern.predicate.allowOpenUI.tooltip.1"})
    public boolean allowOpenUI = true;

    /**
     * Creates a default wildcard-like predicate for editor deserialization.
     */
    protected SimplePredicate() {
        this(x -> true, null);
    }

    /**
     * Creates a simple predicate from a raw matcher and optional candidates.
     *
     * @param predicate  matcher for the active multiblock cursor; should not
     *                   mutate world state
     * @param candidates optional preview/auto-build candidates
     */
    public SimplePredicate(Predicate<MultiblockState> predicate, @Nullable Supplier<BlockInfo[]> candidates) {
        this.predicate = predicate;
        this.candidates = candidates;
    }

    /**
     * Returns the editor/registry name for this predicate.
     *
     * @return stable names for built-in singletons, otherwise the configurable
     * name
     */
    @Override
    public String name() {
        if (this == AIR) {
            return "air";
        }
        if (this == ANY) {
            return "any";
        }
        return IConfigurable.super.name();
    }

    /**
     * Serializes a predicate through the persisted-data API.
     *
     * @param predicate predicate to serialize
     * @return NBT representation including predicate type metadata
     */
    public static CompoundTag serializeWrapper(SimplePredicate predicate) {
        return predicate.serializeNBT();
    }

    /**
     * Restores a predicate from serialized NBT.
     *
     * <p>Side effects: for registered predicate types, constructs the concrete
     * predicate, loads persisted fields, and rebuilds the runtime matcher and
     * preview texture.</p>
     *
     * @param tag serialized predicate wrapper
     * @return restored predicate, singleton predicate, or {@code null} when the
     * type is unknown
     */
    public static SimplePredicate deserializeWrapper(CompoundTag tag) {
        var type = tag.getString("_type");
        if (type.equals("air")) {
            return AIR;
        }
        if (type.equals("any")) {
            return ANY;
        }
        var wrapper = MBDLDLibPlugin.REGISTER_PREDICATES.get(type);
        if (wrapper != null) {
            var renderer = wrapper.creator().get();
            renderer.deserializeNBT(tag);
            renderer.buildPredicate();
            return renderer;
        }
        return null;
    }

    /**
     * Rebuilds derived runtime data after configuration changes.
     *
     * <p>Side effects: recreates the preview texture supplier and notifies the
     * client editor scene when present. Subclasses override this to rebuild the
     * raw {@link #predicate} and {@link #candidates} before delegating here.</p>
     *
     * @return this predicate for chaining
     */
    public SimplePredicate buildPredicate() {
        previewTexture = Suppliers.memoize(() -> candidates == null ? new TextTexture(name()) : new ItemStackTexture(Arrays.stream(candidates.get()).map(BlockInfo::getItemStackForm).toArray(ItemStack[]::new)));
        notifySceneUpdate();
        return this;
    }

    /**
     * Notifies open multiblock editor panels that predicate placeholders changed.
     *
     * <p>Side effects: client-only UI refresh when LDLib editor state is
     * available; no-op on the dedicated server or outside the editor.</p>
     */
    protected void notifySceneUpdate() {
        if (LDLib.isClient() && Editor.INSTANCE != null && Editor.INSTANCE.getCurrentProject() instanceof MultiblockMachineProject) {
            Editor.INSTANCE.getTabPages().tabs.values().stream()
                    .filter(MultiblockPatternPanel.class::isInstance)
                    .map(MultiblockPatternPanel.class::cast)
                    .findAny().ifPresent(MultiblockPatternPanel::onBlockPlaceholdersChanged);
        }
    }

    /**
     * Builds tooltip lines shared by all candidates of this predicate.
     *
     * @param predicates owning composite predicate, or null when only local
     *                   predicate limits should be described
     * @return tooltip lines for client display
     */
    @OnlyIn(Dist.CLIENT)
    public List<Component> getToolTips(TraceabilityPredicate predicates) {
        List<Component> result = new ArrayList<>();
        if (toolTips != null && !toolTips.isEmpty()) {
            result.addAll(toolTips);
        }
        if (minCount == maxCount && maxCount != -1) {
            result.add(Component.translatable("mbd2.multiblock.pattern.error.limited_exact", minCount));
        } else if (minCount != maxCount && minCount != -1 && maxCount != -1) {
            result.add(Component.translatable("mbd2.multiblock.pattern.error.limited_within", minCount, maxCount));
        } else {
            if (minCount != -1) {
                result.add(Component.translatable("mbd2.multiblock.pattern.error.limited.1", minCount));
            }
            if (maxCount != -1) {
                result.add(Component.translatable("mbd2.multiblock.pattern.error.limited.0", maxCount));
            }
        }
        if (predicates == null) return result;
        if (predicates.isSingle()) {
            result.add(Component.translatable("mbd2.multiblock.pattern.single"));
        }
        if (predicates.hasAir()) {
            result.add(Component.translatable("mbd2.multiblock.pattern.replaceable_air"));
        }
        return result;
    }

    /**
     * Builds tooltip lines for a specific candidate stack.
     *
     * @param predicates owning composite predicate
     * @param stack      candidate stack currently displayed
     * @return tooltip lines for client display
     */
    @OnlyIn(Dist.CLIENT)
    public List<Component> getCandidateToolTips(TraceabilityPredicate predicates, ItemStack stack) {
        return getToolTips(predicates);
    }

    private boolean isProxyBlock(MultiblockState blockWorldState) {
        return blockWorldState.getBlockState().getBlock() == ProxyPartBlock.BLOCK;
    }

    /**
     * Tests this predicate without global/layer count enforcement.
     *
     * <p>Proxy part blocks are accepted so already formed proxy structures can
     * remain traceable. Side effects: when the base predicate matches, writes IO,
     * slot, render mask, open-UI mask, and possible error state through
     * {@link #checkInnerConditions(MultiblockState)}.</p>
     *
     * @param blockWorldState current pattern-test state
     * @return {@code true} when the base predicate or proxy block matches and
     * inner conditions pass
     */
    public boolean test(MultiblockState blockWorldState) {
        if (isProxyBlock(blockWorldState) || predicate.test(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    /**
     * Tests this predicate with global and layer count limits.
     *
     * <p>Side effects: increments global/layer counters when those limits are
     * enabled and may set a {@link SinglePredicateError} when an upper bound is
     * exceeded.</p>
     *
     * @param blockWorldState current pattern-test state
     * @return {@code true} when count checks and inner conditions pass
     */
    public boolean testLimited(MultiblockState blockWorldState) {
        if (isProxyBlock(blockWorldState) || testGlobal(blockWorldState) && testLayer(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    private boolean checkInnerConditions(MultiblockState blockWorldState) {
        if (io != IO.BOTH) {
            if (blockWorldState.io == IO.BOTH) {
                blockWorldState.io = io;
            } else if (blockWorldState.io != io) {
                blockWorldState.io = null;
            }
        }
        if (!nbt.isEmpty() && !blockWorldState.world.isClientSide) {
            var te = blockWorldState.getTileEntity();
            if (te != null) {
                var tag = te.saveWithFullMetadata();
                var merged = tag.copy().merge(nbt);
                if (!tag.equals(merged)) {
                    blockWorldState.setError(new PatternStringError("The NBT fails to match"));
                    return false;
                }
            }
        }
        if (!controllerNbt.isEmpty() && !blockWorldState.world.isClientSide) {
            var controller = blockWorldState.getController();
            if (controller != null) {
                var te = controller.getHolder();
                var tag = te.saveWithFullMetadata();
                var merged = tag.copy().merge(controllerNbt);
                if (!tag.equals(merged)) {
                    blockWorldState.setError(new PatternStringError("The Controller NBT fails to match"));
                    return true;
                }
            }
        }
        if (controllerFront.isEnable()) {
            var controller = blockWorldState.getController();
            var front = controller == null ?
                    Optional.of(blockWorldState.getPatternFacing()) :
                    controller.getFrontFacing().or(() -> Optional.of(blockWorldState.getPatternFacing()));
            if (front.isPresent() && front.get() != controllerFront.getValue()) {
                blockWorldState.setError(new PatternStringError("The Controller Front side fails to match"));
                return false;
            }
        }
        if (slotName != null && !slotName.isEmpty()) {
            Map<Long, Set<String>> slots = blockWorldState.getMatchContext().getOrCreate("slots", Long2ObjectArrayMap::new);
            slots.computeIfAbsent(blockWorldState.getPos().asLong(), s -> new HashSet<>()).add(slotName);
            return true;
        }
        if (disableRenderFormed) {
            blockWorldState.getMatchContext().getOrCreate("renderMask", LongOpenHashSet::new).add(blockWorldState.getPos().asLong());
        }
        if (allowOpenUI) {
            blockWorldState.getMatchContext().getOrCreate("openUIMask", LongOpenHashSet::new).add(blockWorldState.getPos().asLong());
        }
        return true;
    }

    /**
     * Applies global count limits for this predicate.
     *
     * <p>Side effects: evaluates the raw predicate, updates
     * {@link MultiblockState#getGlobalCount()}, and sets an error when the
     * maximum global count is exceeded. Minimum counts are checked after the
     * full pattern pass.</p>
     *
     * @param blockWorldState current pattern-test state
     * @return {@code true} when the raw predicate matches and the upper bound is
     * not exceeded
     */
    public boolean testGlobal(MultiblockState blockWorldState) {
        if (minCount == -1 && maxCount == -1) return true;
        Integer count = blockWorldState.getGlobalCount().get(this);
        boolean base = predicate.test(blockWorldState);
        count = (count == null ? 0 : count) + (base ? 1 : 0);
        blockWorldState.getGlobalCount().put(this, count);
        if (maxCount == -1 || count <= maxCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 0));
        return false;
    }

    /**
     * Applies per-layer count limits for this predicate.
     *
     * <p>Side effects: evaluates the raw predicate, updates
     * {@link MultiblockState#getLayerCount()}, and sets an error when the maximum
     * layer count is exceeded. Minimum layer counts are checked after each layer
     * pass.</p>
     *
     * @param blockWorldState current pattern-test state
     * @return {@code true} when the raw predicate matches and the upper bound is
     * not exceeded
     */
    public boolean testLayer(MultiblockState blockWorldState) {
        if (minLayerCount == -1 && maxLayerCount == -1) return true;
        Integer count = blockWorldState.getLayerCount().get(this);
        boolean base = predicate.test(blockWorldState);
        count = (count == null ? 0 : count) + (base ? 1 : 0);
        blockWorldState.getLayerCount().put(this, count);
        if (maxLayerCount == -1 || count <= maxLayerCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 2));
        return false;
    }

    /**
     * Returns item-stack candidates for diagnostics, JEI/REI/EMI, and previews.
     *
     * <p>Side effects: invokes the candidate supplier. On the client, block
     * entity aware item forms are resolved with the current Minecraft level.</p>
     *
     * @return displayable candidate stacks, excluding air candidates
     */
    public List<ItemStack> getCandidates() {
        if (LDLib.isClient()) {
            return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get()).filter(info -> info.getBlockState().getBlock() != Blocks.AIR)
                    .map(blockInfo -> blockInfo.getItemStackForm(Minecraft.getInstance().level, BlockPos.ZERO)).collect(Collectors.toList());
        }
        return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get()).filter(info -> info.getBlockState().getBlock() != Blocks.AIR).map(BlockInfo::getItemStackForm).collect(Collectors.toList());
    }

    /**
     * Returns the editor preview texture.
     *
     * <p>Side effects: may evaluate the memoized texture supplier the first time
     * it is called after {@link #buildPredicate()}.</p>
     *
     * @return texture representing this predicate in editor controls
     */
    public IGuiTexture getPreviewTexture() {
        return previewTexture.get();
    }

    /**
     * Returns the translation key used by the configuration UI.
     *
     * @return configurable translation key
     */
    @Override
    public String getTranslateKey() {
        return "config.%s.%s".formatted(group(), name());
    }

    /**
     * Builds editor configurators for this predicate.
     *
     * <p>Side effects: adds a preview widget before the standard configurable
     * fields.</p>
     *
     * @param father parent configurator group receiving child controls
     */
    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        father.addConfigurators(new WrapperConfigurator("config.block_pattern.predicate.preview",
                new WidgetGroup(0, 0, 100, 100)
                        .addWidget(new ImageWidget(0, 0, 100, 100, IGuiTexture.EMPTY)
                                .setBorder(2, ColorPattern.T_WHITE.color))
                        .addWidget(createPreview())));
        IConfigurable.super.buildConfigurator(father);
    }

    /**
     * Creates the client-only preview widget inserted into the predicate configurator.
     * <p>
     * Subclasses can override this hook to render predicate-specific controls or visuals. The returned widget is added
     * directly to a fixed {@code 100x100} preview container, so it should size and position itself within that area.
     *
     * @return widget used by the editor preview panel
     */
    @OnlyIn(Dist.CLIENT)
    protected Widget createPreview() {
        return SimplePredicateClientPreview.create(this);
    }
}
