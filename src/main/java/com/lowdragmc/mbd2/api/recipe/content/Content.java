package com.lowdragmc.mbd2.api.recipe.content;

import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.NumberRange;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Wrapper around one capability-specific recipe content value plus recipe IO
 * metadata.
 *
 * <p>The business goal is to attach common recipe behavior such as per-tick
 * handling, chance, tier chance boosts, slot routing, and UI binding names to
 * otherwise capability-specific content. Instances are mutable for editor use
 * and should be confined to the recipe-building or recipe-processing thread that
 * owns them.</p>
 */
public class Content {
    @Getter
    public Object content;
    @Configurable(name = "editor.machine.recipe_type.content.per_tick", tips = "editor.machine.recipe_type.content.per_tick.tooltip")
    public boolean perTick;
    @Configurable(name = "editor.machine.recipe_type.content.chance", tips = "editor.machine.recipe_type.content.chance.tooltip")
    @NumberRange(range = {0f, 1f})
    public float chance;
    @Configurable(name = "editor.machine.recipe_type.content.tier_chance_boost", tips = {
            "editor.machine.recipe_type.content.tier_chance_boost.tooltip.0",
            "editor.machine.recipe_type.content.tier_chance_boost.tooltip.1"
    })
    @NumberRange(range = {0f, 1f})
    public float tierChanceBoost;
    @Configurable(name = "editor.machine.recipe_type.content.slot_name", tips = "editor.machine.recipe_type.content.slot_name.tooltip")
    @Nonnull
    public String slotName;
    @Configurable(name = "editor.machine.recipe_type.content.ui_name", tips = "editor.machine.recipe_type.content.ui_name.tooltip")
    @Nonnull
    public String uiName;

    /**
     * Creates a content wrapper with full routing metadata.
     *
     * @param content         capability-specific value; interpretation is defined by the
     *                        owning {@link RecipeCapability}
     * @param perTick         {@code true} when this content is consumed or produced every
     *                        recipe tick instead of at setup/finish
     * @param chance          chance in {@code [0, 1]}; {@code 0} means not consumable or
     *                        always skipped, depending on IO semantics
     * @param tierChanceBoost additional chance per holder tier, normally in
     *                        {@code [0, 1]}
     * @param slotName        optional recipe slot route; {@code null} is normalized to
     *                        an empty string
     * @param uiName          optional template widget id override; {@code null} is
     *                        normalized to an empty string
     */
    public Content(Object content, boolean perTick, float chance, float tierChanceBoost, @Nullable String slotName, @Nullable String uiName) {
        this.content = content;
        this.perTick = perTick;
        this.chance = chance;
        this.tierChanceBoost = tierChanceBoost;
        this.slotName = slotName == null ? "" : slotName;
        this.uiName = uiName == null ? "" : uiName;
    }

    /**
     * Creates a content wrapper without explicit slot or UI binding names.
     *
     * @param content         capability-specific value
     * @param perTick         {@code true} for per-tick recipe IO
     * @param chance          chance in {@code [0, 1]}
     * @param tierChanceBoost additional chance per holder tier, normally in
     *                        {@code [0, 1]}
     */
    public Content(Object content, boolean perTick, float chance, float tierChanceBoost) {
        this(content, perTick, chance, tierChanceBoost, "", "");
    }

    /**
     * Copies this wrapper using the capability's normal copy semantics.
     *
     * <p>Side effects: none on this instance. The modifier is skipped when
     * {@code chance == 0}, preserving non-consumable marker content sizes.</p>
     *
     * @param capability capability that knows how to copy the wrapped content
     * @param modifier   optional content modifier for size-like fields
     * @return copied content wrapper
     */
    public Content copy(RecipeCapability<?> capability, @Nullable ContentModifier modifier) {
        if (modifier == null || chance == 0) {
            return new Content(capability.copyContent(content), perTick, chance, tierChanceBoost, slotName, uiName);
        } else {
            return new Content(capability.copyContent(content, modifier), perTick, chance, tierChanceBoost, slotName, uiName);
        }
    }

