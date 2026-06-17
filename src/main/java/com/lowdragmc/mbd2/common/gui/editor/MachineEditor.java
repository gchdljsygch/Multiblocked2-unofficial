package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.ILDLRegisterClient;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib.gui.editor.data.IProject;
import com.lowdragmc.lowdraglib.gui.editor.ui.*;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.mbd2.MBD2;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

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
}
