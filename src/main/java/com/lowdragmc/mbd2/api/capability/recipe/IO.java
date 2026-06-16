package com.lowdragmc.mbd2.api.capability.recipe;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import lombok.Getter;

/**
 * Direction marker for recipe capability handlers and recipe content.
 *
 * <p>The business goal is to distinguish handlers that consume recipe inputs,
 * emit recipe outputs, accept both roles, or should be ignored by recipe logic.</p>
 */
@Getter
public enum IO {
    /**
     * Recipe input side: content is consumed from a handler.
     */
    IN("import"),
    /**
     * Recipe output side: content is inserted into or emitted by a handler.
     */
    OUT("export"),
    /**
     * Handler or pattern position supports both input and output semantics.
     */
    BOTH("both"),
    /**
     * Handler or pattern position does not participate in recipe IO.
     */
    NONE("none");

    public final String name;
    public final IGuiTexture icon;

    IO(String name) {
        this.name = name;
        this.icon = Icons.borderText(getTooltip());
    }

    /**
     * Returns the translation key used for UI tooltips.
     *
     * @return translation key of the form {@code gui.mbd2.io.<name>}
     */
    public String getTooltip() {
        return "gui.mbd2.io." + name;
    }

    /**
     * Tests whether this handler direction can satisfy a requested recipe
     * direction.
     *
     * @param io requested recipe IO; normally {@link #IN} or {@link #OUT}
     * @return {@code true} for exact matches or when this value is
     * {@link #BOTH}; {@code false} for {@link #NONE}
     */
    public boolean support(IO io) {
        if (io == this) return true;
        if (io == NONE) return false;
        return this == BOTH;
    }

}
