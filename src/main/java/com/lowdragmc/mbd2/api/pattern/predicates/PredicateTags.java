package com.lowdragmc.mbd2.api.pattern.predicates;

import com.google.common.base.Suppliers;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.editor.configurator.SearchComponentConfigurator;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.mbd2.api.pattern.TraceabilityPredicate;
import lombok.NoArgsConstructor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Predicate that accepts blocks belonging to one or more block tags.
 *
 * <p>The business goal is to allow flexible pattern definitions such as "any
 * sand" or "any log" without enumerating every block. Candidate lists are
 * resolved from the block registry tags and therefore reflect the current mod
 * list. Instances are mutable through the editor and rebuild their matcher when
 * tag IDs change.</p>
 */
@LDLRegister(name = "tags", group = "predicate")
@NoArgsConstructor
public class PredicateTags extends SimplePredicate {

    @Persisted
    protected ResourceLocation[] tags = new ResourceLocation[]{};

    /**
     * Creates a tag predicate.
     *
     * @param tags accepted block tag IDs; null entries are discarded during
     *             rebuild
     */
    public PredicateTags(ResourceLocation... tags) {
        this.tags = tags;
        buildPredicate();
    }

    /**
     * Rebuilds the tag matcher and candidates.
     *
     * <p>Side effects: removes null tag IDs, installs the sand tag as an editor
     * fallback when empty, resolves tag keys, and updates inherited preview
     * state.</p>
     *
     * @return this predicate for chaining
     */
    @Override
    public SimplePredicate buildPredicate() {
        tags = Arrays.stream(tags).filter(Objects::nonNull).toArray(ResourceLocation[]::new);
        if (tags.length == 0) tags = new ResourceLocation[]{BlockTags.SAND.location()};
        var tagKeys = (TagKey<Block>[]) Arrays.stream(tags).map(BlockTags::create).toArray(TagKey[]::new);
        predicate = state -> Arrays.stream(tagKeys).anyMatch(tagKey -> state.getBlockState().getBlock().builtInRegistryHolder().is(tagKey));
        candidates = Suppliers.memoize(() -> Arrays.stream(tagKeys).flatMap(tag -> {
            var opt = BuiltInRegistries.BLOCK.getTag(tag);
            if (opt.isPresent()) {
                return opt.get().stream().map(Holder::get);
            }
            return Arrays.stream(new Block[]{Blocks.BARRIER});
        }).map(BlockInfo::fromBlock).toArray(BlockInfo[]::new));
        return super.buildPredicate();
    }

    /**
     * Builds tooltip lines for this tag predicate.
     *
     * @param predicates owning composite predicate
     * @return inherited predicate tooltips plus all configured tag IDs
     */
    @Override
    public List<Component> getToolTips(TraceabilityPredicate predicates) {
        var result = new ArrayList<>(super.getToolTips(predicates));
        Arrays.stream(tags)
                .filter(Objects::nonNull)
                .forEach(tag -> addBlockTagToolTip(result, tag));
        return result;
    }

    /**
     * Builds tooltip lines for a specific displayed candidate.
     *
     * <p>When the candidate belongs to configured tags, only matching tag IDs are
     * shown; otherwise all configured tags are shown as fallback context.</p>
     *
     * @param predicates owning composite predicate
     * @param stack      displayed candidate stack
     * @return inherited predicate tooltips plus relevant tag IDs
     */
    @Override
    public List<Component> getCandidateToolTips(TraceabilityPredicate predicates, ItemStack stack) {
        var result = new ArrayList<>(super.getToolTips(predicates));
        var matchingTags = getMatchingTags(stack);
        if (matchingTags.isEmpty()) {
            matchingTags = Arrays.stream(tags).filter(Objects::nonNull).toList();
        }
        matchingTags.forEach(tag -> addBlockTagToolTip(result, tag));
        return result;
    }

    private List<ResourceLocation> getMatchingTags(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return List.of();
        }
        var block = blockItem.getBlock();
        return Arrays.stream(tags)
                .filter(Objects::nonNull)
                .filter(tag -> block.builtInRegistryHolder().is(BlockTags.create(tag)))
                .toList();
    }

    private static void addBlockTagToolTip(List<Component> tooltips, ResourceLocation tag) {
        tooltips.add(Component.translatable(
                "mbd2.multiblock.pattern.block_tag",
                Component.literal("#" + tag)));
    }

    /**
     * Adds editor controls for block-tag IDs.
     *
     * <p>Side effects: appends a searchable array configurator that mutates the
     * tag list and rebuilds the predicate on add, remove, or update.</p>
     *
     * @param father parent configurator group receiving child controls
     */
    @Override
    public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        var tagsConfigurator = new ArrayConfiguratorGroup<>("config.predicate.tags", false,
                () -> Arrays.stream(tags).toList(), (getter, setter) ->
                new SearchComponentConfigurator<>("", getter, setter, BlockTags.SAND.location(), true, (word, find) -> {
                    var tags = ForgeRegistries.BLOCKS.tags();
                    if (tags == null) return;
                    for (var tag : tags) {
                        if (Thread.currentThread().isInterrupted()) return;
                        var tagKey = tag.getKey().location();
                        if (tagKey.toString().toLowerCase().contains(word.toLowerCase())) {
                            find.accept(tagKey);
                        }
                    }
                }, ResourceLocation::toString), true);
        tagsConfigurator.setAddDefault(BlockTags.SAND::location);
        tagsConfigurator.setOnAdd(value -> {
            tags = Arrays.copyOf(this.tags, this.tags.length + 1);
            tags[tags.length - 1] = value;
            buildPredicate();
        });
        tagsConfigurator.setOnRemove(value -> {
            tags = Arrays.stream(this.tags).filter(tag -> !tag.equals(value)).toArray(ResourceLocation[]::new);
            buildPredicate();
        });
        tagsConfigurator.setOnUpdate(list -> {
            tags = list.toArray(new ResourceLocation[0]);
            buildPredicate();
        });
        father.addConfigurators(tagsConfigurator);
    }
}
