package com.lowdragmc.mbd2.common.gui.editor;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.DialogWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;
import com.lowdragmc.mbd2.api.pattern.predicates.SimplePredicate;
import com.lowdragmc.mbd2.common.gui.editor.multiblock.MultiblockPatternPanel;
import com.lowdragmc.mbd2.integration.ldlib.MBDLDLibPlugin;
import com.mojang.datafixers.util.Either;

import java.io.File;

/**
 * Resource-panel container for reusable multiblock predicates.
 *
 * <p>The container provides previews, drag payloads, add/edit/remove actions, and rename/global-change propagation for
 * {@link PredicateResource}. When predicate keys are renamed, converted between built-in/static storage, or removed,
 * open multiblock projects are updated so all {@link MultiblockMachineProject} placeholders keep valid references.</p>
 */
public class PredicateResourceContainer extends ResourceContainer<SimplePredicate, Widget> {

    /**
     * Creates a predicate resource container and installs all predicate-specific UI callbacks.
     *
     * @param resource predicate resource backing this container
     * @param panel    owning resource panel
     */
    public PredicateResourceContainer(Resource<SimplePredicate> resource, ResourcePanel panel) {
        super(resource, panel);
        setWidgetSupplier(k -> createPreview(getResource().getResource(k)));
        setDragging(key -> key, (k, o, p) -> getResource().getResource(k).getPreviewTexture());
        setOnEdit(key -> {
            var predicate = getResource().getResource(key);
            if (predicate == SimplePredicate.ANY || predicate == SimplePredicate.AIR) return;
            getPanel().getEditor().getConfigPanel().openConfigurator(ConfigPanel.Tab.RESOURCE, predicate);
        });
        setCanGlobalChange(key -> key.map(left -> !left.equals("any") && !left.equals("air"), right -> true));
        setCanRemove(key -> key.map(left -> !left.equals("any") && !left.equals("air"), right -> true));
        setOnGlobalChange(key -> {
            if (Editor.INSTANCE.getCurrentProject() instanceof MultiblockMachineProject project) {
                Either<String, File> newKey = key.map(
                        l -> Either.right(resource.getStaticResourceFile(l)),
                        r -> Either.left(resource.getStaticResourceName(r)));
                var changed = false;
                for (var x : project.getBlockPlaceholders()) {
                    for (var y : x) {
                        for (var z : y) {
                            if (z.getPredicates().remove(key)) {
                                z.getPredicates().add(newKey);
                                changed = true;
                            }
                        }
                    }
                }
                if (changed) {
                    Editor.INSTANCE.getTabPages().tabs.values().stream()
                            .filter(MultiblockPatternPanel.class::isInstance)
                            .map(MultiblockPatternPanel.class::cast)
                            .findAny().ifPresent(MultiblockPatternPanel::onBlockPlaceholdersChanged);
                }
            }
        });
        setOnRemove(key -> {
            if (Editor.INSTANCE.getCurrentProject() instanceof MultiblockMachineProject project) {
                boolean changed = false;
                for (var x : project.getBlockPlaceholders()) {
                    for (var y : x) {
                        for (var z : y) {
                            changed |= z.getPredicates().remove(key);
                        }
                    }
                }
                if (changed) {
                    Editor.INSTANCE.getTabPages().tabs.values().stream()
                            .filter(MultiblockPatternPanel.class::isInstance)
                            .map(MultiblockPatternPanel.class::cast)
                            .findAny().ifPresent(MultiblockPatternPanel::onBlockPlaceholdersChanged);
                }
            }
        });
        setOnMenu((selected, m) -> m.branch(Icons.ADD_FILE, "config.predicate.add_predicate", menu -> {
            for (var entry : MBDLDLibPlugin.REGISTER_PREDICATES.entrySet()) {
                menu.leaf("config.predicate.%s".formatted(entry.getKey()), () -> {
                    var predicate = entry.getValue().creator().get().buildPredicate();
                    predicate.buildPredicate();
                    resource.addBuiltinResource(genNewFileName(), predicate);
                    reBuild();
                });
            }
        }));
    }

    /**
     * Creates the small texture preview used by the resource list.
     *
     * @param predicate predicate to preview
     * @return image widget bound to the predicate preview texture
     */
    protected ImageWidget createPreview(SimplePredicate predicate) {
        return new ImageWidget(0, 0, 33, 33, predicate::getPreviewTexture);
    }

    /**
     * Renames a predicate resource and rewrites open multiblock placeholder references.
     */
    @Override
    protected void renameResource() {
        if (selected != null) {
            DialogWidget.showStringEditorDialog(Editor.INSTANCE,
                    LocalizationUtils.format("ldlib.gui.editor.tips.rename") + " " + LocalizationUtils.format(resource.name()),
                    resource.getResourceName(selected), s -> {
                        if (!selected.map(l -> resource.hasBuiltinResource(s), r -> resource.hasStaticResource(resource.getStaticResourceFile(s)))) {
                            return false;
                        }
                        if (renamePredicate != null) {
                            return renamePredicate.test(s);
                        }
                        return true;
                    }, s -> {
                        if (s == null) return;
                        var stored = resource.removeResource(selected);
                        if (stored != null) {
                            var name = selected.mapBoth(l -> s, r -> resource.getStaticResourceFile(s));
                            resource.addResource(name, stored);
                        }
                        if (Editor.INSTANCE.getCurrentProject() instanceof MultiblockMachineProject project) {
                            boolean changed = false;
                            for (var x : project.getBlockPlaceholders()) {
                                for (var y : x) {
                                    for (var z : y) {
                                        if (z.getPredicates().remove(selected)) {
                                            z.getPredicates().add(Either.left(s));
                                            changed = true;
                                        }
                                    }
                                }
                            }
                            if (changed) {
                                Editor.INSTANCE.getTabPages().tabs.values().stream()
                                        .filter(MultiblockPatternPanel.class::isInstance)
                                        .map(MultiblockPatternPanel.class::cast)
                                        .findAny().ifPresent(MultiblockPatternPanel::onBlockPlaceholdersChanged);
                            }
                        }
                        reBuild();
                    });
        }
    }

}
