package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;

/**
 * Base menu tab for the MBD machine editor.
 *
 * <p>The current tab contributes an empty menu root and standardizes translation keys to
 * {@code group.name}. Concrete tabs can extend this class when they need the same editor registration behavior.</p>
 */
@LDLRegister(name = "machine_tab", group = "editor.machine")
public class MachineTab extends MenuTab {

    /**
     * Creates the root menu builder for this tab.
     *
     * @return empty menu root
     */
    protected TreeBuilder.Menu createMenu() {
        return TreeBuilder.Menu.start();
    }

    /**
     * Returns the translation key used for the tab label.
     *
     * @return key in {@code group.name} form
     */
    @Override
    public String getTranslateKey() {
        return "%s.%s".formatted(group(), name());
    }
}
