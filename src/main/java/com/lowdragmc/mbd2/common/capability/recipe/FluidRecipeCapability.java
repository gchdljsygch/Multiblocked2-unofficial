package com.lowdragmc.mbd2.common.capability.recipe;

import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidEntryList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidStackList;
import com.gregtechceu.gtceu.integration.xei.entry.fluid.FluidTagList;
import com.gregtechceu.gtceu.integration.xei.handlers.fluid.CycleFluidEntryHandler;
import com.lowdragmc.lowdraglib.gui.editor.accessors.CompoundTagAccessor;
import com.lowdragmc.lowdraglib.gui.editor.configurator.*;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.side.fluid.forge.FluidHelperImpl;
import com.lowdragmc.lowdraglib.utils.CycleFluidStorage;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.lowdraglib.utils.TagOrCycleFluidTransfer;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.SerializerFluidIngredient;
import com.lowdragmc.mbd2.api.recipe.ingredient.FluidIngredient;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Recipe capability descriptor for fluid ingredients.
 *
 * <p>The capability renders fixed-amount fluid/tag/NBT requirements, provides editor configurators for candidate
 * fluids/tags and optional NBT, and binds either LowDragLib or GTM tank widgets when available. Recipe IO is handled
 * by fluid tank traits; this descriptor is responsible only for content conversion, display, editing, and diagnostics.</p>
 */
public class FluidRecipeCapability extends RecipeCapability<FluidIngredient> {

    public static final String FLUID_TYPE = "recipe.capability.fluid.ingredient.values.fluid";
    public static final String TAG_TYPE = "recipe.capability.fluid.ingredient.values.tag";

    public final static FluidRecipeCapability CAP = new FluidRecipeCapability();

    /**
     * Creates the singleton fluid recipe capability.
     */
    protected FluidRecipeCapability() {
        super("fluid", SerializerFluidIngredient.INSTANCE);
    }

    /**
     * Returns a representative bucket-sized water requirement.
     *
     * @return water ingredient with amount {@code 1000}
     */
    @Override
    public FluidIngredient createDefaultContent() {
        return FluidIngredient.of(1000, Fluids.WATER);
    }

    /**
     * Creates a tank preview for the supplied fluid ingredient.
     *
     * @param content fluid ingredient to preview
     * @return non-interactive tank widget cycling through matching stacks
     */
    @Override
    public Widget createPreviewWidget(FluidIngredient content) {
        var storage = new CycleFluidStorage(content.getAmount(), Arrays.stream(content.getStacks()).toList());
        return new TankWidget(storage, 0, 0, false, false).setDrawHoverOverlay(false);
    }

    /**
     * Creates the default fluid tank template used in recipe viewers.
     *
     * @return unbound tank widget template
     */
    @Override
    public Widget createXEITemplate() {
        var tankWidget = new TankWidget();
        tankWidget.initTemplate();
        tankWidget.setSize(new Size(20, 58));
        tankWidget.setOverlay(new ResourceTexture("mbd2:textures/gui/fluid_tank_overlay.png"));
        tankWidget.setShowAmount(false);
        return tankWidget;
    }

