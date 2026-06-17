package com.lowdragmc.mbd2.common.gui.recipe.ingredient.entity;

import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.editor.configurator.ValueConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.WidgetTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.SearchComponentWidget;
import com.lowdragmc.mbd2.api.recipe.ingredient.EntityIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nonnull;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Searchable configurator for selecting an {@link EntityType}.
 *
 * <p>The configurator shows a search field populated from
 * {@link BuiltInRegistries#ENTITY_TYPE}, accepts dragged {@link Entity} or
 * {@link EntityType} values, and previews results with {@link EntityPreviewWidget}. It is
 * used by entity ingredient configuration in the recipe editor.</p>
 */
public class EntityTypeConfigurator extends ValueConfigurator<EntityType<?>> implements SearchComponentWidget.IWidgetSearch<EntityType<?>> {
    protected SearchComponentWidget<EntityType<?>> searchComponent;
    protected ImageWidget image;

    /**
     * Creates an entity-type configurator.
     *
     * @param name         configurator label translation key or literal text
     * @param supplier     current value supplier
     * @param onUpdate     callback invoked when the selected entity type changes
     * @param defaultValue fallback value used when the supplier returns {@code null}
     * @param forceUpdate  whether updates should be emitted even when the value appears
     *                     unchanged, matching {@link ValueConfigurator} behavior
     */
    public EntityTypeConfigurator(String name, Supplier<EntityType<?>> supplier, Consumer<EntityType<?>> onUpdate, @Nonnull EntityType<?> defaultValue, boolean forceUpdate) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        if (value == null) {
            value = defaultValue;
        }
    }

    @Override
    protected void onValueUpdate(EntityType<?> newValue) {
        if (newValue == null) newValue = defaultValue;
        if (value == newValue) return;
        super.onValueUpdate(newValue);
        searchComponent.setCurrent(value);
    }

    @Override
    public void init(int width) {
        super.init(width);
        addWidget(image = new ImageWidget(leftWidth, 2, width - leftWidth - 3 - rightWidth, 10, ColorPattern.T_GRAY.rectTexture().setRadius(5)));
        image.setDraggingConsumer(
                o -> o instanceof EntityType<?> || o instanceof Entity,
                o -> image.setImage(ColorPattern.GREEN.rectTexture().setRadius(5)),
                o -> image.setImage(ColorPattern.T_GRAY.rectTexture().setRadius(5)),
                o -> {
                    if (o instanceof Entity entity) {
                        onValueUpdate(entity.getType());
                        updateValue();
                    } else if (o instanceof EntityType<?> entityType) {
                        onValueUpdate(entityType);
                        updateValue();
                    }
                    image.setImage(ColorPattern.T_GRAY.rectTexture().setRadius(5));
                });
        addWidget(searchComponent = new SearchComponentWidget<>(leftWidth + 3, 2, width - leftWidth - 6 - rightWidth, 10, this));
        searchComponent.setIconProvider(type -> new WidgetTexture(new EntityPreviewWidget(EntityIngredient.of(1, type), 0, 0, 18, 18).setShowAmount(false)));
        searchComponent.setShowUp(true);
        searchComponent.setCapacity(5);
        searchComponent.setCurrent(value);
        var textFieldWidget = searchComponent.textFieldWidget;
        textFieldWidget.setClientSideWidget();
        textFieldWidget.setBordered(false);
    }


    @Override
    public String resultDisplay(EntityType<?> entityType) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
    }

    @Override
    public void selectResult(EntityType<?> value) {
        onValueUpdate(value);
        updateValue();
    }

    @Override
    public void search(String word, Consumer<EntityType<?>> find) {
        var wordLower = word.toLowerCase();
        for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            if (Thread.currentThread().isInterrupted()) return;
            var entityType = entry.getValue();
            var id = entry.getKey().location();
            if (id.toString().contains(wordLower)) {
                find.accept(entityType);
            }
        }
    }
}