    /**
     * Copies this wrapper using the capability serializer's deep-copy path.
     *
     * <p>Side effects: may serialize and deserialize the wrapped content through
     * the capability serializer. The modifier is skipped when {@code chance == 0}.</p>
     *
     * @param capability capability that knows how to deep-copy the wrapped
     *                   content
     * @param modifier   optional content modifier for size-like fields
     * @return deep-copied content wrapper
     */
    public Content deepCopy(RecipeCapability<?> capability, @Nullable ContentModifier modifier) {
        if (modifier == null || chance == 0) {
            return new Content(capability.deepCopyContent(content), perTick, chance, tierChanceBoost, slotName, uiName);
        } else {
            return new Content(capability.deepCopyContent(content, modifier), perTick, chance, tierChanceBoost, slotName, uiName);
        }
    }

    /**
     * Creates an overlay texture that displays chance and per-tick markers.
     *
     * @return lightweight overlay texture; drawing has client-side GUI side
     * effects only
     */
    public IGuiTexture createOverlay() {
        return new IGuiTexture() {
            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
                var row = 0;
                if (chance < 1) {
                    String s = chance == 0 ? LocalizationUtils.format("mbd2.gui.content.chance_0_short") : String.format("%.1f", chance * 100) + "%";
                    int color = chance == 0 ? 0xff0000 : 0xFFFF00;
                    drawSmallString(graphics, x, y, width, height, row++, s, color);
                }
                if (perTick) {
                    drawSmallString(graphics, x, y, width, height, row++,
                            LocalizationUtils.format("mbd2.gui.content.tips.per_tick_short"), 0xFFFF00);
                }
            }
        };
    }

    /**
     * Draws a scaled label inside a content slot.
     *
     * <p>Client-side only. Side effects: mutates the GUI pose stack during
     * drawing and restores it before returning.</p>
     *
     * @param graphics GUI graphics context
     * @param x        slot x coordinate
     * @param y        slot y coordinate
     * @param width    slot width in pixels
     * @param height   slot height in pixels
     * @param row      zero-based half-height row index
     * @param text     text to draw
     * @param color    ARGB/RGB text color
     */
    @OnlyIn(Dist.CLIENT)
    public void drawSmallString(GuiGraphics graphics, float x, float y, int width, int height, int row, String text, int color) {
        var font = Minecraft.getInstance().font;
        var textWidth = font.width(text);
        var posX = x + (width - textWidth);
        var posY = y + row * font.lineHeight / 2f;
        graphics.pose().pushPose();
        graphics.pose().translate(posX + textWidth, posY + font.lineHeight / 2f, 0);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.pose().translate(-posX - textWidth, -posY - font.lineHeight / 2f, 0);

        graphics.drawString(font, text, (int) posX, (int) posY, color);

        graphics.pose().popPose();
    }

    /**
     * Appends common content metadata tooltips.
     *
     * <p>Side effects: adds chance, tier boost, and per-tick tooltip components
     * to {@code tooltips} when those metadata values are active.</p>
     *
     * @param tooltips mutable tooltip list to append to
     */
    public void appendTooltip(List<Component> tooltips) {
        if (chance != 1) {
            if (chance == 0) {
                tooltips.add(Component.translatable("mbd2.gui.content.chance_0"));
            } else {
                tooltips.add(Component.translatable("mbd2.gui.content.chance_1", (int) (chance * 100) + "%"));
            }
        }
        if (tierChanceBoost != 0) {
            tooltips.add(Component.translatable("mbd2.gui.content.tier_boost", (int) (tierChanceBoost * 100) + "%"));
        }
        if (perTick) {
            tooltips.add(Component.translatable("mbd2.gui.content.per_tick"));
        }
    }
}
