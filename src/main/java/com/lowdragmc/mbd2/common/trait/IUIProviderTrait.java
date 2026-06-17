package com.lowdragmc.mbd2.common.trait;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

/**
 * Contract for trait definitions that provide machine UI fragments.
 *
 * <p>Implementations create a reusable widget template at definition/edit time and later bind that template to a
 * concrete runtime {@link ITrait}. The methods are called from UI construction paths and should only mutate the
 * supplied widget trees.</p>
 */
public interface IUIProviderTrait {

    /**
     * Returns the trait definition backing this UI provider.
     *
     * <p>The default implementation supports the common case where the provider is implemented by the definition
     * class itself.</p>
     *
     * @return provider definition
     */
    default TraitDefinition getDefinition() {
        return (TraitDefinition) this;
    }

    /**
     * Returns the id prefix used for widgets created by this provider.
     *
     * @return stable widget id prefix derived from the trait definition name
     */
    default String uiPrefixName() {
        return "ui:" + getDefinition().getName();
    }

    /**
     * Adds this trait's reusable UI template to a widget group.
     *
     * @param ui destination group to mutate
     */
    void createTraitUITemplate(WidgetGroup ui);

    /**
     * Binds a runtime trait instance to widgets created by {@link #createTraitUITemplate(WidgetGroup)}.
     *
     * @param trait runtime trait instance that supplies live state
     * @param group instantiated widget tree containing this provider's template widgets
     */
    void initTraitUI(ITrait trait, WidgetGroup group);

}
