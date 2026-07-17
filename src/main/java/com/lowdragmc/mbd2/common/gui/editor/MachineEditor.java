package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.ILDLRegisterClient;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
import com.lowdragmc.lowdraglib.gui.editor.ui.*;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * LowDragLib editor surface for MBD machine and recipe-type projects.
 *
 * <p>The editor owns the standard tool, resource, menu, tab, config, and floating-view panels used by machine
 * definitions. It only accepts {@link MachineProject} and {@link RecipeTypeProject} instances, which keeps editor tabs
 * and resource containers aligned with the project serialization formats.</p>
 */
@LDLRegisterClient(name = "editor.machine", group = "editor")
@OnlyIn(Dist.CLIENT)
public class MachineEditor extends Editor implements ILDLRegisterClient {
    /**
     * Primary configuration tab used for normal widget/property editing.
     */
    public static final ConfigPanel.Tab BASIC = ConfigPanel.Tab.WIDGET;
    /**
     * Secondary configuration tab for project-specific custom panels.
     */
    public static final ConfigPanel.Tab SECOND = ConfigPanel.Tab.createTab(Icons.FILE, Component.translatable("editor.config_panel.other_configurator"));
    /**
     * Resource configuration tab.
     */
    public static final ConfigPanel.Tab RESOURCE = ConfigPanel.Tab.RESOURCE;

    /**
     * Creates an editor rooted at the MBD workspace directory.
     */
    public MachineEditor() {
        super(MBD2.getLocation());
    }

    /**
     * Builds and attaches the editor's panel widgets.
     */
    public void initEditorViews() {
        // LDLib menu tab constructors read Editor.INSTANCE while MenuPanel initializes tabs.
        Editor.INSTANCE = this;

        this.toolPanel = new ToolPanel(this);
        this.toolPanel.setSizeWidth(150);
        this.configPanel = new ConfigPanel(this, List.of(BASIC, SECOND, RESOURCE));
        this.tabPages = new StringTabContainer(this);
        this.resourcePanel = new ResourcePanel(this);
        this.menuPanel = new MenuPanel(this);
        this.floatView = new WidgetGroup(0, 0, this.getSize().width, this.getSize().height);

        this.addWidget(this.tabPages);
        this.addWidget(this.toolPanel);
        this.addWidget(this.configPanel);
        this.addWidget(this.resourcePanel);
        this.addWidget(this.menuPanel);
        this.addWidget(this.floatView);
    }

    /**
     * Loads a supported machine-editor project.
     *
     * @param project project to load, or {@code null} to clear the editor
     * @throws IllegalArgumentException when the project type is not supported by this editor
     */
    @Override
    public void loadProject(IProject project) {
        if (project == null || project instanceof MachineProject || project instanceof RecipeTypeProject) {
            super.loadProject(project);
        } else {
            throw new IllegalArgumentException("Invalid project type");
        }
    }

    /**
     * Saves through project implementations that can report a real write result.
     */
    @Override
    public void saveProject(BooleanConsumer callback) {
        var project = getCurrentProject();
        if (project == null) {
            callback.accept(false);
            return;
        }
        var currentFile = getCurrentProjectFile();
        if (currentFile == null) {
            saveAsProject(callback);
            return;
        }
        var target = saveTarget(project, currentFile);
        if (saveProjectToFile(project, target)) {
            setCurrentProjectFile(target);
            DialogWidget.showNotification(this, "ldlib.gui.editor.menu.save", "ldlib.gui.compass.save_success");
            callback.accept(true);
        } else {
            showSaveFailure();
            callback.accept(false);
        }
    }

    /**
     * Saves through the same file picker as LDLib while delaying editor state changes until the write succeeds.
     */
    @Override
    public void saveAsProject(BooleanConsumer callback) {
        var project = getCurrentProject();
        if (project == null) {
            callback.accept(false);
            return;
        }
        var suffix = "." + project.getSuffix();
        DialogWidget.showFileDialog(this,
                "ldlib.gui.editor.tips.save_as",
                project.getProjectWorkSpace(this),
                false,
                DialogWidget.suffixFilter(suffix),
                selected -> {
                    if (selected == null || selected.isDirectory()) {
                        callback.accept(false);
                        return;
                    }
                    var requested = withSuffix(selected, suffix);
                    var target = saveTarget(project, requested);
                    if (saveProjectToFile(project, target)) {
                        setCurrentProjectFile(target);
                        DialogWidget.showNotification(this, "ldlib.gui.editor.menu.save", "ldlib.gui.compass.save_success");
                        callback.accept(true);
                    } else {
                        showSaveFailure();
                        callback.accept(false);
                    }
                });
    }

    /**
     * Compares the expanded multiblock project with its split manifest, while retaining defensive handling for all
     * editor project files.
     */
    @Override
    public boolean isCurrentProjectSaved() {
        var project = getCurrentProject();
        if (project == null) {
            return true;
        }
        var file = getCurrentProjectFile();
        if (file == null) {
            return false;
        }
        try {
            CompoundTag saved;
            if (project instanceof MultiblockMachineProject) {
                saved = MultiblockMachineProject.readProjectFile(file);
            } else {
                saved = NbtIo.read(file);
            }
            return saved != null && saved.equals(project.serializeNBT());
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Keeps the editor's current file aligned with the canonical multiblock manifest path.
     */
    @Override
    public void setCurrentProjectFile(File file) {
        if (file != null && getCurrentProject() instanceof MultiblockMachineProject) {
            file = MultiblockMachineProject.existingProjectFile(file);
        }
        super.setCurrentProjectFile(file);
    }

    private boolean saveProjectToFile(IProject project, File file) {
        try {
            if (project instanceof MultiblockMachineProject multiblockProject) {
                return multiblockProject.saveProjectChecked(file);
            }
            if (project instanceof MachineProject machineProject) {
                return machineProject.saveProjectChecked(file);
            }
            if (project instanceof RecipeTypeProject recipeProject) {
                return recipeProject.saveProjectChecked(file);
            }
            MBD2.LOGGER.error("Unsupported editor project type: {}", project.getClass().getName());
            return false;
        } catch (RuntimeException e) {
            MBD2.LOGGER.error("Failed to save editor project {}", file, e);
            return false;
        }
    }

    private static File saveTarget(IProject project, File requested) {
        return project instanceof MultiblockMachineProject ?
                MultiblockMachineProject.projectManifestFile(requested) : requested;
    }

    private static File withSuffix(File file, String suffix) {
        if (file.getName().endsWith(suffix)) {
            return file;
        }
        var parent = file.getParentFile();
        var name = file.getName() + suffix;
        return parent == null ? new File(name) : new File(parent, name);
    }

    private void showSaveFailure() {
        DialogWidget.showNotification(this, "ldlib.gui.editor.menu.save", "mbd2.gui.editor.save_failed");
    }
}