    /**
     * Binds a fluid ingredient to a LowDragLib or GTM recipe-viewer tank.
     *
     * <p>Tag-only ingredients are exposed as tag entries with the configured amount; concrete fluid candidates are
     * exposed as cycling stacks. The method mutates only the supplied widget.</p>
     *
     * @param widget       tank widget created by this capability or a compatible GTM widget
     * @param content      recipe content wrapper containing a fluid ingredient
     * @param ingredientIO viewer role for input/output display
     */
    @Override
    public void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO) {
        if (widget instanceof TankWidget tankWidget) {
            var fluidIngredient = of(content.content);
            Either<List<Pair<TagKey<Fluid>, Long>>, List<FluidStack>> either = null;
            // if all fluid tags
            if (Arrays.stream(fluidIngredient.values).allMatch(FluidIngredient.TagValue.class::isInstance)) {
                either = Either.left(Arrays.stream(fluidIngredient.values)
                        .map(FluidIngredient.TagValue.class::cast)
                        .map(FluidIngredient.TagValue::getTag)
                        .map(tagValue -> new Pair<>(tagValue, fluidIngredient.getAmount())).toList());
            }
            if (either == null) {
                either = Either.right(List.of(fluidIngredient.getStacks()));
            }
            if (tankWidget.getOverlay() == null || tankWidget.getOverlay() == IGuiTexture.EMPTY) {
                tankWidget.setOverlay(content.createOverlay());
            } else {
                var groupTexture = new GuiTextureGroup(tankWidget.getOverlay(), content.createOverlay());
                tankWidget.setOverlay(groupTexture);
            }
            tankWidget.setFluidTank(new TagOrCycleFluidTransfer(List.of(either)), 0);
            tankWidget.setIngredientIO(ingredientIO);
            tankWidget.setAllowClickDrained(false);
            tankWidget.setAllowClickFilled(false);
            tankWidget.setXEIChance(content.chance);
        } else if (MBD2.isGTMLoaded()) {
            try {
                if (widget instanceof com.gregtechceu.gtceu.api.gui.widget.TankWidget tankWidget) {
                    var fluidIngredient = of(content.content);
                    var fluidEntries = new ArrayList<FluidEntryList>();
                    Either<FluidTagList, FluidStackList> either = null;
                    // if all fluid tags
                    Arrays.stream(fluidIngredient.values).forEach(value -> {
                        if (value instanceof FluidIngredient.TagValue tagValue) {
                            fluidEntries.add(FluidTagList.of(tagValue.getTag(), (int) fluidIngredient.getAmount(), null));
                        } else {
                            fluidEntries.add(FluidStackList.of(Arrays.stream(fluidIngredient.getStacks()).map(FluidHelperImpl::toFluidStack).toList()));
                        }
                    });
                    if (tankWidget.getOverlay() == null || tankWidget.getOverlay() == IGuiTexture.EMPTY) {
                        tankWidget.setOverlay(content.createOverlay());
                    } else {
                        var groupTexture = new GuiTextureGroup(tankWidget.getOverlay(), content.createOverlay());
                        tankWidget.setOverlay(groupTexture);
                    }
                    tankWidget.setFluidTank(new CycleFluidEntryHandler(fluidEntries), 0);
                    tankWidget.setIngredientIO(ingredientIO);
                    tankWidget.setAllowClickDrained(false);
                    tankWidget.setAllowClickFilled(false);
                    tankWidget.setXEIChance(content.chance);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Creates editor configurators for amount, candidate fluids/tags, and optional fluid NBT.
     *
     * @param father   parent configurator group
     * @param supplier current fluid ingredient supplier
     * @param onUpdate callback receiving updated content
     */
    @Override
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<FluidIngredient> supplier, Consumer<FluidIngredient> onUpdate) {
        // sized ingredient amount
        father.addConfigurators(new NumberConfigurator("recipe.capability.fluid.ingredient.amount",
                () -> supplier.get().getAmount(),
                number -> {
                    var amount = number.intValue();
                    onUpdate.accept(supplier.get().copy(amount));
                }, 1, true).setRange(1, Integer.MAX_VALUE));
        // fluid ingredient
        var valuesGroup = new ArrayConfiguratorGroup<>("recipe.capability.fluid.ingredient.candidates", false,
                () -> Arrays.stream(supplier.get().values).collect(Collectors.toList()), (getter, setter) -> {
            // check values type
            return new ConfiguratorSelectorConfigurator<>("recipe.capability.item.ingredient.values.type", false, getter, setter,
                    new FluidIngredient.FluidValue(Fluids.WATER), true,
                    List.of(
                            // values candidates
                            new FluidIngredient.FluidValue(Fluids.WATER),
                            new FluidIngredient.TagValue(FluidTags.LAVA)),
                    value -> {
                        if (value instanceof FluidIngredient.FluidValue) {
                            return FLUID_TYPE;
                        } else if (value instanceof FluidIngredient.TagValue) {
                            return TAG_TYPE;
                        }
                        return FLUID_TYPE;
                    }, (value, valueGroup) -> {
                // preview slot
                var fluidStorage = new CycleFluidStorage(1, value.getStacks().stream().map(fluid -> FluidStack.create(fluid, 1)).toList());
                var tank = new TankWidget(fluidStorage, 0, 0, false, false);
                tank.setBackground(TankWidget.FLUID_SLOT_TEXTURE);
                tank.setShowAmount(false);
                tank.setClientSideWidget();

                if (value instanceof FluidIngredient.FluidValue fluidValue) {
                    // fluid value
                    valueGroup.addConfigurators(new FluidConfigurator(FLUID_TYPE,
                            fluidValue::getFluid,
                            fluid -> {
                                fluidValue.setFluid(fluid);
                                fluidStorage.updateStacks(value.getStacks().stream().map(f -> FluidStack.create(f, 1)).toList());
                                setter.accept(value);
                            },
                            Fluids.WATER, true));
                } else if (value instanceof FluidIngredient.TagValue tagValue) {
                    // tag value
                    valueGroup.addConfigurators(new SearchComponentConfigurator<>(TAG_TYPE,
                            () -> tagValue.getTag().location(), tagKey -> {
                        tagValue.setTag(FluidTags.create(tagKey));
                        fluidStorage.updateStacks(value.getStacks().stream().map(f -> FluidStack.create(f, 1)).toList());
                        setter.accept(value);
                    }, FluidTags.LAVA.location(), true, (word, find) -> {
                        var tags = ForgeRegistries.FLUIDS.tags();
                        if (tags == null) return;
                        for (var tag : tags) {
                            if (Thread.currentThread().isInterrupted()) return;
                            var tagKey = tag.getKey().location();
                            if (tagKey.toString().toLowerCase().contains(word.toLowerCase())) {
                                find.accept(tagKey);
                            }
                        }
                    }, ResourceLocation::toString));
                }
                valueGroup.addConfigurators(new WrapperConfigurator("ldlib.gui.editor.group.preview", tank));
            });
        }, true);
        valuesGroup.setAddDefault(() -> new FluidIngredient.FluidValue(Fluids.WATER));
        valuesGroup.setOnAdd(value -> {
            var fluidIngredient = supplier.get();
            var values = fluidIngredient.values;
            var newValues = Arrays.copyOf(values, values.length + 1);
            newValues[values.length] = value;
            fluidIngredient.values = newValues;
            fluidIngredient.stacks = null;
        });
        valuesGroup.setOnRemove(value -> {
            var fluidIngredient = supplier.get();
            var values = fluidIngredient.values;
            fluidIngredient.values = Arrays.stream(values).filter(v -> v != value).toArray(FluidIngredient.Value[]::new);
            fluidIngredient.stacks = null;
        });
        valuesGroup.setOnUpdate(values -> {
            var fluidIngredient = supplier.get();
            fluidIngredient.values = values.toArray(FluidIngredient.Value[]::new);
            fluidIngredient.stacks = null;
        });
        father.addConfigurators(valuesGroup);
        // fluid nbt
        try {
            father.addConfigurators(new CompoundTagAccessor().create("ldlib.gui.editor.configurator.nbt",
                    () -> Optional.ofNullable(supplier.get().getNbt()).orElseGet(CompoundTag::new),
                    tag -> {
                        var fluidIngredient = supplier.get();
                        var newTag = tag.isEmpty() ? null : tag;
                        if (Objects.equals(newTag, fluidIngredient.getNbt())) return;
                        fluidIngredient.setNbt(newTag);
                        onUpdate.accept(fluidIngredient);
                    }, false, RecipeCapability.class.getField("name")));
        } catch (Exception ignored) {
        }
    }

    /**
     * Builds a human-readable message for unsatisfied fluid ingredients.
     *
     * @param left remaining fluid ingredients after recipe matching
     * @return component listing amount, first display fluid, and NBT requirement where present
     */
    @Override
    public Component getLeftErrorInfo(List<FluidIngredient> left) {
        var result = Component.empty();
        for (int i = 0; i < left.size(); i++) {
            var fluidIngredient = left.get(i);
            result.append(fluidIngredient.getAmount() + "x ");
            var stacks = fluidIngredient.getStacks();
            if (stacks.length > 0) {
                result.append(stacks[0].getDisplayName());
            } else {
                result.append("Unknown");
            }
            if (fluidIngredient.getNbt() != null) {
                result.append(" with NBT");
                result.append(fluidIngredient.getNbt().toString());
            }
            if (i < left.size() - 1) {
                result.append(", ");
            }
        }
        return result;
    }
}
