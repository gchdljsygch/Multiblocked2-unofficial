package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.client.renderer.IRenderer;
import com.lowdragmc.lowdraglib.client.renderer.impl.IModelRenderer;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.*;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.mbd2.MBD2;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineConfigPanel;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineEventsPanel;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineTraitPanel;
import com.lowdragmc.mbd2.common.gui.editor.machine.MachineUIPanel;
import com.lowdragmc.mbd2.common.machine.definition.MBDMachineDefinition;
import com.lowdragmc.mbd2.common.machine.definition.config.*;
import com.lowdragmc.mbd2.utils.UIResourceRendererContext;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Base editor project for block-backed MBD machine definitions.
 *
 * <p>A machine project owns the editor resources, the {@link MBDMachineDefinition}, and the configurable machine UI
 * widget tree. Projects are serialized as NBT files with {@code resources}, {@code definition}, and {@code ui}
 * sections. Loading a project registers the machine editor tabs needed to edit basic settings, traits, events, and UI.
 * Instances are editor/UI state and should be used on the client editor thread.</p>
 */
@Getter
@LDLRegister(name = "sm", group = "editor.machine")
@NoArgsConstructor
public class MachineProject implements IProject {
    /**
     * Default renderer used for new block-machine projects.
     */
    public static final IRenderer FURNACE_RENDERER = new IModelRenderer(ResourceLocation.parse("block/furnace"));

    /**
     * Project-local editor resources.
     */
    protected Resources resources;
    /**
     * Machine definition edited by this project.
     */
    protected MBDMachineDefinition definition;
    /**
     * Configurable machine UI root.
     */
    protected WidgetGroup ui;

    /**
     * Creates a machine project with explicit resources, definition, and UI.
     *
     * @param resources  project resource map
     * @param definition machine definition to edit
     * @param ui         configurable UI root
     */
    public MachineProject(Resources resources, MBDMachineDefinition definition, WidgetGroup ui) {
        this.resources = resources;
        this.definition = definition;
        this.ui = ui;
        if (this.definition != null) {
            this.definition.loadFactory();
        }
    }

    /**
     * Creates the default resource set for a block-machine project.
     *
     * @return ordered resource map containing entries, renderers, textures, and colors
     */
    protected Map<String, Resource<?>> createResources() {
        Map<String, Resource<?>> resources = new LinkedHashMap<>();
        // entries
        var entries = new EntriesResource();
        entries.buildDefault();
        resources.put(EntriesResource.RESOURCE_NAME, entries);
        // renderer
        var renderer = new IRendererResource();
        resources.put(IRendererResource.RESOURCE_NAME, renderer);
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
     * Creates the default block-machine definition for a new project.
     *
     * @return machine definition using the sample furnace renderer and {@code mbd2:new_machine} ID
     */
    protected MBDMachineDefinition createDefinition() {
        // use vanilla furnace model as an example
        return MBDMachineDefinition.builder()
                .id(MBD2.id("new_machine"))
                .rootState(StateMachine.createSingleDefault(MachineState::builder, FURNACE_RENDERER))
                .build();
    }

    /**
     * Creates the default configurable machine UI.
     *
     * @return widget group containing a bordered background and player inventory
     */
    protected WidgetGroup createDefaultUI() {
        var group = new WidgetGroup(150, 50, 176, 180);
        group.setBackground(ResourceBorderTexture.BORDERED_BACKGROUND);
        var inventory = new PlayerInventoryWidget();
        inventory.setSelfPosition(new Position((group.getSize().width - inventory.getSize().width) / 2,
                group.getSize().height - 2 - inventory.getSize().height));
        group.addWidget(inventory);
        return group;
    }

    /**
     * Creates a new empty block-machine project.
     *
     * @return initialized project with default resources, definition, and UI
     */
    @Override
    public MachineProject newEmptyProject() {
        return new MachineProject(new Resources(createResources()), createDefinition(), createDefaultUI());
    }

    /**
     * Returns the workspace directory for block-machine projects.
     *
     * @param editor owning editor
     * @return {@code machine} subdirectory under the editor workspace
     */
    @Override
    public File getProjectWorkSpace(Editor editor) {
        return new File(editor.getWorkSpace(), "machine");
    }

    /**
     * Serializes the project to NBT.
     *
     * <p>Renderer resources are pushed while serializing definitions and UI so resource-backed renderers are written in
     * a stable editor format.</p>
     *
     * @return project tag containing resources, definition, and UI
     */
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        tag.put("resources", resources.serializeNBT());
        try (var ignored = UIResourceRendererContext.push(getRendererResource(), true)) {
            tag.put("definition", definition.serializeNBT());
        }
        tag.put("ui", IConfigurableWidget.serializeNBT(this.ui, resources, true));
        return tag;
    }

