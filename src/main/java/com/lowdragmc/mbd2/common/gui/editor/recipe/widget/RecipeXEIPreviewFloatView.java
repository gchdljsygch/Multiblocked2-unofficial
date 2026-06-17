package com.lowdragmc.mbd2.common.gui.editor.recipe.widget;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.ui.view.FloatViewWidget;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;
import com.lowdragmc.mbd2.api.recipe.MBDRecipe;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeSerializer;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.RecipeTypeProject;
import net.minecraft.nbt.CompoundTag;

/**
 * Floating live preview for the selected built-in recipe's XEI UI.
 *
 * <p>The view serializes the project's recipe UI template, deserializes it into an isolated
 * client-side widget tree, binds the selected recipe data to that copy, and displays the
 * result. The original editable template is not modified by preview binding. A snapshot of
 * the recipe NBT is kept so the preview can refresh when recipe contents or metadata change.</p>
 *
 * <p>This widget is client editor UI and should be updated on the render/UI thread.</p>
 */
public class RecipeXEIPreviewFloatView extends FloatViewWidget {

    /**
     * Creates an initially empty recipe preview float view.
     */
    public RecipeXEIPreviewFloatView() {
        super(200, 200, 200, 120, false);
    }

    @Override
    public String name() {
        return "recipe_xei_preview";
    }

    @Override
    public String group() {
        return "editor.machine";
    }

    /**
     * Returns the icon shown when the float view is collapsed.
     */
    public IGuiTexture getIcon() {
        return new ProgressTexture();
    }

    @Override
    public IGuiTexture getHoverIcon() {
        return Icons.REMOVE;
    }

    /**
     * Returns the owning machine editor.
     */
    public MachineEditor getEditor() {
        return (MachineEditor) editor;
    }

    private boolean isFuel;
    private MBDRecipe recipe;
    private CompoundTag lastData;

    /**
     * Clears the rendered recipe preview.
     */
    public void clearRecipe() {
        content.clearAllWidgets();
    }

    /**
     * Loads a recipe into an isolated copy of the active recipe UI template.
     *
     * @param isFuel whether the fuel UI template should be used
     * @param recipe recipe to bind into the preview; {@code null} leaves the preview empty
     */
    public void loadRecipe(boolean isFuel, MBDRecipe recipe) {
        clearRecipe();
        this.isFuel = isFuel;
        if (recipe == null) return;
        if (editor.getCurrentProject() instanceof RecipeTypeProject project) {
            this.recipe = recipe;
            lastData = MBDRecipeSerializer.SERIALIZER.toNBT(recipe);
            var tag = IConfigurableWidget.serializeNBT(isFuel ? project.getFuelUI() : project.getUi(), project.getResources(), true);
            var ui = new WidgetGroup();
            ui.setClientSideWidget();
            IConfigurableWidget.deserializeNBT(ui, tag, project.getResources(), true);
            project.getRecipeType().bindXEIRecipeUI(ui, recipe);
            ui.setSelfPosition(0, 0);
            resetSize(ui.getSizeWidth(), ui.getSizeHeight());
            content.addWidget(ui);
        }
    }

    /**
     * Resizes the float view to fit the rendered template.
     *
     * @param width  content width in pixels
     * @param height content height in pixels
     */
    public void resetSize(int width, int height) {
        setSize(width, height + 15);
        clearAllWidgets();
        initWidget();
        if (isCollapse) {
            title.setSize(new Size(15, 15));
            title.setBackground(new GuiTextureGroup(ColorPattern.T_RED.rectTexture().setRadius(5f), ColorPattern.GRAY.borderTexture(-1).setRadius(5f)));
            content.setVisible(false);
            content.setActive(false);
        } else {
            title.setSize(new Size(getSize().width, 15));
            title.setBackground(new GuiTextureGroup(ColorPattern.T_RED.rectTexture().setTopRadius(5f), ColorPattern.GRAY.borderTexture(-1).setTopRadius(5f)));
            content.setVisible(true);
            content.setActive(true);
        }
    }

    /**
     * @deprecated use {@link #resetSize(int, int)}.
     */
    @Deprecated(forRemoval = false)
    public void resetSeize(int width, int height) {
        resetSize(width, height);
    }

    /**
     * Refreshes the preview when the selected recipe's serialized data changes.
     */
    @Override
    public void updateScreen() {
        super.updateScreen();
        if (recipe != null) {
            var data = MBDRecipeSerializer.SERIALIZER.toNBT(recipe);
            if (!data.equals(lastData)) {
                loadRecipe(isFuel, recipe);
            }
        }
    }
}
