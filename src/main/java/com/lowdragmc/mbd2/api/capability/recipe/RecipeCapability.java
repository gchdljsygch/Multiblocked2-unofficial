package com.lowdragmc.mbd2.api.capability.recipe;

import com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.mbd2.api.recipe.content.Content;
import com.lowdragmc.mbd2.api.recipe.content.ContentModifier;
import com.lowdragmc.mbd2.api.recipe.content.IContentSerializer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Describes a typed recipe capability such as items, fluids, energy, or custom
 * machine resources.
 *
 * <p>The business goal is to centralize conversion, copying, UI preview, XEI
 * binding, and error messaging for one recipe content type. Implementations are
 * usually stateless singletons and should keep serializer and UI factory methods
 * side-effect free except for mutating supplied configurator/widget objects.</p>
 */
public abstract class RecipeCapability<T> {

    public final String name;
    public final IContentSerializer<T> serializer;

    /**
     * Creates a capability descriptor.
     *
     * @param name       stable capability name used for translation keys and generated
     *                   UI ids
     * @param serializer serializer/converter for this capability's content type
     */
    protected RecipeCapability(String name, IContentSerializer<T> serializer) {
        this.name = name;
        this.serializer = serializer;
    }

    /**
     * Performs a serializer-level deep copy of content.
     *
     * <p>This method bypasses capability-specific {@link #copyInner(Object)}
     * overrides. Prefer {@link #copyContent(Object)} for normal recipe matching.</p>
     *
     * @param content source content object of type {@code T}
     * @return deep copy produced by the serializer
     */
    public final T deepCopyContent(Object content) {
        return serializer.deepCopyInner((T) content);
    }

    /**
     * Performs a serializer-level deep copy and applies a content modifier.
     *
     * @param content  source content object of type {@code T}
     * @param modifier size/chance modifier to apply to the copied content
     * @return modified deep copy
     */
    public final T deepCopyContent(Object content, ContentModifier modifier) {
        return serializer.copyWithModifier(deepCopyContent(content), modifier);
    }

    /**
     * Copies content for recipe searching and handling.
     *
     * @param content source content value
     * @return copied content value; implementations may return immutable content
     * directly when safe
     */
    public T copyInner(T content) {
        return serializer.copyInner(content);
    }

    /**
     * Copies content and applies a modifier to size-like fields.
     *
     * @param content  source content value
     * @param modifier modifier to apply; exact meaning is serializer-defined
     * @return modified content copy
     */
    public T copyWithModifier(T content, ContentModifier modifier) {
        return serializer.copyWithModifier(content, modifier);
    }

    /**
     * Type-safe wrapper for copying generic recipe content.
     *
     * @param content source content object
     * @return copied content value
     */
    @SuppressWarnings("unchecked")
    public final T copyContent(Object content) {
        return copyInner((T) content);
    }

    /**
     * Type-safe wrapper for copying and modifying generic recipe content.
     *
     * @param content  source content object
     * @param modifier modifier to apply to the copied content
     * @return modified content copy
     */
    @SuppressWarnings("unchecked")
    public final T copyContent(Object content, ContentModifier modifier) {
        return copyWithModifier((T) content, modifier);
    }

    /**
     * Converts a builder/script input object into this capability's content type.
     *
     * @param o value accepted by the serializer; supported types are
     *          capability-specific
     * @return normalized content value
     */
    public T of(Object o) {
        return serializer.of(o);
    }

    /**
     * Returns the localized display name component for this capability.
     *
     * @return translation component keyed by {@code recipe.capability.<name>.name}
     */
    public Component getTraslateComponent() {
        return Component.translatable("recipe.capability.%s.name".formatted(name));
    }

    /**
     * create a default / example content of this capability.
     *
     * @return representative content value for editor initialization or examples
     */
    public abstract T createDefaultContent();

    /**
     * create a default / example content of this capability for the given recipe IO.
     *
     * @param io recipe side being configured
     * @return representative content value for that side
     */
    public T createDefaultContent(IO io) {
        return createDefaultContent();
    }

    /**
     * Whether this capability's input contents should be scaled while detecting automatic parallel.
     *
     * @return {@code true} when automatic parallel detection may scale this
     * capability's inputs
     */
    public boolean scalesForAutomaticParallel() {
        return true;
    }

    /**
     * create a preview widget for the content of this capability.
     *
     * <p>Business goal: show one content value in the UI editor. Implementations
     * should create widgets sized {@code 18x18} unless the surrounding editor
     * explicitly supports another size.</p>
     *
     * @param content content value to preview
     * @return newly created preview widget
     */
    public abstract Widget createPreviewWidget(T content);

    /**
     * create a widget for recipe viewer (XEI).
     *
     * <p>This creates only the template. Call
     * {@link #bindXEIWidget(Widget, Content, IngredientIO)} to bind concrete
     * content.</p>
     *
     * @return newly created recipe-viewer widget template
     */
    public abstract Widget createXEITemplate();

    /**
     * Binds recipe content to a recipe-viewer widget.
     *
     * <p>Side effects: mutates {@code widget} to display the supplied content.
     * Implementations are responsible for validating/casting the widget type.</p>
     *
     * @param widget       widget created by this capability or a compatible template
     * @param content      recipe content wrapper to display
     * @param ingredientIO JEI/REI-style role for input, output, both, or
     *                     render-only display
     */
    public abstract void bindXEIWidget(Widget widget, Content content, IngredientIO ingredientIO);

    /**
     * create a content ui configurator for the content of this capability.
     *
     * <p>Side effects: appends one or more configurator widgets to
     * {@code father}; calls {@code onUpdate} when edited content changes.</p>
     *
     * @param father   parent configurator group
     * @param supplier supplies the current content value
     * @param onUpdate receives updated content values
     */
    public abstract void createContentConfigurator(ConfiguratorGroup father, Supplier<T> supplier, Consumer<T> onUpdate);

    /**
     * create a content ui configurator for the content of this capability for the given recipe IO.
     *
     * @param father   parent configurator group
     * @param supplier supplies the current content value
     * @param onUpdate receives updated content values
     * @param io       recipe side being configured
     */
    public void createContentConfigurator(ConfiguratorGroup father, Supplier<T> supplier, Consumer<T> onUpdate, IO io) {
        createContentConfigurator(father, supplier, onUpdate);
    }

    /**
     * Get the error info for the left content.
     *
     * @param left remaining unsatisfied content from recipe matching
     * @return component describing why the remaining content could not be handled
     */
    public abstract Component getLeftErrorInfo(List<T> left);

    //TODO

    /**
     * Calculates a numeric amount represented by unsatisfied content.
     *
     * <p>Business goal: support diagnostics or progress displays for capabilities
     * that can summarize remaining content. The default returns {@code 1} as a
     * neutral placeholder.</p>
     *
     * @param left remaining unsatisfied content
     * @return capability-defined amount; default is {@code 1}
     */
    public double calculateAmount(List<T> left) {
        return 1;
    }

}