    /**
     * Loads project resources from an NBT resource section.
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
     * Deserializes the project from NBT.
     *
     * @param tag project tag containing resources, definition, and UI
     */
    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || !tag.contains("resources", Tag.TAG_COMPOUND) ||
                !tag.contains("definition", Tag.TAG_COMPOUND) || !tag.contains("ui", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Machine project is missing resources, definition, or UI data");
        }
        this.resources = loadResources(tag.getCompound("resources"));
        if (this.definition == null) {
            this.definition = createDefinition();
            this.definition.loadFactory();
        }
        try (var ignored = UIResourceRendererContext.push(getRendererResource(), true)) {
            this.definition.deserializeNBT(tag.getCompound("definition"));
        }
        this.ui = new WidgetGroup();
        IConfigurableWidget.deserializeNBT(this.ui, tag.getCompound("ui"), resources, true);
    }

    /**
     * Returns the renderer resource container used by UI-resource serialization.
     *
     * @return renderer resource from this project's resource map
     */
    @SuppressWarnings("unchecked")
    protected Resource<IRenderer> getRendererResource() {
        return (Resource<IRenderer>) resources.resources.get(IRendererResource.RESOURCE_NAME);
    }

    /**
     * Writes the project NBT to disk.
     *
     * @param file target project file
     */
    @Override
    public void saveProject(File file) {
        saveProjectChecked(file);
    }

    /**
     * Writes the project NBT to disk and reports whether the complete write succeeded.
     *
     * <p>The editor framework's {@code IProject.saveProject} method has a void return type and suppresses I/O
     * failures. Editor callers use this method so a failed save cannot be reported as successful.</p>
     *
     * @param file target project file
     * @return {@code true} when the target was atomically replaced and verified
     */
    public boolean saveProjectChecked(File file) {
        try {
            writeCompoundTagAtomically(file, serializeNBT());
            return true;
        } catch (IOException | RuntimeException e) {
            MBD2.LOGGER.error("Failed to save machine project {}", file, e);
            return false;
        }
    }

    /**
     * Loads a machine project into a fresh instance of the registered project type.
     *
     * @param file source project file
     * @return loaded project, or {@code null} when the file is missing, malformed, or cannot be read
     */
    @Nullable
    @Override
    public IProject loadProject(File file) {
        try {
            var tag = NbtIo.read(file);
            if (tag == null) {
                return null;
            }
            var project = newEmptyProject();
            project.deserializeNBT(tag);
            return project;
        } catch (IOException | RuntimeException e) {
            MBD2.LOGGER.error("Failed to load machine project {}", file, e);
            return null;
        }
    }

    /**
     * Writes a compound tag to a temporary file, validates the encoded result, and atomically replaces the target.
     */
    protected static void writeCompoundTagAtomically(File file, CompoundTag tag) throws IOException {
        if (file == null) {
            throw new IOException("Project file must not be null");
        }
        Path target = file.getAbsoluteFile().toPath();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Project file has no parent directory: " + file);
        }
        Files.createDirectories(parent);
        if (Files.isDirectory(target)) {
            throw new IOException("Project target is a directory: " + file);
        }

        Path temporary = Files.createTempFile(parent, "." + safeTempPrefix(file.getName()), ".tmp");
        boolean moved = false;
        try {
            NbtIo.write(tag, temporary.toFile());
            var written = NbtIo.read(temporary.toFile());
            if (written == null || !written.equals(tag)) {
                throw new IOException("Written project data did not pass verification: " + file);
            }
            moveAtomically(temporary, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /**
     * Replaces a file using an atomic move when the filesystem supports it, with a same-filesystem replacement fallback.
     */
    protected static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safeTempPrefix(String fileName) {
        var prefix = "." + fileName + ".";
        return prefix.length() >= 3 ? prefix : ".project.";
    }

    /**
     * Adds machine-editing tabs when the project is loaded into a {@link MachineEditor}.
     *
     * @param editor editor receiving the project's tabs and resources
     */
    @Override
    public void onLoad(Editor editor) {
        if (editor instanceof MachineEditor machineEditor) {
            IProject.super.onLoad(editor);
            var tabContainer = machineEditor.getTabPages();
            var machineConfigPanel = createMachineConfigPanel(machineEditor);
            var machineTraitPanel = createMachineTraitPanel(machineEditor);
            var machineEventsPanel = createMachineEventsPanel(machineEditor);
            var machineUIPanel = createMachineUIPanel(machineEditor);
            tabContainer.addTab("editor.machine.basic_settings", machineConfigPanel, machineConfigPanel::onPanelSelected);
            tabContainer.addTab("editor.machine.machine_traits", machineTraitPanel, machineTraitPanel::onPanelSelected, machineTraitPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.machine_events", machineEventsPanel, machineEventsPanel::onPanelSelected, machineEventsPanel::onPanelDeselected);
            tabContainer.addTab("editor.machine.machine_ui", machineUIPanel, machineUIPanel::onPanelSelected, machineUIPanel::onPanelDeselected);
        }
    }

    /**
     * Creates the basic machine configuration panel.
     */
    protected MachineConfigPanel createMachineConfigPanel(MachineEditor editor) {
        return new MachineConfigPanel(editor);
    }

    /**
     * Creates the machine trait panel.
     */
    protected MachineTraitPanel createMachineTraitPanel(MachineEditor editor) {
        return new MachineTraitPanel(editor);
    }

    /**
     * Creates the machine event graph panel.
     */
    protected MachineEventsPanel createMachineEventsPanel(MachineEditor editor) {
        return new MachineEventsPanel(editor);
    }

    /**
     * Creates the machine UI editor panel.
     */
    protected MachineUIPanel createMachineUIPanel(MachineEditor editor) {
        return new MachineUIPanel(editor);
    }

    /**
     * Clears floating editor widgets when the project closes.
     *
     * @param editor editor being closed
     */
    @Override
    public void onClosed(Editor editor) {
        editor.getFloatView().clearAllWidgets();
    }
}
