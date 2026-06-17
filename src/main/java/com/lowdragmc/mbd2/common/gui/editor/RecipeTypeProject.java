package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.ColorsResource;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.EntriesResource;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.TexturesResource;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.UIResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.api.recipe.MBDRecipeType;
import com.lowdragmc.mbd2.common.gui.editor.recipe.RecipeTypePanel;
import com.lowdragmc.mbd2.common.gui.editor.recipe.RecipeXEIUIPanel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Editor project for {@link MBDRecipeType} definitions and their XEI display UIs.
 *
 * <p>The project stores a recipe type, normal recipe UI, fuel recipe UI, and texture/color/entry resources. It is
 * serialized as a single NBT file and loads recipe-type-specific editor tabs when opened in the machine editor.</p>
 */
@Getter
@LDLRegister(name = "rt", group = "editor.machine")
@NoArgsConstructor
public class RecipeTypeProject implements IProject {
    /**
     * Project-local editor resources.
     */
    protected Resources resources;
    /**
     * Recipe type edited by this project.
     */
    protected MBDRecipeType recipeType;
    /**
     * Configurable UI used for normal recipe display.
     */
    protected WidgetGroup ui;
    /**
     * Configurable UI used for fuel recipe display.
     */
    protected WidgetGroup fuelUI;

    /**
     * Creates a recipe-type project with explicit state.
     *
     * @param resources  project resource map
     * @param recipeType recipe type definition
     * @param ui         normal recipe display UI
     * @param fuelUI     fuel recipe display UI
     */
    public RecipeTypeProject(Resources resources, MBDRecipeType recipeType, WidgetGroup ui, WidgetGroup fuelUI) {
        this.resources = resources;
        this.recipeType = recipeType;
        this.ui = ui;
        this.fuelUI = fuelUI;
    }

    /**
     * Creates the default resource set for recipe-type projects.
     *
     * @return ordered resource map containing entries, textures, and colors
     */
    protected Map<String, Resource<?>> createResources() {
        Map<String, Resource<?>> resources = new LinkedHashMap<>();
        // entries
        var entries = new EntriesResource();
        entries.buildDefault();
        resources.put(EntriesResource.RESOURCE_NAME, entries);
        // texture
        var texture = new TexturesResource();
        resources.put(TexturesResource.RESOURCE_NAME, texture);
        // color
        var color = new ColorsResource();
        color.buildDefault();
        resources.put(ColorsResource.RESOURCE_NAME, color);
        return resources;
    }

    /**
     * Creates the default recipe display UI.
     *
     * @return bordered widget group used by new recipe-type projects
     */
    protected WidgetGroup createDefaultUI() {
        var group = new WidgetGroup(200, 50, 176, 100);
        group.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        return group;
    }

    /**
     * Creates the default recipe type definition.
     *
     * @return recipe type with ID {@code mbd2:recipe_type}
     */
    protected MBDRecipeType createDefaultRecipeType() {
        return new MBDRecipeType(MBD2.id("recipe_type"));
    }

    /**
     * Creates a new empty recipe-type project.
     *
     * @return initialized project with default recipe type and UIs
     */
    @Override
    public RecipeTypeProject newEmptyProject() {
        return new RecipeTypeProject(new Resources(createResources()), createDefaultRecipeType(), createDefaultUI(), createDefaultUI());
    }

    /**
     * Serializes resources, both UIs, and recipe-type data.
     *
     * @return project NBT
     */
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        tag.put("resources", resources.serializeNBT());
        tag.put("ui", IConfigurableWidget.serializeNBT(this.ui, resources, true));
        tag.put("fuelUI", IConfigurableWidget.serializeNBT(this.fuelUI, resources, true));
        tag.put("recipe_type", recipeType.serializeNBT());
        return tag;
    }

    /**
     * Loads project resources from NBT.
     *
     * @param tag serialized resource map
     * @return resource set with this project type's default resource kinds
     */
    @Override
    public Resources loadResources(CompoundTag tag) {
        var resources = new Resources(createResources());
        resources.deserializeNBT(tag);
        return resources;
    }

    /**
     * Deserializes recipe-type project state.
     *
     * <p>Older project files without {@code fuelUI} receive a default fuel UI. Texture resources are installed while
     * the recipe type is deserialized so UI resource textures resolve correctly.</p>
     *
     * @param tag project NBT
     */
    public void deserializeNBT(CompoundTag tag) {
        this.resources = loadResources(tag.getCompound("resources"));
        this.ui = new WidgetGroup();
        IConfigurableWidget.deserializeNBT(this.ui, tag.getCompound("ui"), resources, true);
        if (tag.contains("fuelUI")) {
            this.fuelUI = new WidgetGroup();
            IConfigurableWidget.deserializeNBT(this.fuelUI, tag.getCompound("fuelUI"), resources, true);
            if (this.fuelUI.getBackgroundTexture() == null) {
                this.fuelUI.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
            }
        } else {
            this.fuelUI = createDefaultUI();
        }
        this.recipeType = createDefaultRecipeType();
        UIResourceTexture.setCurrentResource((Resource) resources.resources.get(TexturesResource.RESOURCE_NAME), true);
        this.recipeType.deserializeNBT(tag.getCompound("recipe_type"));
        UIResourceTexture.clearCurrentResource();
    }

    /**
     * Returns the workspace directory for recipe-type projects.
     *
     * @param editor owning editor
     * @return {@code recipe_type} subdirectory under the editor workspace
     */
    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "recipe_type");
    }

    /**
     * Writes the project NBT to disk.
     *
     * @param file target project file
     */
    @Override
    public void saveProject(File file) {
        try {
            NbtIo.write(serializeNBT(), file);
        } catch (IOException ignored) {
        }
    }

    /**
     * Loads a recipe-type project from disk.
     *
     * @param file source project file
     * @return loaded project, or {@code null} when the file cannot be read
     */
    @Nullable
    @Override
    public IProject loadProject(File file) {
        try {
            var tag = NbtIo.read(file);
            if (tag != null) {
                var proj = new RecipeTypeProject();
                proj.deserializeNBT(tag);
                return proj;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Adds recipe-type editor tabs when the project is opened.
     *
     * @param editor editor receiving the project's tabs and resources
     */
    @Override
    public void onLoad(Editor editor) {
        if (editor instanceof MachineEditor machineEditor) {
            IProject.super.onLoad(editor);
            var tabContainer = machineEditor.getTabPages();
            var recipeTypePanel = new RecipeTypePanel(recipeType, machineEditor);
            var recipeXEIUIPanel = new RecipeXEIUIPanel(machineEditor, getUi(), false);
            var fuelRecipeXEIUIPanel = new RecipeXEIUIPanel(machineEditor, getFuelUI(), true);
            tabContainer.addTab("editor.machine.recipe_type", recipeTypePanel, recipeTypePanel::onPanelSelected, recipeTypePanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.recipe_xei_ui", recipeXEIUIPanel, recipeXEIUIPanel::onPanelSelected, recipeXEIUIPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.recipe_xei_fuel_ui", fuelRecipeXEIUIPanel, fuelRecipeXEIUIPanel::onPanelSelected, fuelRecipeXEIUIPanel::onPanelDeselected);
        }
    }
}
