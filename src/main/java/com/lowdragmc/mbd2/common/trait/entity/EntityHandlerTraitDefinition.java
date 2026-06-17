package com.lowdragmc.mbd2.common.trait.entity;

import com.lowdragmc.lowdraglib.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.gui.editor.annotation.DefaultValue;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.utils.ColorUtils;
import com.lowdragmc.lowdraglib.utils.ShapeUtils;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineTraitPanel;
import com.lowdragmc.mbd2.common.machine.MBDMachine;
import com.lowdragmc.mbd2.common.trait.ITrait;
import com.lowdragmc.mbd2.common.trait.RecipeCapabilityTraitDefinition;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import lombok.Getter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Definition for a recipe entity handler trait.
 *
 * <p>The definition stores the machine-relative area used both for input entity detection and output spawning.
 * Rotated areas are cached per front-facing direction and invalidated when configuration or serialized state
 * changes.</p>
 */
@LDLRegister(name = "entity_handler", group = "trait", priority = -99)
public class EntityHandlerTraitDefinition extends RecipeCapabilityTraitDefinition {

    @Getter
    @Configurable(name = "config.definition.trait.entity_handler.area", tips = {
            "config.definition.trait.entity_handler.area.tooltip.0",
            "config.definition.trait.entity_handler.area.tooltip.1"
    })
    @DefaultValue(numberValue = {-1, -1, -1, 2, 2, 2})
    private AABB area = new AABB(-1, -1, -1, 2, 2, 2);

    // runtime
    private final Map<Direction, AABB> areaCache = new EnumMap<>(Direction.class);

    /**
     * Creates the runtime trait attached to one machine.
     *
     * @param machine machine instance that will own the entity handler
     * @return a new {@link EntityHandlerTrait}
     */
    @Override
    public ITrait createTrait(MBDMachine machine) {
        return new EntityHandlerTrait(machine, this);
    }

    /**
     * Returns the editor icon for this trait type.
     *
     * @return pig spawn egg texture used to identify entity handlers
     */
    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(Items.PIG_SPAWN_EGG);
    }

    /**
     * Updates the unrotated machine-relative area and clears cached rotations.
     *
     * @param area new scan/spawn area; callers should provide a non-null AABB with min values below max values
     */
    @ConfigSetter(field = "area")
    public void setArea(AABB area) {
        this.area = area;
        areaCache.clear();
    }

    /**
     * Restores persisted settings and invalidates runtime-only rotated-area cache entries.
     *
     * @param tag serialized trait definition tag
     */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        super.deserializeNBT(tag);
        areaCache.clear();
    }

    /**
     * Returns the scan/spawn area rotated to match a machine front.
     *
     * <p>{@code null} and {@link Direction#NORTH} use the stored area directly. Other directions are cached after
     * rotation. The returned AABB is shared configuration/cache state and should be treated as immutable.</p>
     *
     * @param direction machine front direction, or {@code null} for the unrotated area
     * @return machine-relative area for that direction
     */
    public AABB getArea(@Nullable Direction direction) {
        return (direction == Direction.NORTH || direction == null) ? area : this.areaCache.computeIfAbsent(direction, dir -> ShapeUtils.rotate(area, dir));
    }

    /**
     * Draws the configured entity interaction area in the machine trait editor.
     *
     * <p>This method is client-only and mutates render state while drawing; it restores depth testing and culling
     * before returning.</p>
     *
     * @param panel editor panel currently rendering the trait preview
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderAfterWorldInTraitPanel(MachineTraitPanel panel) {
        super.renderAfterWorldInTraitPanel(panel);
        var poseStack = new PoseStack();
        var tessellator = Tesselator.getInstance();
        var buffer = tessellator.getBuilder();

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        poseStack.pushPose();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        RenderSystem.lineWidth(5);

        var color = 0xff11aaee;
        RenderBufferUtils.drawCubeFrame(poseStack, buffer,
                (float) area.minX, (float) area.minY, (float) area.minZ,
                (float) area.maxX, (float) area.maxY, (float) area.maxZ,
                ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color), ColorUtils.alpha(color));

        tessellator.end();

        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
