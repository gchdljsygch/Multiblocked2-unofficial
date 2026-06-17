package com.lowdragmc.mbd2.common.gui.editor.recipe.widget;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.ui.view.FloatViewWidget;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.mbd2.api.capability.recipe.IO;
import com.lowdragmc.mbd2.api.capability.recipe.RecipeCapability;
import com.lowdragmc.mbd2.api.recipe.content.ContentWidget;
import com.lowdragmc.mbd2.common.gui.editor.MachineEditor;
import com.lowdragmc.mbd2.common.gui.editor.RecipeTypeProject;
import com.lowdragmc.mbd2.common.recipe.DimensionCondition;
import com.lowdragmc.mbd2.utils.WidgetUtils;
import lombok.Getter;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Floating helper palette for generating recipe XEI UI template widgets.
 *
 * <p>The palette inspects the active {@link RecipeTypeProject}, offers fixed helpers such
 * as progress, duration, and condition labels, and derives capability slot buttons from the
 * maximum input/output counts used by the built-in recipes. Buttons add widgets to either
 * the common recipe UI or the fuel UI depending on {@link #isFuel}; generated widgets use
 * stable {@code @...} ids so the runtime binder can update them for individual recipes.</p>
 *
 * <p>This is client editor UI and should only mutate widgets on the render/UI thread.</p>
 */
@Getter
public class RecipeTypeUIFloatView extends FloatViewWidget {

    protected final DraggableScrollableWidgetGroup uiList;
    private final boolean isFuel;

    /**
     * Creates a helper palette for a recipe UI type.
     *
     * @param isFuel whether helper buttons target the fuel recipe UI template
     */
    public RecipeTypeUIFloatView(boolean isFuel) {
        super(200, 200, 206, 120, false);
        this.isFuel = isFuel;
        uiList = new DraggableScrollableWidgetGroup(5, 5, 196, 110);
        uiList.setYScrollBarWidth(2).setYBarStyle(null, ColorPattern.T_WHITE.rectTexture().setRadius(1).transform(-0.5f, 0));
    }

    @Override
    public String name() {
        return "recipe_type_ui_view";
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

    @Override
    public void initWidget() {
        super.initWidget();
        content.addWidget(uiList);
        reloadList();
    }

    /**
     * Rebuilds helper buttons from the active recipe type project.
     *
     * <p>Existing widgets in the target template are not removed. Each helper checks for the
     * expected id before adding a new widget so repeated clicks are idempotent for singleton
     * widgets and fill missing capability slots for slot helpers.</p>
     */
    public void reloadList() {
        uiList.clearAllWidgets();
        if (getEditor().getCurrentProject() instanceof RecipeTypeProject project) {
            var ui = isFuel ? project.getFuelUI() : project.getUi();
            // create progress bar
            addButton(new ImageWidget(0, 0, 18, 18, new ProgressTexture(
                    new ResourceTexture("mbd2:textures/gui/arrow_bar.png").getSubTexture(0, 0, 1, 0.5),
                    new ResourceTexture("mbd2:textures/gui/arrow_bar.png").getSubTexture(0, 0.5, 1, 0.5)
            )), () -> "editor.machine.recipe_type_ui_view.progress", () -> {
                if (WidgetUtils.getFirstWidgetById(ui, "^@progress_bar$") == null) {
                    var progress = new ProgressWidget(ProgressWidget.JEIProgress, 5, 5, 18, 18, new ProgressTexture(
                            new ResourceTexture("mbd2:textures/gui/arrow_bar.png").getSubTexture(0, 0, 1, 0.5),
                            new ResourceTexture("mbd2:textures/gui/arrow_bar.png").getSubTexture(0, 0.5, 1, 0.5)
                    ));
                    progress.setId("@progress_bar");
                    ui.addWidget(progress);
                }
            });

            // create duration label
            addButton(new ImageWidget(0, 0, 18, 18, Icons.FILE), () -> "editor.machine.recipe_type_ui_view.duration", () -> {
                if (WidgetUtils.getFirstWidgetById(ui, "^@duration$") == null) {
                    var duration = new LabelWidget(5, 5, Component.translatable("recipe.duration.value", 100));
                    duration.setId("@duration");
                    ui.addWidget(duration);
                }
            });

            // create conditions
            addButton(new ImageWidget(0, 0, 18, 18, DimensionCondition.INSTANCE.getIcon()), () -> "editor.machine.recipe_type_ui_view.condition", () -> {
                if (WidgetUtils.getFirstWidgetById(ui, "^@condition$") == null) {
                    var duration = new TextBoxWidget(5, 5, ui.getSizeWidth() - 10, List.of(DimensionCondition.INSTANCE.getTooltips().getString()));
                    duration.isShadow = true;
                    duration.fontColor = -1;
                    duration.setId("@condition");
                    ui.addWidget(duration);
                }
            });

            // create button to generate ui
            Map<RecipeCapability<?>, Integer> maxInputs = new HashMap<>();
            Map<RecipeCapability<?>, Integer> maxOutputs = new HashMap<>();
            for (var recipe : project.getRecipeType().getBuiltinRecipes().values()) {
                if (recipe.isFuel != isFuel) continue;
                for (var entry : recipe.inputs.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        var cap = maxInputs.getOrDefault(entry.getKey(), 0);
                        maxInputs.put(entry.getKey(), Math.max(cap, entry.getValue().size()));
                    }
                }
                for (var entry : recipe.outputs.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        var cap = maxOutputs.getOrDefault(entry.getKey(), 0);
                        maxOutputs.put(entry.getKey(), Math.max(cap, entry.getValue().size()));
                    }
                }
            }
            maxInputs.forEach((cap, maxSize) -> addCap(cap, maxSize, IO.IN));
            maxOutputs.forEach((cap, maxSize) -> addCap(cap, maxSize, IO.OUT));
        }
    }

    /**
     * Adds one helper row to the float-view list.
     *
     * @param icon    preview icon widget; its position is overwritten for the row
     * @param value   label supplier used to show current helper status
     * @param onClick action that mutates the target UI template
     */
    public void addButton(Widget icon, Supplier<String> value, Runnable onClick) {
        int yOffset = 3 + uiList.getAllWidgetSize() * 20;
        var widgetGroup = new WidgetGroup(0, yOffset, 90, 18);
        icon.setSelfPosition(1, 0);
        widgetGroup.addWidget(icon);
        widgetGroup.addWidget(new ImageWidget(20, 1, 120, 18,
                new TextTexture().setSupplier(value).setType(TextTexture.TextType.ROLL_ALWAYS).setWidth(120)));
        widgetGroup.addWidget(new ButtonWidget(145, 2, 45, 14,
                new GuiTextureGroup(ColorPattern.T_RED.rectTexture().setRadius(7),
                        ColorPattern.WHITE.borderTexture(-1).setRadius(7), new TextTexture("editor.machine.recipe_type_ui_view.add")),
                cd -> onClick.run())
                .setHoverTexture(new GuiTextureGroup(ColorPattern.T_RED.rectTexture().setRadius(7),
                        ColorPattern.GREEN.borderTexture(-1).setRadius(7), new TextTexture("editor.machine.recipe_type_ui_view.add"))));
        uiList.addWidget(widgetGroup);
    }

    /**
     * Adds a helper row that creates missing XEI template slots for one capability.
     *
     * @param cap     capability whose template widgets are generated
     * @param maxSize maximum slot count observed in built-in recipes for this capability
     * @param io      side represented by the generated template ids
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addCap(RecipeCapability cap, int maxSize, IO io) {
        if (getEditor().getCurrentProject() instanceof RecipeTypeProject project) {
            var ui = isFuel ? project.getFuelUI() : project.getUi();
            addButton(cap.createPreviewWidget(cap.createDefaultContent()), () -> {
                var found = 0;
                for (int i = 0; i < maxSize; i++) {
                    var id = "@%s_%s_%d".formatted(cap.name, io.name, i);
                    if (WidgetUtils.getFirstWidgetById(ui, "^%s$".formatted(id)) != null) {
                        found++;
                    }
                }
                if (found < maxSize) {
                    return "%s: §e%d§r/ %d".formatted(
                            io.name,
                            found,
                            maxSize);
                } else {
                    return "%s: §2%d§r/ %d".formatted(
                            io.name,
                            found,
                            maxSize);
                }
            }, () -> {
                var x = 5;
                for (int i = 0; i < maxSize; i++) {
                    var id = "@%s_%s_%d".formatted(cap.name, io.name, i);
                    if (WidgetUtils.getFirstWidgetById(ui, "^%s$".formatted(id)) == null) {
                        var template = cap.createXEITemplate();
                        template.setSelfPosition(x, 5);
                        template.setId(id);
                        x += template.getSize().width;
                        ui.addWidget(template);
                    }
                }
            });
        }
    }

}
